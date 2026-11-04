package com.ai.assistance.operit.data.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Parsed standard MCP configuration used by both UI and market imports. */
internal data class McpConfigImport(
    val servers: List<McpImportedServer>
)

internal sealed interface McpImportedServer {
    val id: String
    val disabled: Boolean
}

internal data class StdioMcpImportedServer(
    override val id: String,
    val command: String,
    val args: List<String>,
    val env: Map<String, String>,
    val autoApprove: List<String>,
    override val disabled: Boolean,
    val cwd: String? = null
) : McpImportedServer

internal data class RemoteMcpImportedServer(
    override val id: String,
    val endpoint: String,
    val connectionType: String,
    val headers: Map<String, String>,
    override val disabled: Boolean
) : McpImportedServer

/**
 * Parses the public MCP configuration format without conflating HTTP transports with stdio.
 */
internal object McpConfigImportParser {
    private const val STREAMABLE_HTTP_TYPE = "streamable_http"
    private const val SSE_TYPE = "sse"
    private const val STDIO_TYPE = "stdio"

    fun parse(jsonConfig: String): McpConfigImport {
        val root = try {
            JsonParser.parseString(jsonConfig)
        } catch (e: Exception) {
            throw IllegalArgumentException("配置不是有效的 JSON", e)
        }

        require(root.isJsonObject) { "配置根节点必须是对象" }
        val mcpServers = root.asJsonObject.requiredObject("mcpServers")
        require(mcpServers.entrySet().isNotEmpty()) { "mcpServers 不能为空" }

        return McpConfigImport(
            servers = mcpServers.entrySet().map { (serverId, configElement) ->
                parseServer(serverId, configElement)
            }
        )
    }

    private fun parseServer(serverId: String, configElement: JsonElement): McpImportedServer {
        require(serverId.isNotBlank()) { "mcpServers 中存在空服务器 ID" }
        require(configElement.isJsonObject) { "mcpServers.$serverId 必须是对象" }

        val config = configElement.asJsonObject
        val declaredType = config.optionalString("type")
        return if (config.has("command")) {
            parseStdioServer(serverId, config, declaredType)
        } else {
            parseRemoteServer(serverId, config, declaredType)
        }
    }

    private fun parseStdioServer(
        serverId: String,
        config: JsonObject,
        declaredType: String?
    ): StdioMcpImportedServer {
        require(declaredType == null || declaredType == STDIO_TYPE) {
            "mcpServers.$serverId 同时声明了 command 和非 stdio transport"
        }

        return StdioMcpImportedServer(
            id = serverId,
            command = config.requiredNonBlankString("command", serverId),
            args = config.optionalStringList("args", serverId),
            env = config.optionalStringMap("env", serverId),
            autoApprove = config.optionalStringList("autoApprove", serverId),
            disabled = config.optionalBoolean("disabled", serverId),
            cwd = config.optionalNonBlankString("cwd", serverId)
        )
    }

    private fun parseRemoteServer(
        serverId: String,
        config: JsonObject,
        declaredType: String?
    ): RemoteMcpImportedServer {
        val connectionType = when (declaredType) {
            STREAMABLE_HTTP_TYPE -> "httpStream"
            SSE_TYPE -> SSE_TYPE
            STDIO_TYPE -> throw IllegalArgumentException("mcpServers.$serverId 缺少 command")
            null -> throw IllegalArgumentException("mcpServers.$serverId 缺少 command 或 type")
            else -> throw IllegalArgumentException(
                "mcpServers.$serverId 使用了不支持的 transport: $declaredType"
            )
        }

        return RemoteMcpImportedServer(
            id = serverId,
            endpoint = config.requiredNonBlankString("url", serverId),
            connectionType = connectionType,
            headers = config.optionalStringMap("headers", serverId),
            disabled = config.optionalBoolean("disabled", serverId)
        )
    }

    private fun JsonObject.requiredObject(field: String): JsonObject {
        val value = get(field)
            ?: throw IllegalArgumentException("配置中没有找到 $field 字段")
        require(value.isJsonObject) { "$field 必须是对象" }
        return value.asJsonObject
    }

    private fun JsonObject.requiredNonBlankString(field: String, serverId: String): String {
        val value = optionalString(field)
            ?: throw IllegalArgumentException("mcpServers.$serverId 缺少 $field")
        require(value.isNotBlank()) { "mcpServers.$serverId 的 $field 不能为空" }
        return value.trim()
    }

    private fun JsonObject.optionalString(field: String): String? {
        val value = get(field) ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$field 必须是字符串" }
        return value.asString
    }

    private fun JsonObject.optionalNonBlankString(field: String, serverId: String): String? {
        val value = optionalString(field) ?: return null
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "mcpServers.$serverId 的 $field 不能为空" }
        return trimmed
    }

    private fun JsonObject.optionalBoolean(field: String, serverId: String): Boolean {
        val value = get(field) ?: return false
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "mcpServers.$serverId 的 $field 必须是布尔值"
        }
        return value.asBoolean
    }

    private fun JsonObject.optionalStringList(field: String, serverId: String): List<String> {
        val value = get(field) ?: return emptyList()
        require(value.isJsonArray) { "mcpServers.$serverId 的 $field 必须是字符串数组" }
        return value.asJsonArray.mapIndexed { index, item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "mcpServers.$serverId 的 $field[$index] 必须是字符串"
            }
            item.asString
        }
    }

    private fun JsonObject.optionalStringMap(field: String, serverId: String): Map<String, String> {
        val value = get(field) ?: return emptyMap()
        require(value.isJsonObject) { "mcpServers.$serverId 的 $field 必须是对象" }
        return value.asJsonObject.entrySet().associate { (key, item) ->
            require(key.isNotBlank()) { "mcpServers.$serverId 的 $field 包含空键" }
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                "mcpServers.$serverId 的 $field.$key 必须是字符串"
            }
            key to item.asString
        }
    }
}
