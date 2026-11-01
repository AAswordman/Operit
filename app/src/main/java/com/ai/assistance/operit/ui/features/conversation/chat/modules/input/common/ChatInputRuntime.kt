package com.ai.assistance.operit.ui.features.conversation.chat.modules.input.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.features.conversation.chat.modules.usage.ChatContextUsageEntry
import com.ai.assistance.operit.ui.features.conversation.chat.modules.usage.ChatContextUsageDialog

/** 输入展示接收状态和动作，制作预览无需创建真实会话 ViewModel。 */
internal data class ChatInputRuntime(
    val currentWindowSize: Long,
    val maxWindowSizeInK: Float,
    val modelName: String,
    val onVoiceCall: () -> Unit,
    val contextUsageEntry: @Composable (onClick: () -> Unit) -> Unit,
    val contextUsageDialog: @Composable (onDismiss: () -> Unit) -> Unit,
)

@Composable
internal fun rememberChatInputRuntime(viewModel: ChatViewModel): ChatInputRuntime {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val windowSize by viewModel.currentWindowSize.collectAsState()
    val maximum by viewModel.maxWindowSizeInK.collectAsState()
    val model by viewModel.modelName.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.launchFloatingModeIn(FloatingMode.FULLSCREEN, colors, typography)
        else OKToast.warning(context, context.getString(R.string.microphone_permission_denied_toast)).show()
    }
    return ChatInputRuntime(
        currentWindowSize = windowSize,
        maxWindowSizeInK = maximum,
        modelName = model,
        onVoiceCall = { viewModel.onFloatingButtonClick(FloatingMode.FULLSCREEN, launcher, colors, typography) },
        contextUsageEntry = { onClick -> ChatContextUsageEntry(viewModel, onClick = onClick) },
        contextUsageDialog = { onDismiss -> ChatContextUsageDialog(viewModel, onDismiss = onDismiss) },
    )
}