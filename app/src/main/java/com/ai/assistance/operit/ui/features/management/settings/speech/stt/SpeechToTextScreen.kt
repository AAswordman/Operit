package com.ai.assistance.operit.ui.features.management.settings.speech.stt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import com.ai.assistance.operit.ui.features.management.settings.components.speech.SpeechRecognitionTestSection

/** 内部识别测试路由复用语音服务中的测试区。 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SpeechToTextScreen(navController: NavController) {
    val context = LocalContext.current
    val preferences = remember { SpeechServiceProfilesPreferences(context) }
    val profile by preferences.currentSttProfileOrNullFlow.collectAsState(initial = null)
    val current = profile
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SpeechRecognitionTestSection(profileId = current.id, serviceType = current.serviceType, httpConfig = current.httpConfig)
        }
    }
}