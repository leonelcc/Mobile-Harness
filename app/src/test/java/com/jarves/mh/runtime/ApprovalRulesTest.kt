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

    @Test fun gitOnlyForReadOnlySubcommands() {
        assertEquals("git status", ApprovalRules.ruleFor("git status -sb"))
        assertEquals("git log", ApprovalRules.ruleFor("git log --oneline -5"))
        assertNull(ApprovalRules.ruleFor("git push"))
        assertNull(ApprovalRules.ruleFor("git commit -m x"))
        assertNull(ApprovalRules.ruleFor("git -c core.pager=x log"))
        assertNull(ApprovalRules.ruleFor("git diff --output=/pocket-bridge/x.response"))
        assertNull(ApprovalRules.ruleFor("git log --ext-diff"))
        assertNull(ApprovalRules.ruleFor("git show -O/tmp/x"))
    }

    @Test fun refusesToolsOutsideTheLists() {
        listOf("rm -rf build", "curl http://x", "mv a /pocket-bridge/b.response", "./run.sh",
            "npm test", "npm run build", "rg --pre sh x", "sort -o f x", "tree -o f", "uniq a b")
            .forEach { assertNull(it, ApprovalRules.ruleFor(it)) }
    }

    @Test fun refusesChainingRedirectionAndExpansion() {
        listOf("ls; rm -rf x", "ls && rm x", "ls | sh", "echo hi > f", "ls `id`", "ls \$(id)", "ls\nrm x", "ls &")
            .forEach { assertNull(it, ApprovalRules.ruleFor(it)) }
    }

    @Test fun matchesOnlySimpleCommandsWithTheRulePrefix() {
        assertTrue(ApprovalRules.matches("git status", "git status -sb"))
        assertFalse(ApprovalRules.matches("git status", "git push"))
        assertFalse(ApprovalRules.matches("git diff", "git diff --output=x"))
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
