package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApprovalRulesTest {
    @Test fun plainToolsUseTheToolName() {
        assertEquals("ls", ApprovalRules.ruleFor("ls -la /workspace"))
        assertEquals("python3", ApprovalRules.ruleFor("python3 script.py"))
        assertEquals("rm", ApprovalRules.ruleFor("rm -rf build"))
        assertEquals("grep", ApprovalRules.ruleFor("grep \"foo bar\" file.txt"))
    }

    @Test fun subcommandToolsIncludeTheSubcommand() {
        assertEquals("git status", ApprovalRules.ruleFor("git status -sb"))
        assertEquals("git push", ApprovalRules.ruleFor("git push origin main"))
        assertEquals("npm run", ApprovalRules.ruleFor("npm run build"))
        assertEquals("pip install", ApprovalRules.ruleFor("pip install requests"))
        // Sem subcomando, ou com flag no lugar dele, a regra é só a ferramenta.
        assertEquals("git", ApprovalRules.ruleFor("git -c core.pager=x log"))
        assertEquals("npm", ApprovalRules.ruleFor("npm"))
    }

    @Test fun neverCreatesOrMatchesRulesForChainedCommands() {
        listOf("ls; rm -rf x", "ls && rm x", "ls | sh", "echo hi > f", "ls `id`", "ls \$(id)", "ls\nrm x", "ls &")
            .forEach {
                assertNull(it, ApprovalRules.ruleFor(it))
                assertFalse(it, ApprovalRules.matches("ls", it))
            }
    }

    @Test fun matchesOnlyTheRulePrefix() {
        assertTrue(ApprovalRules.matches("git status", "git status -sb"))
        assertFalse(ApprovalRules.matches("git status", "git push"))
        assertFalse(ApprovalRules.matches("ls", "lsof"))
        assertTrue(ApprovalRules.matches("npm run", "npm run build"))
        assertFalse(ApprovalRules.matches("npm run", "npm install"))
    }

    @Test fun storePersistsRules() {
        val file = File.createTempFile("rules", ".json").apply { delete(); deleteOnExit() }
        val store = ApprovalRuleStore(file)
        assertFalse(store.isAllowed("ls"))
        store.add("ls")
        store.add("ls")
        assertTrue(ApprovalRuleStore(file).isAllowed("ls -la"))
        assertEquals("[\"ls\"]", file.readText())
    }
}
