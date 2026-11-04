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
        // 用户可以预建应用管理目录，但启动前仍需由部署器准备文件。
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
        // 独立二进制程序无需复制插件文件。
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
        // 相对入口按工作目录解析，因此应用管理目录中仍需存在插件文件。
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
        // 显式指定应用管理目录时，仍需先由部署器创建目录并部署。
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
        // 内联执行没有外部入口文件，仍按应用管理目录规则处理。
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
