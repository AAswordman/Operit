package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTagTest {

    @Test fun `create with required fields`() {
        val tag = PromptTag(id = "tag1", name = "Friendly")
        assertEquals("tag1", tag.id)
        assertEquals("Friendly", tag.name)
    }

    @Test fun `description defaults to empty`() {
        val tag = PromptTag(id = "t1", name = "T")
        assertEquals("", tag.description)
    }

    @Test fun `prompt content defaults to empty`() {
        val tag = PromptTag(id = "t1", name = "T")
        assertEquals("", tag.promptContent)
    }

    @Test fun `tag type defaults to CUSTOM`() {
        val tag = PromptTag(id = "t1", name = "T")
        assertEquals(TagType.CUSTOM, tag.tagType)
    }

    @Test fun `created at defaults to current time`() {
        val before = System.currentTimeMillis()
        val tag = PromptTag(id = "t1", name = "T")
        val after = System.currentTimeMillis()
        assertTrue(tag.createdAt >= before)
        assertTrue(tag.createdAt <= after + 1000)
    }

    @Test fun `updated at defaults to current time`() {
        val before = System.currentTimeMillis()
        val tag = PromptTag(id = "t1", name = "T")
        val after = System.currentTimeMillis()
        assertTrue(tag.updatedAt >= before)
        assertTrue(tag.updatedAt <= after + 1000)
    }

    @Test fun `tone tag type`() {
        val tag = PromptTag(id = "t1", name = "T", tagType = TagType.TONE)
        assertEquals(TagType.TONE, tag.tagType)
    }

    @Test fun `character tag type`() {
        val tag = PromptTag(id = "t1", name = "T", tagType = TagType.CHARACTER)
        assertEquals(TagType.CHARACTER, tag.tagType)
    }

    @Test fun `function tag type`() {
        val tag = PromptTag(id = "t1", name = "T", tagType = TagType.FUNCTION)
        assertEquals(TagType.FUNCTION, tag.tagType)
    }

    @Test fun `tag type enum has all values`() {
        assertEquals(4, TagType.values().size)
        assertTrue(TagType.values().contains(TagType.TONE))
        assertTrue(TagType.values().contains(TagType.CHARACTER))
        assertTrue(TagType.values().contains(TagType.FUNCTION))
        assertTrue(TagType.values().contains(TagType.CUSTOM))
    }

    @Test fun `copy with different name`() {
        val tag = PromptTag(id = "t1", name = "Old")
        val copy = tag.copy(name = "New")
        assertEquals("New", copy.name)
        assertEquals("t1", copy.id)
    }

    @Test fun `copy with prompt content`() {
        val tag = PromptTag(id = "t1", name = "T")
        val copy = tag.copy(promptContent = "You are helpful")
        assertEquals("You are helpful", copy.promptContent)
    }

    @Test fun `model option creation`() {
        val option = ModelOption(id = "gpt-4", name = "GPT-4")
        assertEquals("gpt-4", option.id)
        assertEquals("GPT-4", option.name)
    }

    @Test fun `workspace rename result creation`() {
        val result = WorkspaceRenameResult(
            workspacePath = "/path/to/ws",
            workspaceEnv = "production",
            workspaceName = "My Workspace",
        )
        assertEquals("/path/to/ws", result.workspacePath)
        assertEquals("production", result.workspaceEnv)
        assertEquals("My Workspace", result.workspaceName)
    }

    @Test fun `workspace rename result null env`() {
        val result = WorkspaceRenameResult("/path", null, "Name")
        assertEquals(null, result.workspaceEnv)
    }
}
