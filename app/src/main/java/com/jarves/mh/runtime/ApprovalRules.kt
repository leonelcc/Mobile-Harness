package com.jarves.mh.runtime

import org.json.JSONArray
import java.io.File

/**
 * Regras do botão "Always allow" da aprovação de comandos.
 *
 * A regra é o início do comando que o usuário aprovou: a ferramenta (`ls`, `python3`) ou, nas
 * ferramentas que têm subcomando, a ferramenta mais o subcomando (`git status`, `npm run`).
 * Qualquer comando pode virar regra; a decisão de confiar nele é do usuário.
 *
 * Uma única exceção: comandos com encadeamento, pipe, redirecionamento ou expansão nunca
 * casam com uma regra. Sem isso, liberar `ls` liberaria também `ls; qualquer-coisa`.
 */
object ApprovalRules {
    private val SHELL_META = Regex("""[;&|`$<>(){}\\\n\r]""")

    // Ferramentas em que o subcomando define o que o comando faz; a regra inclui o subcomando.
    private val SUBCOMMAND_TOOLS = setOf(
        "git", "npm", "npx", "pnpm", "yarn", "pip", "pip3", "composer", "cargo", "go", "docker", "apt", "apt-get",
    )

    /** Regra a gravar para [command], ou nulo se o comando é encadeado ou está vazio. */
    fun ruleFor(command: String): String? {
        val tokens = simpleTokens(command) ?: return null
        val tool = tokens[0]
        if (tool in SUBCOMMAND_TOOLS) {
            tokens.getOrNull(1)?.takeUnless { it.startsWith("-") }?.let { return "$tool $it" }
        }
        return tool
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
