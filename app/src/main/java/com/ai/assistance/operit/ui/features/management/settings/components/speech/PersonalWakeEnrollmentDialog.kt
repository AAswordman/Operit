package com.ai.assistance.operit.ui.features.management.settings.components.speech

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptFilledTonalButton as FilledTonalButton

import com.ai.assistance.operit.ui.common.oktoast.OKToast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.wake.PersonalWakeEnrollment
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechWorkbenchSheet
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 录制三次个人唤醒样本，全部完成后保存为现有模板格式。 */
@Composable
internal fun PersonalWakeEnrollmentDialog(wakePrefs: WakeWordPreferences, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step1 by remember { mutableStateOf<FloatArray?>(null) }
    var step2 by remember { mutableStateOf<FloatArray?>(null) }
    var step3 by remember { mutableStateOf<FloatArray?>(null) }
    var recordingStep by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) recordingJob?.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SpeechWorkbenchSheet(
        title = stringResource(R.string.voice_wakeup_personal_config_dialog_title),
        onDismiss = onDismiss,
        dismissEnabled = recordingStep == 0 && !saving,
    ) {
        Text(
            text = stringResource(R.string.voice_wakeup_personal_config_dialog_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        @Composable
        fun stepRow(index: Int, value: FloatArray?, onRecord: () -> Unit) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.voice_wakeup_personal_config_step, index),
                    style = MaterialTheme.typography.bodyMedium
                )

                val busy = recordingStep == index
                val label =
                    when {
                        busy -> stringResource(R.string.voice_wakeup_personal_config_recording)
                        value != null -> stringResource(R.string.voice_wakeup_personal_config_record_done)
                        else -> stringResource(R.string.voice_wakeup_personal_config_record)
                    }
                FilledTonalButton(
                    onClick = onRecord,
                    enabled = recordingStep == 0 && !saving,
                ) {
                    Text(text = label)
                }
            }
        }

        stepRow(1, step1) {
            recordingStep = 1
            recordingJob = scope.launch {
                try {
                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                    if (feat != null) step1 = feat
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("PersonalWakeEnrollment", "录制个人唤醒模板失败", error)
                    OKToast.error(context, context.getString(R.string.recognition_error, error.message.orEmpty()), OKToast.LENGTH_SHORT).show()
                } finally {
                    recordingStep = 0
                }
            }
        }
        stepRow(2, step2) {
            recordingStep = 2
            recordingJob = scope.launch {
                try {
                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                    if (feat != null) step2 = feat
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("PersonalWakeEnrollment", "录制个人唤醒模板失败", error)
                    OKToast.error(context, context.getString(R.string.recognition_error, error.message.orEmpty()), OKToast.LENGTH_SHORT).show()
                } finally {
                    recordingStep = 0
                }
            }
        }
        stepRow(3, step3) {
            recordingStep = 3
            recordingJob = scope.launch {
                try {
                    val feat = PersonalWakeEnrollment.recordOneTemplate(context)
                    if (feat != null) step3 = feat
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.e("PersonalWakeEnrollment", "录制个人唤醒模板失败", error)
                    OKToast.error(context, context.getString(R.string.recognition_error, error.message.orEmpty()), OKToast.LENGTH_SHORT).show()
                } finally {
                    recordingStep = 0
                }
            }
        }
        val canSave = step1 != null && step2 != null && step3 != null && recordingStep == 0 && !saving
        Button(
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!canSave) return@Button
                saving = true
                scope.launch {
                    try {
                        val templates = listOf(step1!!, step2!!, step3!!).map { features ->
                            WakeWordPreferences.PersonalWakeTemplate(features = features.toList())
                        }
                        wakePrefs.savePersonalWakeTemplates(templates)
                        onDismiss()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e("PersonalWakeEnrollment", "保存个人唤醒模板失败", error)
                        OKToast.error(context, context.getString(R.string.recognition_error, error.message.orEmpty()), OKToast.LENGTH_SHORT).show()
                    } finally {
                        saving = false
                    }
                }
            }
        ) {
            Text(text = stringResource(R.string.voice_wakeup_personal_config_save))
        }

    }
}
