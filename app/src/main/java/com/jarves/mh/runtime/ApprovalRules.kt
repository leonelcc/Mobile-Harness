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

    // Só leitura: sem escrita, não dá para forjar a resposta de aprovação por aqui.
    private val READ_ONLY_TOOLS = setOf(
        "ls", "cat", "head", "tail", "wc", "grep", "rg", "pwd", "echo", "tree", "which",
        "date", "stat", "file", "du", "df", "sort", "uniq", "diff", "cut", "basename",
        "dirname", "realpath", "whoami", "uname",
    )

    // Ferramentas em que o subcomando muda o risco; a regra inclui o subcomando.
    private val SUBCOMMAND_TOOLS = setOf("git", "npm", "pnpm", "yarn")

    /** Regra a gravar para [command], ou nulo quando o botão "Always allow" não deve aparecer. */
    fun ruleFor(command: String): String? {
        val tokens = simpleTokens(command) ?: return null
        val tool = tokens[0]
        return when {
            tool in READ_ONLY_TOOLS -> tool
            tool in SUBCOMMAND_TOOLS -> tokens.getOrNull(1)?.takeUnless { it.startsWith("-") }?.let { "$tool $it" }
            else -> null
        }
    }

    /** Verdadeiro se [command] é simples e começa exatamente pelos termos de [rule]. */
    fun matches(rule: String, command: String): Boolean {
        val tokens = simpleTokens(command) ?: return false
        val ruleTokens = rule.trim().split(Regex("\\s+"))
        return tokens.size >= ruleTokens.size && ruleTokens.indices.all { tokens[it] == ruleTokens[it] }
    }

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
