package com.jarves.mh.runtime

import org.json.JSONArray
import java.io.File

/**
 * Regras do botão "Always allow" da aprovação de comandos.
 *
 * Por que a regra é estreita: liberar "o comando que o usuário viu" para sempre só é
 * seguro se o texto aprovado não puder embutir outro comando. Por isso só comandos
 * simples (sem encadeamento, pipe, redirecionamento ou expansão) e de uma lista fixa
 * geram regra; todo o resto continua pedindo aprovação a cada vez.
 */
object ApprovalRules {
    // Caracteres que encadeiam, redirecionam ou expandem outro comando.
    private val SHELL_META = Regex("""[;&|`$<>(){}\\\n\r]""")

    // Só leitura e sem flag que execute ou grave. Ficaram de fora, de propósito: rg (--pre executa),
    // sort (-o grava, --compress-program executa), uniq (2º argumento grava), tree (-o grava),
    // file, date e find. Escrita aqui permitiria forjar a resposta de aprovação.
    private val READ_ONLY_TOOLS = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "pwd", "echo", "which", "stat",
        "du", "df", "cut", "basename", "dirname", "realpath", "whoami", "uname",
    )

    // git só nos subcomandos que apenas leem o repositório. npm/pnpm/yarn ficam de fora porque
    // run/test/exec executam scripts que o agente pode ter editado.
    private val READ_ONLY_GIT = setOf("status", "log", "diff", "show")

    // Flags do git que gravam em arquivo ou chamam programa externo.
    private val GIT_UNSAFE_FLAGS = listOf("--output", "--ext-diff", "--textconv", "--open-files-in-pager", "-O")

    /** Regra a gravar para [command], ou nulo quando o botão "Always allow" não deve aparecer. */
    fun ruleFor(command: String): String? {
        val tokens = simpleTokens(command) ?: return null
        val tool = tokens[0]
        return when {
            tool in READ_ONLY_TOOLS -> tool
            tool == "git" && tokens.getOrNull(1) in READ_ONLY_GIT && !hasUnsafeGitFlag(tokens) -> "git ${tokens[1]}"
            else -> null
        }
    }

    /** Verdadeiro se [command] é simples e começa exatamente pelos termos de [rule]. */
    fun matches(rule: String, command: String): Boolean {
        val tokens = simpleTokens(command) ?: return false
        val ruleTokens = rule.trim().split(Regex("\\s+"))
        if (tokens[0] == "git" && hasUnsafeGitFlag(tokens)) return false
        return tokens.size >= ruleTokens.size && ruleTokens.indices.all { tokens[it] == ruleTokens[it] }
    }

    private fun hasUnsafeGitFlag(tokens: List<String>) =
        tokens.any { token -> GIT_UNSAFE_FLAGS.any { token == it || token.startsWith("$it=") || (it.length > 2 && token.startsWith(it)) } }

    private fun simpleTokens(command: String): List<String>? {
        val text = command.trim()
        if (text.isEmpty() || SHELL_META.containsMatchIn(text)) return null
        return text.split(Regex("\\s+"))
    }
}

/** Guarda as regras num JSON no armazenamento privado do app, entre execuções. */
class ApprovalRuleStore(private val file: File) {
    @Synchronized
    fun isAllowed(command: String): Boolean = load().any { ApprovalRules.matches(it, command) }

    @Synchronized
    fun add(rule: String) {
        val rules = load()
        if (rule in rules) return
        file.writeText(JSONArray(rules + rule).toString())
    }

    private fun load(): List<String> = runCatching {
        val array = JSONArray(file.readText())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
}
