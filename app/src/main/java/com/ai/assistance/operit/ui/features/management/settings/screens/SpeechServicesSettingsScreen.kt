package com.ai.assistance.operit.ui.features.management.settings.screens

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench.SpeechServicesWorkbench

/** 保留设置路由的调用契约，具体界面由语音工作区组织。 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SpeechServicesSettingsScreen(
    onBackPressed: () -> Unit,
    initialTabIndex: Int = 0,
) {
    SpeechServicesWorkbench(initialTabIndex = initialTabIndex)
}
