package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.oktoast.OKToast

/** 过期语音作为气泡外的独立媒体入口，正常语音仍只显示语音条。 */
@Composable
internal fun ExpiredVoiceMessageTag(enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val message = stringResource(R.string.chat_media_file_expired)
    Surface(
        modifier = modifier.clickable(enabled = enabled, role = Role.Button) {
            OKToast.warning(context, message).show()
        },
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(R.string.chat_expired_audio_label),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
