package com.ai.assistance.operit.ui.common.a2ui.submission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.a2ui.A2uiOwner

@Composable
internal fun rememberCardSubmissionStatus(
    owner: A2uiOwner?, document: String, surface: String,
): CardSubmissionStore.Status {
    val state = remember(owner?.chatId, owner?.timestamp, owner?.variant, document, surface) {
        owner?.let { CardSubmissionStore.current.observe(it, document, surface) }
    }
    return state?.collectAsState()?.value ?: CardSubmissionStore.Status.Inactive
}

@Composable
internal fun CardSubmissionNotice(status: CardSubmissionStore.Status, onCancel: () -> Unit) {
    val message = when (status) {
        CardSubmissionStore.Status.Waiting -> R.string.a2ui_waiting_submission
        CardSubmissionStore.Status.Submitted -> R.string.a2ui_wait_submitted
        CardSubmissionStore.Status.TimedOut -> R.string.a2ui_wait_timed_out
        CardSubmissionStore.Status.Cancelled -> R.string.a2ui_wait_cancelled
        CardSubmissionStore.Status.Inactive -> R.string.a2ui_wait_inactive
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(message), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == CardSubmissionStore.Status.Waiting) TextButton(onClick = onCancel) {
            Text(stringResource(R.string.a2ui_cancel_wait))
        }
    }
}