package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApprovalRulesTest {
    @Test fun readOnlyToolsUseTheToolName() {
        assertEquals("ls", ApprovalRules.ruleFor("ls -la /workspace"))
        assertEquals("grep", ApprovalRules.ruleFor("grep -rn foo ."))
    }

    @Test fun subcommandToolsIncludeTheSubcommand() {
        assertEquals("git status", ApprovalRules.ruleFor("git status -sb"))
        assertEquals("npm test", ApprovalRules.ruleFor("npm test"))
        assertNull(ApprovalRules.ruleFor("git -c core.pager=x log"))
        assertNull(ApprovalRules.ruleFor("npm"))
    }

    @Test fun refusesToolsOutsideTheLists() {
        assertNull(ApprovalRules.ruleFor("rm -rf build"))
        assertNull(ApprovalRules.ruleFor("curl http://x"))
        assertNull(ApprovalRules.ruleFor("mv a /pocket-bridge/b.response"))
        assertNull(ApprovalRules.ruleFor("./run.sh"))
    }

    @Test fun refusesChainingRedirectionAndExpansion() {
        listOf("ls; rm -rf x", "ls && rm x", "ls | sh", "echo hi > f", "ls `id`", "ls \$(id)", "ls\nrm x", "ls &")
            .forEach { assertNull(it, ApprovalRules.ruleFor(it)) }
    }

    @Test fun matchesOnlySimpleCommandsWithTheRulePrefix() {
        assertTrue(ApprovalRules.matches("git status", "git status -sb"))
        assertFalse(ApprovalRules.matches("git status", "git push"))
        assertFalse(ApprovalRules.matches("git status", "git status; rm -rf x"))
        assertFalse(ApprovalRules.matches("ls", "lsof"))
        assertFalse(ApprovalRules.matches("ls", "ls | sh"))
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
