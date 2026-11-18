package com.ai.assistance.operit.core.agent.collaboration

import com.ai.assistance.operit.data.model.ModelConfigSummary
import org.junit.Assert.*
import org.junit.Test

class CollaborationModelsTest {
    private fun config(id: String, name: String = "Work", models: String = "alpha,beta", provider: String = "P") =
        ModelConfigSummary(id = id, name = name, modelName = models, apiProviderTypeId = provider)

    @Test fun eachModelIsSelectableIncludingNonFirstModel() {
        val configs = listOf(config("internal"))
        assertEquals(listOf("Work / alpha", "Work / beta"), CollaborationModels.choices(configs).map { it.selector })
        val chosen = CollaborationModels.resolve("Work / beta", configs)
        assertEquals("internal", chosen.configId)
        assertEquals(1, chosen.modelIndex)
    }

    @Test fun sameNameAcrossProvidersIsExplicitAndNeverPicksFirst() {
        val configs = listOf(config("a", provider = "P"), config("b", provider = "Q"))
        assertThrows(IllegalArgumentException::class.java) { CollaborationModels.resolve("Work / alpha", configs) }
        assertEquals("b", CollaborationModels.resolve("Work / alpha [Q]", configs).configId)
    }

    @Test fun identicalDisplayNamesAndRepeatedModelsRemainIndividuallySelectable() {
        val configs = listOf(config("a", models = "alpha,alpha"), config("b", models = "alpha"))
        val choices = CollaborationModels.choices(configs)
        assertEquals(3, choices.map { it.selector }.distinct().size)
        choices.forEach { assertEquals(it, CollaborationModels.resolve(it.selector, configs)) }
        assertEquals(choices.toSet(), CollaborationModels.choices(configs.reversed()).toSet())
    }

    @Test fun removedOrRenamedChoicesFailWithoutSilentlyFallingBack() {
        assertTrue(CollaborationModels.choices(listOf(config("empty", models = " , "))).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { CollaborationModels.resolve("Work / beta", listOf(config("a", models = "alpha"))) }
        assertThrows(IllegalArgumentException::class.java) { CollaborationModels.resolve("internal", listOf(config("internal"))) }
    }

    @Test fun whitespaceAndBlankConfigurationNamesProduceCopyableSelectors() {
        val configs = listOf(config("a", name = " Work "), config("b", name = "   "))
        assertEquals(listOf("Work / alpha", "Work / beta", "P / alpha", "P / beta"),
            CollaborationModels.choices(configs).map { it.selector })
        CollaborationModels.choices(configs).forEach {
            assertEquals(it, CollaborationModels.resolve(it.selector.trim(), configs))
        }
        val duplicates = listOf(config("a", name = "Work"), config("b", name = " Work "))
        CollaborationModels.choices(duplicates).forEach {
            assertEquals(it, CollaborationModels.resolve(it.selector.trim(), duplicates))
        }
    }

    @Test fun catalogToolTakesNoArgumentsAndPromptContainsNoModelCatalog() {
        CollaborationArguments.validate("list_agent_models", emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            CollaborationArguments.validate("list_agent_models", listOf("model"))
        }
        for (chinese in listOf(false, true)) {
            val category = CollaborationTools.category(chinese, emptyList())
            assertEquals(1, category.tools.count { it.name == "list_agent_models" })
            val description = category.tools.single { it.name == "spawn_agent" }
                .parametersStructured.orEmpty().single { it.name == "model" }.description
            assertTrue(description.contains("list_agent_models"))
            assertFalse(description.contains("Work / alpha"))
        }
    }

    @Test fun modelCatalogResultCarriesTheNoSwitchConstraintInTheCallerLanguage() {
        for (chinese in listOf(false, true)) {
            val note = CollaborationTools.listNote(chinese)
            assertTrue(note.isNotBlank())
            assertNotEquals(CollaborationTools.listNote(!chinese), note)
        }
        assertTrue(CollaborationTools.listNote(chinese = true)
            .contains("除非用户偏好或同意，不要调用其他模型"))
        assertTrue(CollaborationTools.listNote(chinese = false)
            .contains("unless the user prefers or agrees"))
    }

    @Test fun subagentPromptsSayACompletionDoesNotWakeTheCaller() {
        for (chinese in listOf(false, true)) {
            val category = CollaborationTools.category(chinese, emptyList())
            val spawn = category.tools.single { it.name == "spawn_agent" }.description
            val wait = category.tools.single { it.name == "wait_agent" }.description
            assertTrue(spawn.contains(if (chinese) "不会唤醒你" else "does not wake you"))
            assertTrue(wait.contains(if (chinese) "不会唤醒你" else "does not wake you"))
            assertTrue(spawn.contains("wait_agent"))
            assertTrue(spawn.contains(
                if (chinese) "结束本轮前调用 wait_agent" else "call wait_agent before ending the turn"
            ))
            assertTrue(wait.contains(if (chinese) "留在邮箱" else "stays in the mailbox"))
            assertTrue(wait.contains(if (chinese) "下一轮" else "a later turn"))
            assertTrue(wait.contains(if (chinese) "立即返回" else "returns immediately"))
        }
    }
}
