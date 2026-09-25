package com.ai.assistance.operit.ui.features.packages.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageTemplateKind

/**
 * 包管理加号弹出的中心选择面板。
 * 插件页：导入现有 / 新建 JS 模板 / 新建 TS 模板。
 * 沙盒包页：导入现有 / 新建 JS 模板。
 */
@Composable
fun PackageAddActionDialog(
    isPluginTab: Boolean,
    onImport: () -> Unit,
    onCreateTemplate: (PackageTemplateKind) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxDialogHeight = configuration.screenHeightDp.dp * 0.8f
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogHeight),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(
                        if (isPluginTab) {
                            R.string.package_add_dialog_title_plugin
                        } else {
                            R.string.package_add_dialog_title_package
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (isPluginTab) {
                                R.string.package_add_dialog_message_plugin
                            } else {
                                R.string.package_add_dialog_message_package
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AddActionCard(
                        icon = Icons.Default.FileOpen,
                        title = stringResource(
                            if (isPluginTab) {
                                R.string.package_add_import_existing_plugin
                            } else {
                                R.string.package_add_import_existing_package
                            }
                        ),
                        subtitle = stringResource(
                            if (isPluginTab) {
                                R.string.package_add_import_existing_plugin_hint
                            } else {
                                R.string.package_add_import_existing_package_hint
                            }
                        ),
                        onClick = onImport
                    )
                    AddActionCard(
                        icon = Icons.Default.Code,
                        title = stringResource(
                            if (isPluginTab) {
                                R.string.package_add_create_js_plugin
                            } else {
                                R.string.package_add_create_js_package
                            }
                        ),
                        subtitle = stringResource(
                            if (isPluginTab) {
                                R.string.package_add_create_js_plugin_hint
                            } else {
                                R.string.package_add_create_js_package_hint
                            }
                        ),
                        onClick = {
                            onCreateTemplate(
                                if (isPluginTab) {
                                    PackageTemplateKind.PLUGIN_JS
                                } else {
                                    PackageTemplateKind.SANDBOX_JS
                                }
                            )
                        }
                    )
                    if (isPluginTab) {
                        AddActionCard(
                            icon = Icons.Default.DataObject,
                            title = stringResource(R.string.package_add_create_ts_plugin),
                            subtitle = stringResource(R.string.package_add_create_ts_plugin_hint),
                            onClick = { onCreateTemplate(PackageTemplateKind.PLUGIN_TS) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
