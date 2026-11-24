package com.jarves.mh.runtime

import android.content.Context
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

/** Forma de entrar no Claude; cada uma corresponde a uma flag de `claude auth login`. */
enum class ClaudeLoginMode(val flag: String) {
    SUBSCRIPTION("--claudeai"),
    CONSOLE("--console"),
    SSO("--sso"),
}

sealed interface ClaudeLoginState {
    data object Idle : ClaudeLoginState
    data object Starting : ClaudeLoginState
    data class WaitingForCode(val url: String) : ClaudeLoginState
    data object Verifying : ClaudeLoginState
    data object Success : ClaudeLoginState
    data class Failed(val message: String) : ClaudeLoginState
}

/**
 * Faz o login do Claude Code dentro do Linux do app, sem precisar de outro computador.
 *
 * `claude auth login` imprime a URL de autorização, espera o código no stdin e termina com
 * código 0 quando as credenciais foram salvas em ~/.claude. O app só mostra a URL, recebe o
 * código do usuário e o escreve no stdin. Diferente do token de `setup-token`, esse login
 * tem o escopo completo que o Remote Control exige.
 */
class ClaudeLoginSession(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val installer = RuntimeInstaller(context)
    private val mutableState = MutableStateFlow<ClaudeLoginState>(ClaudeLoginState.Idle)
    val state: StateFlow<ClaudeLoginState> = mutableState.asStateFlow()
    private val outputFile = File(context.cacheDir, "claude-login.log")

    @Volatile private var process: Process? = null
    private var job: Job? = null

    fun start(mode: ClaudeLoginMode) {
        cancel()
        mutableState.value = ClaudeLoginState.Starting
        job = scope.launch(Dispatchers.IO) { run(mode) }
    }

    fun submitCode(code: String) {
        val value = code.trim()
        if (value.isEmpty()) return
        val running = process?.takeIf { it.isAlive }
        if (running == null) {
            mutableState.value = ClaudeLoginState.Failed("The sign-in session ended. Start again.")
            return
        }
        runCatching {
            running.outputStream.write((value + "\n").toByteArray())
            running.outputStream.flush()
        }.onFailure {
            mutableState.value = ClaudeLoginState.Failed("Could not send the code. Start again.")
            return
        }
        mutableState.value = ClaudeLoginState.Verifying
    }

    fun cancel() {
        job?.cancel()
        job = null
        process?.let { runCatching { it.destroy() } }
        process = null
        mutableState.value = ClaudeLoginState.Idle
    }

    private suspend fun run(mode: ClaudeLoginMode) {
        try {
            val runtime = installer.installedRuntime()
            // Sem token colado: o que vale é o login que o próprio comando vai gravar.
            val launch = RuntimeLaunchConfigBuilder.build(ProviderProfile(kind = ProviderKind.CLAUDE), allowStoredLogin = true)
            val workspace = File(context.filesDir, "workspaces/claude-login").apply { mkdirs() }
            outputFile.delete()
            val running = installer.process(
                runtime.proot,
                runtime.rootfs,
                workspace,
                // BROWSER=true evita que o CLI tente abrir um navegador que não existe no proot.
                launch.environment + ("BROWSER" to "true"),
                listOf(launch.executable, "auth", "login", mode.flag),
                guestWorkspacePath = "/workspace/claude-login",
                outputFile = outputFile,
            )
            process = running

            var urlShown = false
            while (running.isAlive && coroutineContext.isActive) {
                if (!urlShown) {
                    extractLoginUrl(readOutput())?.let {
                        urlShown = true
                        mutableState.value = ClaudeLoginState.WaitingForCode(it)
                    }
                }
                delay(250)
            }
            if (!coroutineContext.isActive) return
            val exitCode = running.waitFor()
            mutableState.value = if (exitCode == 0) {
                ClaudeLoginState.Success
            } else {
                ClaudeLoginState.Failed(lastLine(readOutput()) ?: "Sign-in failed (exit $exitCode).")
            }
        } catch (error: Exception) {
            if (coroutineContext.isActive) {
                mutableState.value = ClaudeLoginState.Failed(error.message ?: "Sign-in failed.")
            }
        } finally {
            process = null
        }
    }

    private fun readOutput(): String = runCatching { cleanTerminalOutput(outputFile.readText()) }.getOrDefault("")
}

private val ANSI = Regex("\\u001B(?:\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)|\\[[0-?]*[ -/]*[@-~]|[()][A-Z0-9])")
private val URL = Regex("https://[^\\s\"'<>]+")

internal fun cleanTerminalOutput(text: String): String = text.replace(ANSI, "").replace("\r", "")

/** Primeira URL https da saída do `claude auth login`, sem pontuação final. */
internal fun extractLoginUrl(output: String): String? =
    URL.find(output)?.value?.trimEnd('.', ',', ')', ']')

internal fun lastLine(output: String): String? =
    output.lines().map(String::trim).lastOrNull { it.isNotEmpty() }?.take(300)
