package com.ai.assistance.operit.ui.floating

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatKind

/**
 * A subagent chat belongs to the agent tree, not to the user: the main chat screen shows such a
 * transcript read-only, so every other surface must treat it the same way.
 */
internal fun ChatHistory?.isReadOnlyTranscript(): Boolean =
    this?.chatKind == ChatKind.SUBAGENT.name

/** Applies the read-only transcript rule to the chat the floating surface currently displays. */
@Composable
internal fun rememberIsReadOnlyTranscript(floatContext: FloatContext): Boolean {
    val core = floatContext.chatService?.getChatCore() ?: return false
    val histories by core.chatHistories.collectAsState(initial = emptyList())
    val currentChatId by core.currentChatId.collectAsState(initial = null)
    return remember(histories, currentChatId) {
        histories.firstOrNull { it.id == currentChatId }.isReadOnlyTranscript()
    }
}

/** States why the floating surface offers no composer for this conversation. */
@Composable
internal fun ReadOnlyTranscriptNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.floating_chat_subagent_read_only),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
