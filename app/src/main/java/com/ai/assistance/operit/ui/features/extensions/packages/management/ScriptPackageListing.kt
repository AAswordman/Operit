package com.ai.assistance.operit.ui.features.extensions.packages.management

import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager

/** A presentation projection: ToolPkg children remain available to the execution runtime. */
internal fun PackageManager.getStandaloneScripts(forceRefresh: Boolean = false): Map<String, ToolPackage> =
    selectStandaloneScripts(getAvailablePackages(forceRefresh)) { name ->
        isToolPkgContainer(name) || isToolPkgSubpackage(name)
    }

internal fun selectStandaloneScripts(
    packages: Map<String, ToolPackage>,
    belongsToToolPkg: (String) -> Boolean,
): Map<String, ToolPackage> = packages.filterKeys { !belongsToToolPkg(it) }
