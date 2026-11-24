package com.jarves.mh.ui

/**
 * Comandos "/" tratados pelo próprio app.
 *
 * Por que existem: o app chama `claude -p` a cada mensagem, e o modo -p não executa
 * comandos embutidos do Claude Code (/model, /clear...). Sem isso o texto seguiria
 * para o agente como um pedido comum. Comandos desconhecidos NÃO entram aqui: seguem
 * para o agente, que pode ter skills e comandos próprios do projeto.
 */
sealed interface SlashCommand {
    data object Help : SlashCommand
    data object Clear : SlashCommand
    data object Status : SlashCommand
    data object Login : SlashCommand

    /** [model] nulo significa "mostrar o modelo atual". */
    data class Model(val model: String?) : SlashCommand
}

/** Devolve o comando se [input] for um dos comandos do app; caso contrário, nulo. */
fun parseSlashCommand(input: String): SlashCommand? {
    val text = input.trim()
    if (!text.startsWith("/")) return null
    val parts = text.removePrefix("/").split(Regex("\\s+"), limit = 2)
    val argument = parts.getOrNull(1)?.trim()?.takeIf(String::isNotEmpty)
    return when (parts[0].lowercase()) {
        "help", "?" -> SlashCommand.Help
        // /new existe porque quem vem do Claude Code usa /clear; os dois abrem um chat novo.
        "clear", "new" -> SlashCommand.Clear
        "status" -> SlashCommand.Status
        "login" -> SlashCommand.Login
        "model" -> SlashCommand.Model(argument)
        else -> null
    }
}

/** Texto de ajuda. Fica em inglês como as demais mensagens geradas pelo app no chat. */
const val SLASH_HELP_TEXT = """Commands handled by Mobile Harness:
/model – show the current model
/model <id> – switch model (for example /model sonnet or /model claude-sonnet-5-5)
/clear – start a new chat (also /new)
/status – show agent, provider, model and project
/login – sign in to Claude (subscription, console or SSO)
/help – show this list

Any other /command is sent to the agent as a normal message."""
