package com.ai.assistance.operit.ui.features.chat.components

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay
import java.util.Locale

internal fun formatResponseElapsed(elapsedMs: Long): String {
    val seconds = elapsedMs.coerceAtLeast(0L) / 1000
    val hours = seconds / 3600
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, (seconds % 3600) / 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}

@Composable
internal fun LiveResponseTimer(startedAt: Long) {
    var elapsed by remember(startedAt) { mutableLongStateOf(SystemClock.elapsedRealtime() - startedAt) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(1000)
        }
    }
    Text(
        stringResource(R.string.chat_live_elapsed, formatResponseElapsed(elapsed)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
