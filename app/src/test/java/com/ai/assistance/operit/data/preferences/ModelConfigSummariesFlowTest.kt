package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigGroup
import com.ai.assistance.operit.data.model.ModelConfigSelection
import com.ai.assistance.operit.data.model.ModelConfigSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelConfigSummariesFlowTest {

    @Test
    fun `active config summaries subscription receives created and updated models`() = runBlocking {
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = TestPreferencesDataStore(preferences)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val firstConfigId = "first-config"
        val thinkingConfigurations = """[{"id":"think-1","label":"Low"}]"""
        val firstConfig =
            ModelConfigData(
                id = firstConfigId,
                name = "first",
                modelName = "model-a",
                apiProviderType = ApiProviderType.OPENAI_GENERIC,
                thinkingConfigurations = thinkingConfigurations,
                thinkingOptionId = "think-1",
            )
        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                set(stringPreferencesKey("config_$firstConfigId"), json.encodeToString(firstConfig))
                set(ModelConfigManager.CONFIG_LIST_KEY, json.encodeToString(listOf(firstConfigId)))
            }
        }

        val context = Mockito.mock(Context::class.java)
        val manager = ModelConfigManager(context, dataStore)
        val emissions = Channel<List<ModelConfigSummary>>(Channel.UNLIMITED)
        val collector = launch {
            manager.configSummariesFlow.take(3).collect { emissions.send(it) }
        }

        val initialSummaries = emissions.receive()
        assertEquals(listOf(firstConfigId), initialSummaries.map { it.id })
        assertEquals("model-a", initialSummaries.single().modelName)
        assertEquals(thinkingConfigurations, initialSummaries.single().thinkingConfigurations)
        assertEquals("think-1", initialSummaries.single().thinkingOptionId)

        val secondConfigId = manager.createConfig("second")
        val afterCreateSummaries = emissions.receive()
        assertEquals(listOf(firstConfigId, secondConfigId), afterCreateSummaries.map { it.id })

        manager.updateConfigBase(firstConfigId, "renamed")
        val afterUpdateSummaries = emissions.receive()
        assertEquals("renamed", afterUpdateSummaries.first().name)
        assertEquals("second", afterUpdateSummaries.last().name)
        assertEquals(thinkingConfigurations, afterUpdateSummaries.first().thinkingConfigurations)
        assertEquals("think-1", afterUpdateSummaries.first().thinkingOptionId)
        collector.join()
    }

    @Test
    fun `legacy imports are committed once and remain visible in group summaries`() = runBlocking {
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = TestPreferencesDataStore(preferences)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val validGroup = ModelConfigGroup(id = "group-a", name = "Group A")
        val existingConfig =
            ModelConfigData(id = "existing", name = "Existing", groupId = validGroup.id)

        dataStore.updateData { current ->
            current.toMutablePreferences().apply {
                set(ModelConfigManager.CONFIG_GROUPS_KEY, json.encodeToString(listOf(validGroup)))
                set(
                    stringPreferencesKey("config_${existingConfig.id}"),
                    json.encodeToString(existingConfig),
                )
                set(
                    ModelConfigManager.CONFIG_LIST_KEY,
                    json.encodeToString(listOf(existingConfig.id)),
                )
            }
        }

        val context = Mockito.mock(Context::class.java)
        val manager = ModelConfigManager(context, dataStore)
        val updatesBeforeImport = dataStore.updateCount
        val legacyPayload =
            """
            [
              {"id":"legacy","name":"Legacy"},
              {"id":"blank","name":"Blank","groupId":"   "},
              {"id":"orphan","name":"Orphan","groupId":"missing-group"},
              {"id":"grouped","name":"Grouped","groupId":"group-a"},
              {"id":"existing","name":"Existing updated"}
            ]
            """.trimIndent()

        assertEquals(Triple(4, 1, 0), manager.importConfigs(legacyPayload))
        assertEquals(updatesBeforeImport + 1, dataStore.updateCount)

        val summaries = manager.configSummariesFlow.first()
        assertEquals(
            listOf("existing", "legacy", "blank", "orphan", "grouped"),
            summaries.map { it.id },
        )
        assertEquals(
            listOf(null, null, null, null, validGroup.id),
            summaries.map { it.groupId },
        )
        assertEquals("Existing updated", summaries.first().name)
        assertEquals(null, manager.getModelConfig("existing")?.groupId)
        assertEquals(null, manager.getModelConfig("orphan")?.groupId)
    }

    @Test
    fun `export includes groups and import recreates missing groups`() = runBlocking {
        val sourceStore = TestPreferencesDataStore(MutableStateFlow<Preferences>(mutablePreferencesOf()))
        val context = Mockito.mock(Context::class.java)
        val source = ModelConfigManager(context, sourceStore)
        val groupId = source.createConfigGroup("Remote")
        val configId = source.createConfig("Remote model", groupId)

        val exported = source.exportAllConfigs()

        val targetStore = TestPreferencesDataStore(MutableStateFlow<Preferences>(mutablePreferencesOf()))
        val target = ModelConfigManager(context, targetStore)
        assertEquals(Triple(1, 0, 0), target.importConfigs(exported))
        assertEquals(listOf(groupId), target.configGroupsFlow.first().map { it.id })
        assertEquals(groupId, target.getModelConfig(configId)?.groupId)
    }

    @Test
    fun `group candidates track selection and organization without hiding existing bindings`() = runBlocking {
        withTimeout(5_000) {
            val dataStore = TestPreferencesDataStore(MutableStateFlow<Preferences>(mutablePreferencesOf()))
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            // Duplicate names must not merge groups. An empty group must stay empty.
            val groupA = ModelConfigGroup("group-a", "Same name")
            val groupB = ModelConfigGroup("group-b", "Same name")
            val emptyGroup = ModelConfigGroup("empty", "Empty")
            val groups = listOf(groupA, groupB, emptyGroup)
            val configs = listOf(
                ModelConfigData(id = "default", name = "Default", modelName = "deepseek-flash"),
                ModelConfigData(id = "grok", name = "Grok", groupId = groupA.id, modelName = "grok-4.5"),
                ModelConfigData(id = "other", name = "Other", groupId = groupB.id),
                ModelConfigData(id = "claude", name = "Claude", groupId = groupA.id),
            )
            dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    set(ModelConfigManager.CONFIG_GROUPS_KEY, json.encodeToString(groups))
                    set(ModelConfigManager.CONFIG_LIST_KEY, json.encodeToString(configs.map { it.id }))
                    configs.forEach { config ->
                        set(stringPreferencesKey("config_${config.id}"), json.encodeToString(config))
                    }
                }
            }
            val manager = ModelConfigManager(Mockito.mock(Context::class.java), dataStore)
            manager.setSelectedConfigGroup(groupA.id)
            val emissions = Channel<ModelConfigSelection>(Channel.UNLIMITED)
            val collector = launch {
                manager.configSelectionFlow.take(7).collect { emissions.send(it) }
            }

            val initial = emissions.receive()
            assertEquals(groupA, initial.selectedGroup)
            assertEquals(listOf("grok", "claude"), initial.availableConfigs.map { it.id })
            assertEquals("deepseek-flash", initial.allConfigs.first { it.id == "default" }.modelName)

            manager.setSelectedConfigGroup(groupB.id)
            val otherGroup = emissions.receive()
            assertEquals(groupB.id, otherGroup.selectedGroupId)
            assertEquals(listOf("other"), otherGroup.availableConfigs.map { it.id })

            manager.setSelectedConfigGroup(emptyGroup.id)
            val empty = emissions.receive()
            assertEquals(emptyGroup, empty.selectedGroup)
            assertEquals(emptyList<String>(), empty.availableConfigs.map { it.id })
            assertEquals(configs.map { it.id }, empty.allConfigs.map { it.id })

            manager.setSelectedConfigGroup(null)
            val ungrouped = emissions.receive()
            assertEquals(null, ungrouped.selectedGroupId)
            assertEquals(listOf("default"), ungrouped.availableConfigs.map { it.id })

            manager.setSelectedConfigGroup(groupA.id)
            assertEquals(listOf("grok", "claude"), emissions.receive().availableConfigs.map { it.id })
            dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    set(ModelConfigManager.CONFIG_GROUPS_KEY, json.encodeToString(
                        listOf(groupA.copy(name = "Renamed"), groupB, emptyGroup)
                    ))
                    set(stringPreferencesKey("config_grok"), json.encodeToString(configs[1].copy(groupId = groupB.id)))
                }
            }
            val reorganized = emissions.receive()
            assertEquals("Renamed", reorganized.selectedGroup?.name)
            assertEquals(listOf("claude"), reorganized.availableConfigs.map { it.id })
            assertEquals(configs.map { it.id }, reorganized.allConfigs.map { it.id })

            // A persisted selection whose group is removed uses the same ungrouped rules as summaries.
            dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    set(ModelConfigManager.CONFIG_GROUPS_KEY, json.encodeToString(listOf(groupB, emptyGroup)))
                }
            }
            val removed = emissions.receive()
            assertEquals(null, removed.selectedGroupId)
            assertEquals(listOf("default", "claude"), removed.availableConfigs.map { it.id })
            collector.join()
        }
    }

    private class TestPreferencesDataStore(
        private val preferences: MutableStateFlow<Preferences>
    ) : DataStore<Preferences> {
        var updateCount: Int = 0
            private set

        override val data = preferences.asStateFlow()

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val updated = transform(preferences.value)
            updateCount++
            preferences.value = updated
            return updated
        }
    }
}
