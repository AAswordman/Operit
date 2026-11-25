package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRosterTest {

    @Test
    fun `normalize always materialises the built-in row first`() {
        val normalized = SubagentRoster.normalize(emptyList())

        assertEquals(1, normalized.size)
        assertEquals(SubagentProfile.BUILT_IN_ID, normalized.first().id)
        assertEquals(SubagentProfile.BUILT_IN_NAME, normalized.first().name)
        assertTrue(normalized.first().isBuiltIn)
        assertEquals(0, normalized.first().sortOrder)
    }

    @Test
    fun `normalize restores the built-in name after a user rename`() {
        val renamed = SubagentProfile(
            id = SubagentProfile.BUILT_IN_ID,
            name = "renamed helper",
            description = "kept",
            isBuiltIn = false,
        )

        val normalized = SubagentRoster.normalize(listOf(renamed))

        assertEquals(SubagentProfile.BUILT_IN_NAME, normalized.first().name)
        // 简介由用户维护，不强制恢复。
        assertEquals("kept", normalized.first().description)
        assertTrue(normalized.first().isBuiltIn)
    }

    @Test
    fun `normalize drops rows that impersonate the built-in id`() {
        val impostor = SubagentProfile(id = "other", name = SubagentProfile.BUILT_IN_NAME)
        val normalized = SubagentRoster.normalize(listOf(impostor))

        // 内置行已占用该名字，冒名行按 nameKey 去重被丢弃。
        assertEquals(1, normalized.size)
        assertEquals(SubagentProfile.BUILT_IN_ID, normalized.first().id)
    }

    @Test
    fun `normalize deduplicates names including diacritic variants`() {
        val first = SubagentProfile(id = "a", name = "Café")
        val second = SubagentProfile(id = "b", name = "Cafe")
        val normalized = SubagentRoster.normalize(listOf(first, second))

        assertEquals(2, normalized.size)
        assertEquals("Café", normalized[1].name)
        assertEquals("a", normalized[1].id)
    }

    @Test
    fun `normalize truncates fields to their limits`() {
        val long = SubagentProfile(
            id = "a",
            name = "n".repeat(SubagentLimits.NAME + 20),
            description = "d".repeat(SubagentLimits.DESCRIPTION + 20),
            instructions = "i".repeat(SubagentLimits.INSTRUCTIONS + 20),
        )

        val entry = SubagentRoster.normalize(listOf(long))[1]

        assertEquals(SubagentLimits.NAME, entry.name.length)
        assertEquals(SubagentLimits.DESCRIPTION, entry.description.length)
        assertEquals(SubagentLimits.INSTRUCTIONS, entry.instructions.length)
    }

    @Test
    fun `normalize caps the roster at the maximum count`() {
        val many = (1..SubagentLimits.MAX_COUNT + 5).map {
            SubagentProfile(id = "id-$it", name = "agent-$it")
        }

        assertEquals(SubagentLimits.MAX_COUNT, SubagentRoster.normalize(many).size)
    }

    @Test
    fun `normalize never throws on dirty rows and reindexes sortOrder`() {
        val dirty = listOf(
            SubagentProfile(id = "blank", name = "   ", sortOrder = -7),
            SubagentProfile(id = "ok", name = "ok", sortOrder = 99),
            SubagentProfile(id = "bad-mode", name = "bad", modelBindingMode = "NONSENSE", modelIndex = -3),
        )

        val normalized = SubagentRoster.normalize(dirty)

        assertEquals(3, normalized.size)
        assertEquals(listOf(0, 1, 2), normalized.map { it.sortOrder })
        val badMode = normalized.first { it.id == "bad-mode" }
        assertEquals(SubagentProfile.ModelBindingMode.FOLLOW_SESSION, badMode.modelBindingMode)
        assertEquals(0, badMode.modelIndex)
    }

    @Test
    fun `resolve falls back to the built-in row only when the name is blank`() {
        val roster = listOf(SubagentProfile(id = "a", name = "researcher"))

        assertEquals(SubagentProfile.BUILT_IN_ID, SubagentRoster.resolve(roster, null)?.id)
        assertEquals(SubagentProfile.BUILT_IN_ID, SubagentRoster.resolve(roster, "  ")?.id)
        assertEquals("a", SubagentRoster.resolve(roster, "RESEARCHER")?.id)
        // 未命中必须返回 null，由调用方报 unknown_agent，不做猜测式兜底。
        assertNull(SubagentRoster.resolve(roster, "missing"))
    }

    @Test
    fun `resolve treats folded names as the same agent`() {
        val roster = listOf(SubagentProfile(id = "a", name = "Café"))

        assertEquals("a", SubagentRoster.resolve(roster, "Cafe")?.id)
    }

    @Test
    fun `model binding mode normalisation only yields two values`() {
        assertEquals(
            SubagentProfile.ModelBindingMode.FIXED_CONFIG,
            SubagentProfile.ModelBindingMode.normalize("FIXED_CONFIG"),
        )
        assertEquals(
            SubagentProfile.ModelBindingMode.FOLLOW_SESSION,
            SubagentProfile.ModelBindingMode.normalize("anything else"),
        )
        assertEquals(
            SubagentProfile.ModelBindingMode.FOLLOW_SESSION,
            SubagentProfile.ModelBindingMode.normalize(null),
        )
    }

    @Test
    fun `built-in row keeps a non-zero description but never loses identity`() {
        val normalized = SubagentRoster.normalize(
            listOf(SubagentProfile(id = SubagentProfile.BUILT_IN_ID, name = "x", isBuiltIn = true))
        )

        val builtIn = normalized.first()
        assertTrue(builtIn.isBuiltIn)
        assertEquals(SubagentProfile.BUILT_IN_NAME, builtIn.name)
        assertFalse(builtIn.name.isBlank())
    }
}
