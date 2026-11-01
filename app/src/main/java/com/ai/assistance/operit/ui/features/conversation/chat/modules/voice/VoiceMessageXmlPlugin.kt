package com.ai.assistance.operit.ui.features.conversation.chat.modules.voice

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.core.chat.voice.VoiceMessageMarkup
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPlugin
import com.ai.assistance.operit.ui.common.markdown.XmlRenderResult
import com.ai.assistance.operit.util.stream.Stream

object VoiceMessageXmlPlugin : XmlRenderPlugin {
    override val id = "builtin.voice-message"
    override fun supports(tagName: String) = tagName.equals("voice_bar", ignoreCase = true)
    override suspend fun resolve(context: Context, xmlContent: String, tagName: String,
        textColor: Color, xmlStream: Stream<String>?): XmlRenderResult? {
        val message = VoiceMessageMarkup.parse(xmlContent) ?: return null
        return XmlRenderResult.ComposableRender { modifier, _, _ ->
            VoiceMessagePlayer(message.attachment.filePath, message.durationMs, message.waveform, message.transcript, modifier, contentColor = textColor)
        }
    }
}
