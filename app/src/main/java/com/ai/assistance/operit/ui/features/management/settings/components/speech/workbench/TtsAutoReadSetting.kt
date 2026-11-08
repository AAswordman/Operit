package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 全局朗读沿用已发布的存储键，切换 TTS 档案不会改变这个开关。 */
@Composable
internal fun TtsAutoReadSetting() {
    val context = LocalContext.current
    val preferences = remember { ApiPreferences.getInstance(context.applicationContext) }
    val enabled: Boolean? by preferences.enableAutoReadFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SpeechWorkbenchCard {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = enabled == true,
                    enabled = enabled != null && !saving,
                    role = Role.Switch,
                    onValueChange = { checked ->
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                preferences.saveEnableAutoRead(checked)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                AppLogger.e("TtsAutoReadSetting", "保存自动朗读设置失败", failure)
                                error = failure.localizedMessage ?: failure.javaClass.simpleName
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.auto_read_message), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.auto_read_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled == true, onCheckedChange = null, enabled = enabled != null && !saving)
        }
        error?.let { detail ->
            Text(
                stringResource(R.string.speech_workbench_save_error, detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}