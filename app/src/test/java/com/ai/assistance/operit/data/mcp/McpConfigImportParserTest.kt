package com.ai.assistance.operit.data.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpConfigImportParserTest {

    @Test
    fun parse_classifiesStdioAndRemoteTransports() {
        val config =
            """
            {
              "mcpServers": {
                "filesystem": {
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-filesystem"],
                  "env": {"HOME": "/data/local/tmp"},
                  "autoApprove": ["read_file"]
                },
                "fetch": {
                  "type": "streamable_http",
                  "url": "https://example.com/mcp",
                  "headers": {"X-Client": "Operit"},
                  "disabled": true
                },
                "events": {
                  "type": "sse",
                  "url": "https://example.com/sse"
                }
              }
            }
            """.trimIndent()

        val parsed = McpConfigImportParser.parse(config)

        val stdio = parsed.servers.filterIsInstance<StdioMcpImportedServer>().single()
        assertEquals("filesystem", stdio.id)
        assertEquals("npx", stdio.command)
        assertEquals(listOf("-y", "@modelcontextprotocol/server-filesystem"), stdio.args)
        assertEquals(mapOf("HOME" to "/data/local/tmp"), stdio.env)
        assertEquals(listOf("read_file"), stdio.autoApprove)

        val remoteServers = parsed.servers.filterIsInstance<RemoteMcpImportedServer>()
            .associateBy { it.id }
        assertEquals("httpStream", remoteServers.getValue("fetch").connectionType)
        assertEquals(mapOf("X-Client" to "Operit"), remoteServers.getValue("fetch").headers)
        assertTrue(remoteServers.getValue("fetch").disabled)
        assertEquals("sse", remoteServers.getValue("events").connectionType)
        assertFalse(remoteServers.getValue("events").disabled)
    }

    @Test
    fun parse_rejectsMissingTransportDeclaration() {
        val config =
            """
            {
              "mcpServers": {
                "fetch": {"url": "https://example.com/mcp"}
              }
            }
            """.trimIndent()

        assertParseFails(config)
    }

    @Test
    fun parse_rejectsAmbiguousStdioAndRemoteConfiguration() {
        val config =
            """
            {
              "mcpServers": {
                "fetch": {
                  "command": "node",
                  "type": "streamable_http",
                  "url": "https://example.com/mcp"
                }
              }
            }
            """.trimIndent()

        assertParseFails(config)
    }

    @Test
    fun parse_readsStdioWorkingDirectory() {
        val config =
            """
            {
              "mcpServers": {
                "handytools": {
                  "command": "node",
                  "args": ["dist/index.js"],
                  "cwd": "/root/moonlight-garden-handytools"
                }
              }
            }
            """.trimIndent()

        val stdio = McpConfigImportParser.parse(config)
            .servers
            .filterIsInstance<StdioMcpImportedServer>()
            .single()

        assertEquals("/root/moonlight-garden-handytools", stdio.cwd)
    }

    @Test
    fun parse_rejectsBlankStdioWorkingDirectory() {
        val config =
            """
            {
              "mcpServers": {
                "handytools": {
                  "command": "node",
                  "args": ["dist/index.js"],
                  "cwd": "   "
                }
              }
            }
            """.trimIndent()

        assertParseFails(config)
    }

    private fun assertParseFails(config: String) {
        try {
            McpConfigImportParser.parse(config)
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected MCP configuration parsing to fail")
    }
}
