package com.ai.assistance.operit.ui.features.extensions.packages.management

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ActionSheet
import com.ai.assistance.operit.ui.components.ActionSheetItem

@Composable
fun PackageManagerMenu(
    isPluginTab: Boolean,
    isLoading: Boolean,
    errorCount: Int,
    onRefresh: () -> Unit,
    onEnvironmentVariables: () -> Unit,
    onLoadErrors: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showFormatHelp by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.plugin_manage_more))
    }
    if (expanded) {
        ActionSheet(stringResource(R.string.plugin_manage_more), onDismissRequest = { expanded = false }) {
            ActionSheetItem(stringResource(R.string.refresh), Icons.Default.Refresh, enabled = !isLoading) {
                expanded = false
                onRefresh()
            }
            ActionSheetItem(stringResource(R.string.pkg_manage_env_vars), Icons.Default.Tune) {
                expanded = false
                onEnvironmentVariables()
            }
            ActionSheetItem(stringResource(R.string.plugin_import_format_help), Icons.Default.Info) {
                expanded = false
                showFormatHelp = true
            }
            if (errorCount > 0) {
                ActionSheetItem(stringResource(R.string.plugin_manage_load_errors, errorCount), Icons.Default.ErrorOutline) {
                    expanded = false
                    onLoadErrors()
                }
            }
        }
    }
    if (showFormatHelp) {
        AlertDialog(
            onDismissRequest = { showFormatHelp = false },
            title = { Text(stringResource(R.string.plugin_import_format_help)) },
            text = {
                Text(stringResource(if (isPluginTab) R.string.plugin_import_format_description else R.string.sandbox_import_format_description))
            },
            confirmButton = {
                TextButton(onClick = { showFormatHelp = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}
