package com.ai.assistance.operit.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentToolExposureTest {
    @Test
    fun nativeToolCallCategoriesExposeTaskExactlyOnce() {
        assertTaskExactlyOnce(SystemToolPrompts.getAIAllCategoriesEn())
        assertTaskExactlyOnce(SystemToolPrompts.getAIAllCategoriesCn())
    }

    @Test
    fun fullPromptAndCliCatalogSourceExposeTaskExactlyOnce() {
        assertTaskExactlyOnce(SystemToolPrompts.getAllCategoriesEn())
        assertTaskExactlyOnce(SystemToolPrompts.getAllCategoriesCn())
        assertEquals(
            0,
            SystemToolPromptsInternal.internalToolCategoriesEn
                .flatMap { it.tools }
                .count { it.name == TASK_TOOL_NAME },
        )
        assertEquals(
            0,
            SystemToolPromptsInternal.internalToolCategoriesCn
                .flatMap { it.tools }
                .count { it.name == TASK_TOOL_NAME },
        )
    }

    @Test
    fun childToolSourcesDoNotExposeTask() {
        assertTaskAbsent(
            SystemToolPrompts.getAIAllCategoriesEn(includeSubagentTools = false),
        )
        assertTaskAbsent(
            SystemToolPrompts.getAIAllCategoriesCn(includeSubagentTools = false),
        )
        assertTaskAbsent(
            SystemToolPrompts.getAllCategoriesEn(includeSubagentTools = false),
        )
        assertTaskAbsent(
            SystemToolPrompts.getAllCategoriesCn(includeSubagentTools = false),
        )

        assertFalse(
            SystemToolPrompts.generateToolsPromptEn(
                toolVisibility = mapOf(TASK_TOOL_NAME to false),
            ).contains("- task:"),
        )
        assertFalse(
            SystemToolPrompts.generateToolsPromptCn(
                toolVisibility = mapOf(TASK_TOOL_NAME to false),
            ).contains("- task:"),
        )
    }

    @Test
    fun taskParticipatesInUserControlledToolVisibility() {
        assertEquals(
            1,
            SystemToolPrompts.getManageableToolPrompts(useEnglish = true)
                .count { it.name == TASK_TOOL_NAME },
        )
        assertEquals(
            1,
            SystemToolPrompts.getManageableToolPrompts(useEnglish = false)
                .count { it.name == TASK_TOOL_NAME },
        )

        val visiblePrompt = SystemToolPrompts.generateToolsPromptEn()
        val hiddenPrompt =
            SystemToolPrompts.generateToolsPromptEn(
                toolVisibility = mapOf(TASK_TOOL_NAME to false),
            )
        assertTrue(visiblePrompt.contains("- task:"))
        assertFalse(hiddenPrompt.contains("- task:"))
    }

    @Test
    fun taskSchemaKeepsRequiredInvocationContract() {
        val englishTask =
            SystemToolPrompts.getAIAllCategoriesEn()
                .flatMap { it.tools }
                .single { it.name == TASK_TOOL_NAME }
        val chineseTask =
            SystemToolPrompts.getAIAllCategoriesCn()
                .flatMap { it.tools }
                .single { it.name == TASK_TOOL_NAME }
        val englishParameters = englishTask.parametersStructured.orEmpty()
        val chineseParameters = chineseTask.parametersStructured.orEmpty()

        assertEquals(
            listOf("title", "prompt", "subagent_type", "task_id"),
            englishParameters.map { it.name },
        )
        assertEquals(
            listOf(true, true, true, false),
            englishParameters.map { it.required },
        )
        assertEquals(listOf("string", "string", "string", "string"), englishParameters.map { it.type })
        assertEquals(englishParameters.map { it.name }, chineseParameters.map { it.name })
        assertEquals(englishParameters.map { it.required }, chineseParameters.map { it.required })
        assertEquals(englishParameters.map { it.type }, chineseParameters.map { it.type })
    }

    private fun assertTaskExactlyOnce(
        categories: List<com.ai.assistance.operit.data.model.SystemToolPromptCategory>,
    ) {
        assertEquals(
            1,
            categories.flatMap { it.tools }.count { it.name == TASK_TOOL_NAME },
        )
    }

    private fun assertTaskAbsent(
        categories: List<com.ai.assistance.operit.data.model.SystemToolPromptCategory>,
    ) {
        assertEquals(
            0,
            categories.flatMap { it.tools }.count { it.name == TASK_TOOL_NAME },
        )
    }

    private companion object {
        const val TASK_TOOL_NAME = "task"
    }
}
