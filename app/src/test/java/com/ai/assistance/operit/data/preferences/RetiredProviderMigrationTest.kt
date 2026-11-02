package com.ai.assistance.operit.data.preferences

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.config.model.ModelConfigExport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetiredProviderMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val retiredConfig = """
        {
            "id": "existing-config", "name": "保留的模型配置", "groupId": "group-one",
            "apiProviderType": "IFLOW", "apiProviderTypeId": "IFLOW",
            "apiEndpoint": "https://apis.iflow.cn/v1/chat/completions",
            "modelName": "model-a,model-b", "apiKey": "example-key",
            "useMultipleApiKeys": true,
            "apiKeyPool": [{"id": "key-one", "key": "example-pool-key", "usageCount": 42}]
        }
    """.trimIndent()

    @Test
    fun schemaUpgradePreservesConfigIdentityKeysModelsAndGroup() {
        val configKey = stringPreferencesKey("config_existing-config")
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("config_list") to json.encodeToString(listOf("existing-config")),
            configKey to retiredConfig,
        )
        ModelConfigManager.migratePreferencesFromVersionFour(prefs)
        val config = json.decodeFromString<ModelConfigData>(requireNotNull(prefs[configKey]))
        assertEquals(ApiProviderType.OPENAI_GENERIC, config.apiProviderType)
        assertEquals("OPENAI_GENERIC", config.apiProviderTypeId)
        assertEquals("", config.apiEndpoint)
        assertPreserved(config)
    }

    @Test
    fun customEndpointAndCustomProviderIdArePreserved() {
        val raw = retiredConfig.replace("https://apis.iflow.cn/v1/chat/completions", "https://custom.example/v1/chat/completions")
            .replace("\"apiProviderTypeId\": \"IFLOW\"", "\"apiProviderTypeId\": \"custom-provider\"")
        val config = json.decodeFromString<ModelConfigData>(RetiredProviderMigration.rewriteConfig(raw))
        assertEquals("https://custom.example/v1/chat/completions", config.apiEndpoint)
        assertEquals("custom-provider", config.apiProviderTypeId)
        assertPreserved(config)
    }

    @Test
    fun bothPublishedExportFormatsRemainImportable() {
        val legacyArray = RetiredProviderMigration.rewriteExport("[$retiredConfig]")
        assertPreserved(json.decodeFromString<List<ModelConfigData>>(legacyArray).single())
        val grouped = RetiredProviderMigration.rewriteExport("""
            {"groups":[{"id":"group-one","name":"原来的分组"}],"configs":[$retiredConfig]}
        """.trimIndent())
        val export = json.decodeFromString<ModelConfigExport>(grouped)
        assertEquals("原来的分组", export.groups.single().name)
        assertEquals(ApiProviderType.OPENAI_GENERIC, export.configs.single().apiProviderType)
        assertPreserved(export.configs.single())
        assertEquals(grouped, RetiredProviderMigration.rewriteExport(grouped))
    }

    @Test
    fun activeProvidersAreNotRewrittenWhenModelNameContainsRetiredId() {
        val active = retiredConfig.replace("\"IFLOW\"", "\"OPENAI\"")
            .replace("model-a,model-b", "IFLOW-compatible-model")
        val before = json.decodeFromString<ModelConfigData>(active)
        val after = json.decodeFromString<ModelConfigData>(RetiredProviderMigration.rewriteConfig(active))
        assertEquals(before, after)
    }

    private fun assertPreserved(config: ModelConfigData) {
        assertEquals("existing-config", config.id)
        assertEquals("保留的模型配置", config.name)
        assertEquals("group-one", config.groupId)
        assertEquals("model-a,model-b", config.modelName)
        assertEquals("example-key", config.apiKey)
        assertTrue(config.useMultipleApiKeys)
        assertEquals("key-one", config.apiKeyPool.single().id)
        assertEquals("example-pool-key", config.apiKeyPool.single().key)
        assertEquals(42L, config.apiKeyPool.single().usageCount)
    }
}
