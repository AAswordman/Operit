package com.ai.assistance.operit.ui.features.support.demo.wizards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

/**
 * Root权限设置向导卡片
 *
 * @param isDeviceRooted 设备是否已Root
 * @param hasRootAccess 应用是否拥有Root权限
 * @param showWizard 是否显示向导详情
 * @param onToggleWizard 切换向导显示状态的回调
 * @param onRequestRoot 请求Root权限的回调
 * @param onWatchTutorial 查看教程的回调
 */
@Composable
fun RootWizardCard(
        isDeviceRooted: Boolean,
        hasRootAccess: Boolean,
        showWizard: Boolean,
        onToggleWizard: () -> Unit,
        onRequestRoot: () -> Unit,
        onWatchTutorial: () -> Unit
) {
        Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                shadowElevation = 0.dp
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // 标题栏
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                stringResource(R.string.root_wizard_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onBackground
                                        )
                                }

                                TextButton(
                                        onClick = onToggleWizard,
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                ) {
                                        Text(
                                                if (showWizard)
                                                        stringResource(R.string.wizard_collapse)
                                                else stringResource(R.string.wizard_expand),
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 进度和状态
                        val isComplete = hasRootAccess
                        val progress =
                                when {
                                        !isDeviceRooted -> 0f
                                        !hasRootAccess -> 0.5f
                                        else -> 1f
                                }

                        Surface(
                                color =
                                        if (isComplete)
                                                MaterialTheme.colorScheme.primary.copy(
                                                        alpha = 0.08f
                                                )
                                        else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                        // 进度指示器
                                        LinearProgressIndicator(
                                                progress = progress,
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 当前状态
                                        val statusText =
                                                when {
                                                        !isDeviceRooted ->
                                                                stringResource(
                                                                        R.string.root_wizard_step1
                                                                )
                                                        !hasRootAccess ->
                                                                stringResource(
                                                                        R.string.root_wizard_step2
                                                                )
                                                        else ->
                                                                stringResource(
                                                                        R.string
                                                                                .root_wizard_completed
                                                                )
                                                }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isComplete) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                }

                                                Text(
                                                        text = statusText,
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .SemiBold
                                                                        ),
                                                        color =
                                                                if (isComplete)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                )
                                        }
                                }
                        }

                        // 详细设置内容，仅在展开时显示
                        AnimatedVisibility(visible = showWizard) {
                                Column {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        when {
                                        // 已获取Root权限
                                        hasRootAccess -> {
                                                Surface(
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer,
                                                        shape = RoundedCornerShape(8.dp)
                                                ) {
                                                        Row(
                                                                modifier = Modifier.padding(16.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .CheckCircle,
                                                                        contentDescription = null,
                                                                        tint =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onPrimaryContainer,
                                                                        modifier =
                                                                                Modifier.size(24.dp)
                                                                )

                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(
                                                                                        12.dp
                                                                                )
                                                                )

                                                                Text(
                                                                        stringResource(
                                                                                R.string
                                                                                        .root_wizard_success_message
                                                                        ),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .bodyMedium,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onPrimaryContainer
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))

                                                // 测试命令按钮
                                                OutlinedButton(
                                                        onClick = {
                                                                // 这里直接使用 onRequestRoot 来执行一个示例命令
                                                                onRequestRoot()
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding =
                                                                PaddingValues(vertical = 12.dp)
                                                ) {
                                                        Text(
                                                                stringResource(
                                                                        R.string
                                                                                .root_wizard_test_command
                                                                ),
                                                                fontSize = 14.sp
                                                        )
                                                }
                                        }

                                        // 设备已Root但应用未获授权
                                        isDeviceRooted -> {
                                                Column {
                                                        Text(
                                                                stringResource(
                                                                        R.string
                                                                                .root_wizard_device_rooted_message
                                                                ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        Surface(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                                .copy(alpha = 0.3f),
                                                                shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                R.string
                                                                                                        .root_wizard_how_to_get_root
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodyMedium
                                                                                                .copy(
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Bold
                                                                                                ),
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface
                                                                        )

                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                4.dp
                                                                                        )
                                                                        )

                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                R.string
                                                                                                        .root_wizard_how_to_get_root_steps
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                        }

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        Button(
                                                                onClick = onRequestRoot,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding =
                                                                        PaddingValues(
                                                                                vertical = 12.dp
                                                                        )
                                                        ) {
                                                                Text(
                                                                        stringResource(
                                                                                R.string
                                                                                        .root_wizard_request_permission
                                                                        ),
                                                                        fontSize = 14.sp
                                                                )
                                                        }

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        OutlinedButton(
                                                                onClick = onWatchTutorial,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding =
                                                                        PaddingValues(
                                                                                vertical = 12.dp
                                                                        )
                                                        ) {
                                                                Text(
                                                                        stringResource(
                                                                                R.string
                                                                                        .root_wizard_view_tutorial
                                                                        ),
                                                                        fontSize = 14.sp
                                                                )
                                                        }
                                                }
                                        }

                                        // 设备未Root
                                        else -> {
                                                Column {
                                                        Text(
                                                                stringResource(
                                                                        R.string
                                                                                .root_wizard_device_not_rooted
                                                                ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        Surface(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .errorContainer,
                                                                shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        12.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                R.string
                                                                                                        .root_wizard_risk_notice
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall
                                                                                                .copy(
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Bold
                                                                                                ),
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onErrorContainer
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        stringResource(
                                                                                                R.string
                                                                                                        .root_wizard_risk_details
                                                                                        ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodySmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onErrorContainer
                                                                        )
                                                                }
                                                        }

                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        ElevatedButton(
                                                                onClick = onWatchTutorial,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding =
                                                                        PaddingValues(
                                                                                vertical = 12.dp
                                                                        )
                                                        ) {
                                                                Text(
                                                                        stringResource(
                                                                                R.string
                                                                                        .root_wizard_view_tutorial
                                                                        ),
                                                                        fontSize = 14.sp
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}
}
