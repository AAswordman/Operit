package com.ai.assistance.operit.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.config.model.ModelConfigData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStreamOutputMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldGlobalChoiceIsCopiedToEveryExistingConfiguration() {
        for (enabled in listOf(false, true)) {
            val firstKey = stringPreferencesKey("config_first")
            val secondKey = stringPreferencesKey("config_second")
            val preferences = mutablePreferencesOf(
                ModelConfigManager.CONFIG_LIST_KEY to json.encodeToString(listOf("first", "second")),
                firstKey to """{"id":"first","name":"第一份","enableToolCall":false,"extension":{"keep":true}}""",
                secondKey to """{"id":"second","name":"第二份"}""",
            )
            ModelConfigManager.migratePreferencesFromVersionFive(preferences, enabled)
            val first = json.parseToJsonElement(requireNotNull(preferences[firstKey])).jsonObject
            val second = json.parseToJsonElement(requireNotNull(preferences[secondKey])).jsonObject
            assertEquals(enabled, first.getValue("enableStreamOutput").jsonPrimitive.boolean)
            assertEquals(enabled, second.getValue("enableStreamOutput").jsonPrimitive.boolean)
            assertFalse(first.getValue("enableToolCall").jsonPrimitive.boolean)
            assertTrue(first.getValue("extension").jsonObject.getValue("keep").jsonPrimitive.boolean)
        }
    }

    @Test
    fun explicitPerConfigurationChoiceIsNeverOverwritten() {
        val key = stringPreferencesKey("config_first")
        val preferences = mutablePreferencesOf(
            ModelConfigManager.CONFIG_LIST_KEY to json.encodeToString(listOf("first")),
            key to """{"id":"first","name":"第一份","enableStreamOutput":false}""",
        )
        ModelConfigManager.migratePreferencesFromVersionFive(preferences, true)
        ModelConfigManager.migratePreferencesFromVersionFive(preferences, true)
        assertFalse(json.decodeFromString<ModelConfigData>(requireNotNull(preferences[key])).enableStreamOutput)
    }

    @Test
    fun newAndImportedLegacyConfigurationsEnableBothCapabilitiesByDefault() {
        val fresh = ModelConfigData(id = "fresh", name = "新配置")
        val imported = json.decodeFromString<ModelConfigData>("""{"id":"imported","name":"旧格式导入"}""")
        assertTrue(fresh.enableStreamOutput)
        assertTrue(fresh.enableToolCall)
        assertTrue(imported.enableStreamOutput)
        assertTrue(imported.enableToolCall)
    }

    @Test
    fun serializationAndCopyPreserveIndependentStreamingChoices() {
        val first = ModelConfigData(id = "first", name = "流式")
        val second = ModelConfigData(id = "second", name = "完整回复", enableStreamOutput = false)
        val decoded = json.decodeFromString<List<ModelConfigData>>(json.encodeToString(listOf(first, second)))
        assertTrue(decoded[0].enableStreamOutput)
        assertFalse(decoded[1].enableStreamOutput)
        assertFalse(decoded[1].copy(name = "重命名").enableStreamOutput)
    }
}