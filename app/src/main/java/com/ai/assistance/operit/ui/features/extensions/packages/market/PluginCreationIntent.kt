package com.ai.assistance.operit.ui.features.extensions.packages.market

import android.content.Context
import com.ai.assistance.operit.R

sealed interface PluginCreationIntent {
    val requirement: String

    fun toPrompt(context: Context): String

    data class Fresh(
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(context: Context): String {
            return buildCreationPrompt(
                context = context,
                taskLine = context.getString(R.string.plugin_creation_fresh_task_line),
                packageRuleLine = context.getString(R.string.plugin_creation_fresh_package_rule_line),
                devDirectoryLine = context.getString(R.string.plugin_creation_fresh_dev_directory_line),
                requirement = requirement
            )
        }
    }

    data class Continue(
        val runtimePackageId: String,
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(context: Context): String {
            return buildCreationPrompt(
                context = context,
                taskLine = context.getString(R.string.plugin_creation_continue_task_line, runtimePackageId),
                packageRuleLine = context.getString(R.string.plugin_creation_existing_package_rule_line, runtimePackageId),
                devDirectoryLine = context.getString(R.string.plugin_creation_existing_dev_directory_line, runtimePackageId),
                requirement = requirement
            )
        }
    }

    data class Merge(
        val runtimePackageId: String,
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(context: Context): String {
            return buildCreationPrompt(
                context = context,
                taskLine = context.getString(R.string.plugin_creation_merge_task_line, runtimePackageId),
                packageRuleLine = context.getString(R.string.plugin_creation_existing_package_rule_line, runtimePackageId),
                devDirectoryLine = context.getString(R.string.plugin_creation_existing_dev_directory_line, runtimePackageId),
                requirement = requirement
            )
        }
    }
}

private fun buildCreationPrompt(
    context: Context,
    taskLine: String,
    packageRuleLine: String,
    devDirectoryLine: String,
    requirement: String
): String {
    return buildString {
        appendLine(taskLine)
        appendLine(context.getString(R.string.plugin_creation_update_sandbox_package_dev))
        appendLine(devDirectoryLine)
        appendLine(packageRuleLine)
        appendLine(context.getString(R.string.plugin_creation_copy_types_to_dev_dir))
        appendLine(context.getString(R.string.plugin_creation_use_terminal_ts_js_hint))
        appendLine(context.getString(R.string.plugin_creation_package_ts_hint))
        appendLine(context.getString(R.string.plugin_creation_requirement_label))
        append(requirement.trim())
    }
}
