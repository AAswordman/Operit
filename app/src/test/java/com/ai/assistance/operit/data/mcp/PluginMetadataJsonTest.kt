package com.ai.assistance.operit.data.mcp

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMetadataJsonTest {
    private val gson = createMcpConfigGson()

    @Test
    fun missingAuthorUsesDeclaredDefaultAndCanBeCopied() {
        val metadata = readConfig(requiredMetadata()).pluginMetadata.getValue("server")
        assertEquals("Unknown", metadata.author)
        assertTrue(metadata.copy(isInstalled = true).isInstalled)
        assertEquals("local", metadata.type)
        assertEquals("", metadata.version)
    }

    @Test
    fun nullAuthorUsesDeclaredDefaultBeforeRepositoryAndScreenCopies() {
        val data = requiredMetadata().apply { add("author", JsonNull.INSTANCE) }
        val metadata = readConfig(data).pluginMetadata.getValue("server")
        assertEquals("Unknown", metadata.copy(isInstalled = true).author)
        assertEquals("Unknown", metadata.copy(disabled = true).author)
    }

    @Test
    fun blankAuthorRemainsBlankAndDoesNotPreventCopying() {
        val data = requiredMetadata().apply { addProperty("author", "") }
        val metadata = readConfig(data).pluginMetadata.getValue("server")
        assertEquals("", metadata.copy(isInstalled = true).author)
    }

    @Test
    fun nullOptionalStringsUseModelDefaultsAndNullableFieldsStayNull() {
        val data = requiredMetadata().apply {
            for (key in listOf("author", "version", "updatedAt", "longDescription", "repoUrl", "type")) {
                add(key, JsonNull.INSTANCE)
            }
            add("logoUrl", JsonNull.INSTANCE)
        }
        val metadata = readConfig(data).pluginMetadata.getValue("server").copy(isInstalled = true)
        assertEquals("Unknown", metadata.author)
        assertEquals("", metadata.version)
        assertEquals("", metadata.updatedAt)
        assertEquals("", metadata.longDescription)
        assertEquals("", metadata.repoUrl)
        assertEquals("local", metadata.type)
        assertNull(metadata.logoUrl)
    }

    @Test
    fun existingConfigRoundTripPreservesMetadataCommandsAndHeaders() {
        val data = requiredMetadata().apply {
            addProperty("author", "作者")
            addProperty("version", "1.2.3")
            addProperty("type", "remote")
            addProperty("endpoint", "https://example.test/mcp")
            addProperty("installedTime", 1_750_000_123_456L)
            add("headers", JsonObject().apply { addProperty("X-Test", "test-value") })
            addProperty("futureMetadata", "未知字段")
        }
        val config = readConfig(data)
        config.mcpServers["local"] = MCPLocalServer.MCPConfig.ServerConfig(
            command = "node", args = listOf("server.js"), disabled = true,
            env = mapOf("EXAMPLE" to "value"), autoApprove = listOf("tool"),
        )
        val restored = gson.fromJson(gson.toJson(config), MCPLocalServer.MCPConfig::class.java)
        assertEquals(config, restored)
        // 按备份导入使用的 JSON 子对象读取，整型时间戳不能经过通用 Map 转成 Double。
        val exported = JsonObject().apply { add("mcpConfig", gson.toJsonTree(config)) }
        val importData = gson.fromJson(gson.toJson(exported), JsonObject::class.java)
        val imported = gson.fromJson(importData["mcpConfig"], MCPLocalServer.MCPConfig::class.java)
        assertEquals(config, imported)
        val metadata = restored.pluginMetadata.getValue("server").copy(isInstalled = true)
        assertEquals("作者", metadata.author)
        assertEquals(1_750_000_123_456L, metadata.installedTime)
        assertEquals("test-value", metadata.headers?.get("X-Test"))
    }

    @Test
    fun requiredMetadataCannotBeReplacedWithInventedValues() {
        for (key in listOf("id", "name", "description")) {
            val data = requiredMetadata().apply { add(key, JsonNull.INSTANCE) }
            assertThrows(SerializationException::class.java) { readConfig(data) }
        }
    }

    private fun requiredMetadata() = JsonObject().apply {
        addProperty("id", "server")
        addProperty("name", "测试服务")
        addProperty("description", "测试描述")
    }

    private fun readConfig(metadata: JsonObject): MCPLocalServer.MCPConfig {
        val config = JsonObject().apply {
            add("mcpServers", JsonObject())
            add("pluginMetadata", JsonObject().apply { add("server", metadata) })
        }
        return gson.fromJson(config, MCPLocalServer.MCPConfig::class.java)
    }
}
