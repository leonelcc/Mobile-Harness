package com.jarves.mh.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeLoginSessionTest {
    @Test fun extractsTheUrlFromRealCliOutput() {
        val output = "Opening browser to sign in…\nIf the browser didn't open, visit:\n" +
            "https://claude.ai/oauth/authorize?code=true&client_id=abc&state=xyz\nPaste code here if prompted > "
        assertEquals("https://claude.ai/oauth/authorize?code=true&client_id=abc&state=xyz", extractLoginUrl(output))
    }

    @Test fun noUrlBeforeTheCliPrintsIt() {
        assertNull(extractLoginUrl("Opening browser to sign in…"))
    }

    @Test fun stripsTerminalControlCodes() {
        assertEquals("ok", cleanTerminalOutput("\u001B[32mok\u001B[0m\r"))
    }

    @Test fun lastLineSkipsBlankLines() {
        assertEquals("Login failed", lastLine("something\nLogin failed\n\n"))
        assertNull(lastLine("\n  \n"))
    }
}
