package com.ai.assistance.operit.core.tools.packTool.metadata

import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.parser.ToolPkgLoadResult

/** 个人发行版的首次默认值；已保存的启停状态仍由包管理器持有。 */
internal object BuiltInPackageDefaults {
    private val enabledSandboxPackages = setOf(
        "system_tools",
        "super_admin",
        "various_search",
        "extended_http_tools",
        "extended_memory_tools",
        "extended_file_tools",
        "code_runner",
        "operit_editor",
    )

    fun sandbox(toolPackage: ToolPackage): ToolPackage = toolPackage.copy(
        isBuiltIn = true,
        enabledByDefault = toolPackage.name in enabledSandboxPackages,
    )

    fun plugin(result: ToolPkgLoadResult): ToolPkgLoadResult = result.copy(
        containerPackage = result.containerPackage.copy(enabledByDefault = false),
    )
}
