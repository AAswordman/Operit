package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.packTool.PackageManager

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
                        OutlinedButton(onClick = { onDeleteSource(errorInfo.sourcePath) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageEnvironmentVariablesDialog(
    toolPackages: List<ToolPackage>,
    currentValues: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val packageEntries = remember(toolPackages) {
        toolPackages
            .filter { it.env.isNotEmpty() }
            .sortedBy { it.name.lowercase() }
    }
    val environmentKeys = remember(packageEntries) {
        packageEntries.flatMap { it.env }.map { it.name }.distinct()
    }
    var editableValues by remember(environmentKeys, currentValues) {
        mutableStateOf(environmentKeys.associateWith { currentValues[it].orEmpty() })
    }
    var expandedPackages by remember(packageEntries) {
        mutableStateOf(emptySet<String>())
    }

    val configuredCount = environmentKeys.count { editableValues[it].isNullOrBlank().not() }
    val requiredVariables = packageEntries.flatMap { it.env }.filter { it.required }.distinctBy { it.name }
    val missingRequiredCount = requiredVariables.count { editableValues[it.name].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.pkg_config_env_vars))
                Text(
                    text = stringResource(R.string.pkg_env_vars_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (environmentKeys.isEmpty()) {
                Text(
                    text = stringResource(R.string.pkg_no_env_vars),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EnvironmentVariablesSummary(
                        configuredCount = configuredCount,
                        totalCount = environmentKeys.size,
                        missingRequiredCount = missingRequiredCount
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        packageEntries.forEach { toolPackage ->
                            item(key = toolPackage.name) {
                                val expanded = toolPackage.name in expandedPackages
                                val configuredForPackage = toolPackage.env.count {
                                    editableValues[it.name].isNullOrBlank().not()
                                }
                                val missingRequiredForPackage = toolPackage.env.count {
                                    it.required && editableValues[it.name].isNullOrBlank()
                                }
                                PackageEnvironmentVariablesSection(
                                    toolPackage = toolPackage,
                                    values = editableValues,
                                    expanded = expanded,
                                    configuredCount = configuredForPackage,
                                    missingRequiredCount = missingRequiredForPackage,
                                    context = context,
                                    onToggleExpanded = {
                                        expandedPackages = if (expanded) {
                                            expandedPackages - toolPackage.name
                                        } else {
                                            expandedPackages + toolPackage.name
                                        }
                                    },
                                    onValueChange = { name, value ->
                                        editableValues = editableValues.toMutableMap().apply {
                                            this[name] = value
                                        }
                                    }
                                )
                            }
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

@Composable
private fun EnvironmentVariablesSummary(
    configuredCount: Int,
    totalCount: Int,
    missingRequiredCount: Int
) {
    val progress = configuredCount.toFloat() / totalCount.toFloat()
    val complete = configuredCount == totalCount
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.pkg_env_vars_status),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$configuredCount / $totalCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when {
                        missingRequiredCount > 0 -> {
                            stringResource(R.string.pkg_env_vars_missing_required, missingRequiredCount)
                        }
                        complete -> stringResource(R.string.pkg_env_vars_complete)
                        else -> stringResource(R.string.pkg_env_vars_required_complete)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (missingRequiredCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun PackageEnvironmentVariablesSection(
    toolPackage: ToolPackage,
    values: Map<String, String>,
    expanded: Boolean,
    configuredCount: Int,
    missingRequiredCount: Int,
    context: Context,
    onToggleExpanded: () -> Unit,
    onValueChange: (String, String) -> Unit
) {
    val packageName = toolPackage.name
    val packageDescription = toolPackage.description.resolve(context).trim()
    val environmentVariables = toolPackage.env
    val packageInitial = packageName.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    val complete = missingRequiredCount == 0 && configuredCount == environmentVariables.size
    val status = when {
        missingRequiredCount > 0 -> stringResource(
            R.string.pkg_env_vars_missing_required,
            missingRequiredCount
        )
        complete -> stringResource(R.string.pkg_env_vars_complete)
        else -> stringResource(
            R.string.pkg_env_vars_configured_count,
            configuredCount,
            environmentVariables.size
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (expanded) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = packageInitial,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (packageDescription.isNotBlank()) {
                        Text(
                            text = packageDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missingRequiredCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else if (complete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    environmentVariables.forEach { environmentVariable ->
                        EnvironmentVariableField(
                            environmentVariable = environmentVariable,
                            value = values[environmentVariable.name].orEmpty(),
                            context = context,
                            onValueChange = { onValueChange(environmentVariable.name, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentVariableField(
    environmentVariable: EnvVar,
    value: String,
    context: Context,
    onValueChange: (String) -> Unit
) {
    var isValueVisible by remember(environmentVariable.name) { mutableStateOf(false) }
    val description = environmentVariable.description.resolve(context).trim()
    val hasValue = value.isNotBlank()
    val isSensitive = environmentVariable.name.isSensitiveEnvironmentVariableName()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = environmentVariable.name.toEnvironmentVariableLabel(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when {
                    environmentVariable.required && !hasValue -> {
                        stringResource(R.string.pkg_env_vars_required)
                    }
                    hasValue -> stringResource(R.string.pkg_env_vars_configured)
                    else -> stringResource(R.string.pkg_env_vars_optional)
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    environmentVariable.required && !hasValue -> MaterialTheme.colorScheme.error
                    hasValue -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Text(
            text = description.ifBlank { stringResource(R.string.pkg_env_vars_missing_description) },
            style = MaterialTheme.typography.bodySmall,
            color = if (description.isBlank()) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        environmentVariable.defaultValue?.takeIf { it.isNotBlank() }?.let { defaultValue ->
            Text(
                text = stringResource(R.string.pkg_default, defaultValue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = environmentVariable.required && !hasValue,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = if (environmentVariable.required) {
                        stringResource(R.string.pkg_input_required)
                    } else {
                        stringResource(R.string.pkg_input_optional)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingIcon = if (isSensitive) {
                {
                    IconButton(onClick = { isValueVisible = !isValueVisible }) {
                        Icon(
                            imageVector = if (isValueVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isValueVisible) {
                                stringResource(R.string.pkg_env_vars_hide_value)
                            } else {
                                stringResource(R.string.pkg_env_vars_show_value)
                            }
                        )
                    }
                }
            } else if (hasValue) {
                {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.pkg_env_vars_configured),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                null
            },
            visualTransformation = if (isSensitive && !isValueVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            shape = RoundedCornerShape(8.dp)
        )
    }
}

private fun String.isSensitiveEnvironmentVariableName(): Boolean {
    val normalized = lowercase()
    return listOf("key", "token", "secret", "password", "passwd", "credential").any {
        normalized.contains(it)
    }
}

private fun String.toEnvironmentVariableLabel(): String {
    val words = substringBeforeLast("_API_KEY", missingDelimiterValue = this)
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
    return when {
        endsWith("_API_KEY") -> "$words API Key"
        endsWith("_TOKEN") -> "${substringBeforeLast("_TOKEN").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} Token"
        endsWith("_URL") -> "${substringBeforeLast("_URL").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} URL"
        else -> replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
