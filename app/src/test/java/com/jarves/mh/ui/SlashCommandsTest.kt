package com.jarves.mh.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlashCommandsTest {
    @Test fun parsesSimpleCommands() {
        assertEquals(SlashCommand.Help, parseSlashCommand("/help"))
        assertEquals(SlashCommand.Help, parseSlashCommand("  /?  "))
        assertEquals(SlashCommand.Clear, parseSlashCommand("/clear"))
        assertEquals(SlashCommand.Clear, parseSlashCommand("/new"))
        assertEquals(SlashCommand.Status, parseSlashCommand("/status"))
        assertEquals(SlashCommand.Login, parseSlashCommand("/login"))
    }

    @Test fun parsesModelWithAndWithoutArgument() {
        assertEquals(SlashCommand.Model(null), parseSlashCommand("/model"))
        assertEquals(SlashCommand.Model("sonnet"), parseSlashCommand("/model sonnet"))
        assertEquals(SlashCommand.Model("claude-sonnet-5-5"), parseSlashCommand("/MODEL   claude-sonnet-5-5  "))
    }

    @Test fun ignoresEverythingElse() {
        assertNull(parseSlashCommand("hello"))
        assertNull(parseSlashCommand("/review"))
        assertNull(parseSlashCommand("/path/to/file.txt please read it"))
        assertNull(parseSlashCommand(""))
    }
}
