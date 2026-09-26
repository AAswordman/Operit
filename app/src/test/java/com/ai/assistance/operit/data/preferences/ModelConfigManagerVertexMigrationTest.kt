package com.ai.assistance.operit.data.preferences

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigManagerVertexMigrationTest {
    @Test
    fun legacyVertexEndpoint_isSplitIntoIndependentFields() {
        val migrated = ModelConfigManager.migrateLegacyVertexConfig(
            ModelConfigData(
                id = "vertex",
                name = "Vertex",
                apiProviderType = ApiProviderType.VERTEX_AI,
                apiProviderTypeId = ApiProviderType.VERTEX_AI.name,
                apiEndpoint = "my-project|us-central1",
            )
        )

        assertEquals("", migrated.apiEndpoint)
        assertEquals("my-project", migrated.vertexProjectId)
        assertEquals("us-central1", migrated.vertexLocation)
    }

    @Test
    fun legacyVertexEndpoint_withoutLocation_usesGlobal() {
        val migrated = ModelConfigManager.migrateLegacyVertexConfig(
            ModelConfigData(
                id = "vertex",
                name = "Vertex",
                apiProviderType = ApiProviderType.VERTEX_AI,
                apiProviderTypeId = ApiProviderType.VERTEX_AI.name,
                apiEndpoint = "my-project",
            )
        )

        assertEquals("my-project", migrated.vertexProjectId)
        assertEquals("global", migrated.vertexLocation)
    }

    @Test
    fun nonVertexEndpoint_isPreserved() {
        val config = ModelConfigData(
            id = "openai",
            name = "OpenAI",
            apiProviderType = ApiProviderType.OPENAI_GENERIC,
            apiProviderTypeId = ApiProviderType.OPENAI_GENERIC.name,
            apiEndpoint = "https://example.com/v1",
        )

        assertTrue(ModelConfigManager.migrateLegacyVertexConfig(config) === config)
    }
}
