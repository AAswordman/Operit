package com.ai.assistance.operit.ui.features.packages.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.packTool.PackageManager

internal enum class PackageEnvVarFieldState {
    REQUIRED_EMPTY,
    REQUIRED_FILLED,
    OPTIONAL
}

internal fun packageEnvVarFieldState(
    required: Boolean,
    value: String?
): PackageEnvVarFieldState =
    when {
        !required -> PackageEnvVarFieldState.OPTIONAL
        value.isNullOrBlank() -> PackageEnvVarFieldState.REQUIRED_EMPTY
        else -> PackageEnvVarFieldState.REQUIRED_FILLED
    }

@Composable
fun PackageLoadErrorsDialog(
    errorInfos: List<PackageManager.PackageLoadErrorInfo>,
    onDeleteSource: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errorInfos.forEach { errorInfo ->
                    Text(
                        text = errorInfo.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    errorInfo.sourcePath?.let { sourcePath ->
                        Text(
                            text = sourcePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = errorInfo.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorInfo.isExternalSource && errorInfo.sourcePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDeleteSource(errorInfo.sourcePath) }
                        ) {
                            Text(text = stringResource(R.string.package_conflict_delete_source))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageEnvironmentVariablesDialog(
    requiredEnvByPackage: Map<String, List<EnvVar>>,
    currentValues: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current

    val requiredEnvKeys = remember(requiredEnvByPackage) {
        requiredEnvByPackage.values
            .flatten()
            .map { it.name }
            .toSet()
            .toList()
            .sorted()
    }

    var editableValues by
        remember(requiredEnvKeys, currentValues) {
            mutableStateOf(
                requiredEnvKeys.associateWith { key: String -> currentValues[key] ?: "" }
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pkg_config_env_vars)) },
        text = {
            if (requiredEnvKeys.isEmpty()) {
                Text(
                    text = stringResource(R.string.pkg_no_env_vars),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    requiredEnvByPackage.forEach { (packageName, envVars) ->
                        stickyHeader(key = "header:$packageName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = packageName.first().uppercaseChar().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        items(
                            items = envVars,
                            key = { envVar -> "${packageName}:${envVar.name}" }
                        ) { envVar ->
                            val fieldState =
                                packageEnvVarFieldState(
                                    required = envVar.required,
                                    value = editableValues[envVar.name]
                                )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = envVar.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (fieldState != PackageEnvVarFieldState.OPTIONAL) {
                                                val missingRequired =
                                                    fieldState ==
                                                        PackageEnvVarFieldState.REQUIRED_EMPTY
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color =
                                                        if (missingRequired) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme
                                                                .primaryContainer
                                                        }
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Text(
                                                            text = "!",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color =
                                                                if (missingRequired) {
                                                                    MaterialTheme.colorScheme.onError
                                                                } else {
                                                                    MaterialTheme.colorScheme
                                                                        .onPrimaryContainer
                                                                }
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Optional",
                                                            modifier = Modifier.size(10.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        val description = envVar.description.resolve(context)
                                        if (description.isNotBlank()) {
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (envVar.defaultValue != null) {
                                    Text(
                                        text = stringResource(R.string.pkg_default, envVar.defaultValue),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editableValues[envVar.name] ?: "",
                                onValueChange = { newValue ->
                                    editableValues =
                                        editableValues.toMutableMap().apply {
                                            this[envVar.name] = newValue
                                        }
                                },
                                singleLine = true,
                                isError = fieldState == PackageEnvVarFieldState.REQUIRED_EMPTY,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text =
                                            if (envVar.required) {
                                                stringResource(R.string.pkg_input_required)
                                            } else {
                                                stringResource(R.string.pkg_input_optional)
                                            },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(editableValues) }) {
                Text(text = stringResource(R.string.pkg_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.pkg_cancel))
            }
        }
    )
}
