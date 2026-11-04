package com.ai.assistance.operit.ui.features.management.settings.toolpermissions

import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolPermissionCatalogTest {
    private fun tool(name: String, advice: Boolean = false) = PackageTool(
        name = name,
        description = LocalizedText.of("回归工具"),
        parameters = emptyList(),
        script = "",
        advice = advice,
    )

    private fun tools(vararg entries: PackageTool) = ToolPackage(
        name = "installed",
        description = LocalizedText.of("已安装启用包"),
        tools = entries.toList(),
    )

    @Test
    fun keepsNativeToolsAndExcludesStaleScriptExecutorsAndProxyEntries() {
        val registered = listOf(
            "bluetooth_accept", "read_file", "removed:run", "disabled:run",
            "package_proxy", "proxy", "search",
        )
        assertEquals(
            listOf("bluetooth_accept", "read_file"),
            permissionToolNames(registered, emptyList(), emptySet()),
        )
    }

    @Test
    fun enabledInstalledToolsAppearBeforeActivationAndAdviceIsExcluded() {
        assertEquals(
            listOf("installed:read", "read_file"),
            permissionToolNames(
                listOf("read_file"),
                listOf(tools(tool("read"), tool("usage", advice = true))),
                emptySet(),
            ),
        )
    }

    @Test
    fun effectiveStateReplacementDoesNotKeepOldRegisteredPackageTools() {
        val registered = listOf("read_file", "installed:old")
        assertEquals(
            listOf("installed:new", "read_file"),
            permissionToolNames(registered, listOf(tools(tool("new"))), emptySet()),
        )
        // 停用或移除后只剩原生工具，即使旧执行器名称尚在注册快照中也不重新展示。
        assertEquals(
            listOf("read_file"),
            permissionToolNames(registered, emptyList(), emptySet()),
        )
    }

    @Test
    fun mcpToolsRequireBothAnActiveServerAndARegisteredExecutor() {
        val registered = listOf("read_file", "connected:read", "offline:read")
        assertEquals(
            listOf("connected:read", "read_file"),
            permissionToolNames(registered, emptyList(), setOf("connected", "not_registered")),
        )
        assertEquals(
            listOf("read_file"),
            permissionToolNames(registered, emptyList(), emptySet()),
        )
    }
}