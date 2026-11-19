package com.ai.assistance.operit.ui.health

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.OperitUtilityTheme

/**
 * 展示 Health Connect 健康数据权限的用途说明（隐私政策 / 授权理由）。
 *
 * Health Connect 的权限授权界面在关闭之前会校验请求方应用是否声明了
 * "查看权限用途" 入口，即 `android.intent.action.VIEW_PERMISSION_USAGE`
 * 与 `android.intent.category.HEALTH_PERMISSIONS`。若未声明，
 * `PermissionsActivity` 会直接 `finish()`，表现为「跳转到授权页后点允许没有任何反应、
 * 设置里依然是未授权」。
 *
 * 因此本 Activity 必须在 manifest 中通过两个入口暴露：
 *  - Android 13 及以下（APK 版 Health Connect）：`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
 *  - Android 14 及以上（系统内置 Health Connect）：`activity-alias` +
 *    `android.intent.action.VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS`
 *
 * @see <a href="https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started">Health Connect 集成文档</a>
 */
class HealthConnectPermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OperitUtilityTheme { HealthConnectRationaleScreen() } }
    }
}

@Composable
private fun HealthConnectRationaleScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.health_rationale_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.health_rationale_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.health_rationale_items_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.health_rationale_items),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.health_rationale_storage_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.health_rationale_storage),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.health_rationale_revoke),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Text(
                        text = stringResource(R.string.health_rationale_settings_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(onClick = { /* 用户可返回 Health Connect 继续授权 */ }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        }
    }
}
