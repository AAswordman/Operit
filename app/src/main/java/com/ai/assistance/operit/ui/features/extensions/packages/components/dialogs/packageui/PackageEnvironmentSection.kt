package com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.EnvVar

/** 展示包声明，不读取或展示环境变量的实际配置值。 */
@Composable
internal fun PackageEnvironmentSection(variables: List<EnvVar>) {
    if (variables.isEmpty()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.pkg_declared_environment_variables),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        variables.forEach { variable ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = variable.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Medium,
                        )
                        val description = variable.description.resolve(context)
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (variable.required) MaterialTheme.colorScheme.primaryContainer else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (variable.required) MaterialTheme.colorScheme.onPrimaryContainer else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (variable.required) R.string.pkg_environment_required else R.string.pkg_environment_optional
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** 运行页面只说明环境变量的配置位置，不将其当成这次调用的参数。 */
@Composable
internal fun PackageEnvironmentNotice(variables: List<EnvVar>) {
    if (variables.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                R.string.pkg_run_environment_notice,
                variables.joinToString(", ") { it.name },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
