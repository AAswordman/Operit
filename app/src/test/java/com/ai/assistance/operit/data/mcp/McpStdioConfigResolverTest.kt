package com.ai.assistance.operit.data.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpStdioConfigResolverTest {

    @Test
    fun commandName_ignoresDirectoryAndCase() {
        assertEquals("node", McpStdioConfigResolver.commandName("/usr/local/bin/Node"))
        assertEquals("node.exe", McpStdioConfigResolver.commandName("C:\\Program Files\\node.exe"))
        assertEquals("npx", McpStdioConfigResolver.commandName("  npx  "))
    }

    @Test
    fun isTerminalPath_acceptsLinuxFormsOnly() {
        assertTrue(McpStdioConfigResolver.isTerminalPath("/root/tool"))
        assertTrue(McpStdioConfigResolver.isTerminalPath("~/tool"))
        assertTrue(McpStdioConfigResolver.isTerminalPath("~"))
        assertTrue(McpStdioConfigResolver.isTerminalPath("\$HOME/tool"))
        assertFalse(McpStdioConfigResolver.isTerminalPath("dist/index.js"))
        assertFalse(McpStdioConfigResolver.isTerminalPath("C:\\tool"))
    }

    @Test
    fun appManagedRuntimePath_coversCopiedDirectory() {
        assertTrue(McpStdioConfigResolver.isAppManagedRuntimePath("~/mcp_plugins/tool"))
        assertTrue(McpStdioConfigResolver.isAppManagedRuntimePath("/root/mcp_plugins/tool"))
        assertFalse(McpStdioConfigResolver.isAppManagedRuntimePath("/root/tools/my-mcp"))
    }

    @Test
    fun requiresAppManagedInstallation_skipsPackageRunners() {
        assertFalse(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "npx",
                args = listOf("-y", "@modelcontextprotocol/server-filesystem"),
                cwd = null
            )
        )
        assertFalse(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "uvx",
                args = listOf("mcp-server-fetch"),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_relativeEntryNeedsDeployment() {
        assertTrue(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "node",
                args = listOf("dist/index.js"),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_absoluteEntryIsSelfContained() {
        assertFalse(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "node",
                args = listOf("/root/tools/my-mcp/dist/index.js"),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_explicitCwdIsSelfContained() {
        assertFalse(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "node",
                args = listOf("dist/index.js"),
                cwd = "/root/tools/my-mcp"
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_cwdInsideAppManagedDirectoryStillDeploys() {
        // The user may pre-create ~/mcp_plugins/<id>; Operit still owns that directory and has to
        // populate it before launch.
        assertTrue(
            McpStdioConfigResolver.usesAppManagedRuntimeDirectory(
                command = "node",
                args = listOf("dist/index.js"),
                cwd = "~/mcp_plugins/my-mcp"
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_absoluteCommandWithoutRelativeEntryIsSelfContained() {
        // A standalone binary needs no copied files.
        assertFalse(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "/usr/bin/mcp-server",
                args = emptyList(),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_absoluteCommandWithRelativeEntryStillDeploys() {
        // `node dist/index.js` resolves the entry against the working directory, so the plugin
        // files still have to exist in the app-managed directory.
        assertTrue(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "/usr/bin/node",
                args = listOf("dist/index.js"),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_appManagedAbsoluteArgsStillDeploy() {
        // A user may point at the directory Operit copies into; that directory is created by the
        // deployer, so the plugin still has to be deployed first.
        assertTrue(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "node",
                args = listOf("~/mcp_plugins/my-mcp/dist/index.js"),
                cwd = null
            )
        )
    }

    @Test
    fun requiresAppManagedInstallation_inlineScriptKeepsDeployment() {
        // `node -e "..."` has no entry file; it runs wherever the app-managed directory points,
        // so it is not a self-contained terminal path.
        assertTrue(
            McpStdioConfigResolver.requiresAppManagedInstallation(
                command = "node",
                args = listOf("-e", "console.log('x')"),
                cwd = null
            )
        )
    }

    @Test
    fun resolveWorkingDirectory_prefersConfiguredCwd() {
        assertEquals(
            "/root/tools/my-mcp",
            McpStdioConfigResolver.resolveWorkingDirectory(
                configuredCwd = " /root/tools/my-mcp ",
                pluginId = "owner/my-mcp",
                command = "node",
                args = listOf("dist/index.js")
            )
        )
    }

    @Test
    fun resolveWorkingDirectory_usesAbsoluteEntryParent() {
        assertEquals(
            "/root/tools/my-mcp/dist",
            McpStdioConfigResolver.resolveWorkingDirectory(
                configuredCwd = null,
                pluginId = "owner/my-mcp",
                command = "node",
                args = listOf("/root/tools/my-mcp/dist/index.js")
            )
        )
    }

    @Test
    fun resolveWorkingDirectory_usesAppManagedDirectoryForRelativeEntry() {
        assertEquals(
            "~/mcp_plugins/my-mcp",
            McpStdioConfigResolver.resolveWorkingDirectory(
                configuredCwd = null,
                pluginId = "owner/my-mcp",
                command = "node",
                args = listOf("dist/index.js")
            )
        )
    }
}
