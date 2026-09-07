package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager

fun packageMatchesSearch(
    context: Context,
    packageName: String,
    toolPackage: ToolPackage,
    searchText: String
): Boolean {
    val searchableText =
        buildList {
            add(packageName)
            add(toolPackage.name)
            add(toolPackage.displayName.resolve(context))
            add(toolPackage.description.resolve(context))
            add(toolPackage.category)
            addAll(toolPackage.author)
            toolPackage.tools.forEach { tool ->
                addPackageToolSearchText(context, tool)
            }
            toolPackage.states.forEach { state ->
                add(state.id)
                state.tools.forEach { tool ->
                    addPackageToolSearchText(context, tool)
                }
            }
        }

    return searchableText.any { text -> text.contains(searchText, ignoreCase = true) }
}

fun pluginMatchesSearch(
    packageName: String,
    details: PackageManager.ToolPkgContainerDetails,
    searchText: String
): Boolean {
    val searchableText =
        buildList {
            add(packageName)
            add(details.packageName)
            add(details.displayName)
            add(details.description)
            add(details.version)
            add(details.apiVersion)
            details.requires.forEach { requirement ->
                add(requirement.id)
                add(requirement.description)
                add(requirement.minVersion.orEmpty())
                add(requirement.maxVersion.orEmpty())
            }
            addAll(details.author)
            details.subpackages.forEach { subpackage ->
                add(subpackage.packageName)
                add(subpackage.subpackageId)
                add(subpackage.displayName)
                add(subpackage.description)
            }
            details.workflowTemplates.forEach { template ->
                add(template.templateId)
                add(template.displayName)
                add(template.description)
            }
            details.workspaceTemplates.forEach { template ->
                add(template.templateId)
                add(template.displayName)
                add(template.description)
            }
            details.toolboxUiModules.forEach { module ->
                add(module.routeId)
                add(module.uiModuleId)
                add(module.title)
                add(module.description)
            }
        }

    return searchableText.any { text -> text.contains(searchText, ignoreCase = true) }
}

private fun MutableList<String>.addPackageToolSearchText(
    context: Context,
    tool: PackageTool
) {
    add(tool.name)
    add(tool.description.resolve(context))
    tool.parameters.forEach { parameter ->
        add(parameter.name)
        add(parameter.description.resolve(context))
        add(parameter.type)
    }
}
