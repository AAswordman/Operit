package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Guards the registration surface remote MCP plugins depend on (issue #907).
 *
 * Bringing a remote plugin up only installs a runtime descriptor. The AI can reach it only once
 * the plugin is also registered as a server, because the "Available packages" prompt section and
 * the package auto-activation path both read [MCPManager.getRegisteredServers].
 */
class McpServerRegistrationTest {

    private val context: Context = mock()

    private val remoteDescriptor = McpRuntimeDescriptor.Remote(
        endpoint = "https://example.invalid/mcp",
        connectionType = "httpStream",
        bearerToken = "token",
        headers = mapOf("X-Client" to "Operit")
    )

    private fun remoteServerConfig() = MCPServerConfig(
        name = PLUGIN_ID,
        endpoint = remoteDescriptor.endpoint,
        description = "Remote MCP service",
        capabilities = listOf("tools"),
        extraData = emptyMap()
    )

    @Test
    fun `connecting a remote runtime does not expose the server to the AI`() {
        val manager = MCPManager(context)

        manager.registerRuntime(PLUGIN_ID, remoteDescriptor)

        assertFalse(manager.isServerRegistered(PLUGIN_ID))
        assertTrue(manager.getRegisteredServers().isEmpty())
    }

    @Test
    fun `registering the server exposes the remote endpoint to the AI`() {
        val manager = MCPManager(context)

        manager.registerServer(PLUGIN_ID, remoteServerConfig(), remoteDescriptor)

        assertTrue(manager.isServerRegistered(PLUGIN_ID))
        assertEquals(
            remoteDescriptor.endpoint,
            manager.getRegisteredServers().getValue(PLUGIN_ID).endpoint
        )
    }

    @Test
    fun `repeated registration keeps a single server entry`() {
        val manager = MCPManager(context)

        repeat(3) {
            manager.registerServer(PLUGIN_ID, remoteServerConfig(), remoteDescriptor.copy())
        }

        assertEquals(setOf(PLUGIN_ID), manager.getRegisteredServers().keys)
    }

    @Test
    fun `descriptors rebuilt from the same metadata compare equal`() {
        // registerRuntime closes the cached session whenever the incoming descriptor differs from
        // the previous one. Registration runs from several lifecycle points, so unchanged plugin
        // metadata has to keep producing an equal descriptor or a live session gets torn down on
        // every refresh.
        val rebuilt = McpRuntimeDescriptor.Remote(
            endpoint = "https://example.invalid/mcp",
            connectionType = "httpStream",
            bearerToken = "token",
            headers = mapOf("X-Client" to "Operit")
        )

        assertEquals(remoteDescriptor, rebuilt)
    }

    @Test
    fun `unregistering a server hides it from the AI again`() {
        val manager = MCPManager(context)
        manager.registerServer(PLUGIN_ID, remoteServerConfig(), remoteDescriptor)

        manager.unregisterServer(PLUGIN_ID)

        assertFalse(manager.isServerRegistered(PLUGIN_ID))
        assertTrue(manager.getRegisteredServers().isEmpty())
    }

    private companion object {
        const val PLUGIN_ID = "galatea-garden"
    }
}
