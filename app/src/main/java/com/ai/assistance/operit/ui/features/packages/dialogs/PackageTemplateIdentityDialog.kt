package com.ai.assistance.operit.ui.features.packages.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageTemplateFactory
import com.ai.assistance.operit.core.tools.packTool.PackageTemplateKind

/** 新建模板前填写显示名和包标识，避免落盘出未命名垃圾包。 */
@Composable
fun PackageTemplateIdentityDialog(
    kind: PackageTemplateKind,
    onConfirm: (displayName: String, packageId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val forToolPkg = PackageTemplateFactory.isToolPkgKind(kind)
    var displayName by remember(kind) { mutableStateOf("") }
    var packageId by remember(kind) {
        mutableStateOf(PackageTemplateFactory.suggestPackageId("", forToolPkg))
    }
    var userEditedPackageId by remember(kind) { mutableStateOf(false) }

    LaunchedEffect(displayName, kind) {
        if (!userEditedPackageId) {
            packageId = PackageTemplateFactory.suggestPackageId(displayName, forToolPkg)
        }
    }

    val displayNameValid = PackageTemplateFactory.isValidDisplayName(displayName)
    val packageIdValid = PackageTemplateFactory.isValidPackageId(packageId, forToolPkg)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.package_template_identity_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(
                        if (forToolPkg) {
                            R.string.package_template_identity_message_plugin
                        } else {
                            R.string.package_template_identity_message_package
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.package_template_display_name)) },
                    placeholder = {
                        Text(text = stringResource(R.string.package_template_display_name_hint))
                    },
                    isError = displayName.isNotBlank() && !displayNameValid
                )
                OutlinedTextField(
                    value = packageId,
                    onValueChange = { value ->
                        userEditedPackageId = true
                        packageId = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(
                                if (forToolPkg) {
                                    R.string.package_template_toolpkg_id
                                } else {
                                    R.string.package_template_package_id
                                }
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(
                                if (forToolPkg) {
                                    R.string.package_template_toolpkg_id_hint
                                } else {
                                    R.string.package_template_package_id_hint
                                }
                            )
                        )
                    },
                    isError = packageId.isNotBlank() && !packageIdValid,
                    supportingText = {
                        if (packageId.isNotBlank() && !packageIdValid) {
                            Text(
                                text = stringResource(
                                    if (forToolPkg) {
                                        R.string.package_template_toolpkg_id_invalid
                                    } else {
                                        R.string.package_template_package_id_invalid
                                    }
                                )
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(displayName.trim(), packageId.trim()) },
                enabled = displayNameValid && packageIdValid
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}