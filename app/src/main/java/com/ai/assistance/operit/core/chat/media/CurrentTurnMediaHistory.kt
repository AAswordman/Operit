package com.ai.assistance.operit.core.chat.media

import com.ai.assistance.operit.core.chat.media.MediaLinkParser
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.voice.VoiceMessageMarkup

/** 只为当前用户轮次保留媒体输入；原聊天记录和附件文件不在此处修改。 */
internal fun currentTurnMediaHistory(
    history: List<PromptTurn>,
    currentMessage: String,
    omittedLabel: String,
): List<PromptTurn> {
    val currentText = currentMessage.removePrefix("[From user]\n").trim()
    return history.mapIndexed { index, turn ->
        // 只认历史末尾的当前输入，不能把已回复的同文旧消息当作新一轮。
        val isCurrent = currentText.isNotBlank() && index == history.lastIndex &&
            turn.kind == PromptTurnKind.USER &&
            turn.content.removePrefix("[From user]\n").trim() == currentText
        if (isCurrent) {
            turn
        } else {
            val text = VoiceMessageMarkup.toHistoryModelText(turn.content)
            val withoutMedia = MediaLinkParser.removeMediaLinks(MediaLinkParser.removeImageLinks(text))
            if (withoutMedia == turn.content) turn
            else turn.copy(content = withoutMedia.trim().ifBlank { omittedLabel })
        }
    }
}