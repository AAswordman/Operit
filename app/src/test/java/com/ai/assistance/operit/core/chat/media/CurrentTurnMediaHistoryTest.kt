package com.ai.assistance.operit.core.chat.media

import com.ai.assistance.operit.core.chat.media.MediaLinkParser
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.chat.hooks.applyFinalizedCurrentUserTurn
import com.ai.assistance.operit.core.chat.voice.VoiceInputMode
import com.ai.assistance.operit.core.chat.voice.VoiceMessageData
import com.ai.assistance.operit.core.chat.voice.VoiceMessageMarkup
import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import org.junit.Assert.*
import org.junit.Test

class CurrentTurnMediaHistoryTest {
    private val oldMedia = """旧说明 <link type="image" id="image-old"/> <link type="audio" id="audio-old"/> <link type="video" id="video-old"/> <link type="file" id="pdf-old" filename="old.pdf"/>"""
    private val current = """当前输入 <link type="image" id="image-current"/>"""
    private fun user(content: String) = PromptTurn(PromptTurnKind.USER, content)
    private fun filter(history: List<PromptTurn>, message: String = current) =
        currentTurnMediaHistory(history, message, "[媒体已省略]")

    private fun assertNoMedia(value: String) {
        assertFalse(MediaLinkParser.hasImageLinks(value))
        assertFalse(MediaLinkParser.hasMediaLinks(value))
    }

    @Test fun currentInputIsPreservedAndPreviousMediaIsRemovedWithoutChangingSavedHistory() {
        val attachment = """ <attachment id="/cleanOnExit/photo.jpg" filename="photo.jpg" type="image/jpeg"/>"""
        val saved = listOf(user(oldMedia + attachment), PromptTurn(PromptTurnKind.ASSISTANT, "回复"), user(current))
        val result = filter(saved)
        assertNoMedia(result.first().content)
        assertTrue(result.first().content.contains(attachment))
        assertTrue(result.first().content.contains("旧说明"))
        assertSame(saved.last(), result.last())
        assertEquals(oldMedia + attachment, saved.first().content)
    }

    @Test fun currentInputNotYetInHistoryIsAddedOnceAfterOldMediaIsRemoved() {
        val history = listOf(user(oldMedia), PromptTurn(PromptTurnKind.ASSISTANT, "已回复"))
        val request = applyFinalizedCurrentUserTurn(filter(history), current, current)
        assertNoMedia(request.first().content)
        assertEquals(listOf("image-current"), request.flatMap { MediaLinkParser.extractImageLinkIds(it.content) })
        assertEquals(current, request.last().content)
    }

    @Test fun consecutiveIdenticalInputsOnlyKeepLastMedia() {
        val result = filter(listOf(user(current), user(current)))
        assertNoMedia(result.first().content)
        assertEquals(current, result.last().content)
    }

    @Test fun matchingOldInputBeforeAssistantReplyIsNotTreatedAsCurrent() {
        val result = filter(listOf(user(current), PromptTurn(PromptTurnKind.ASSISTANT, "已回复")))
        assertNoMedia(result.first().content)
    }

    @Test fun groupUserPrefixAndWhitespaceDoNotDiscardCurrentMedia() {
        val last = user("[From user]\n  $current  ")
        val result = filter(listOf(user(oldMedia), last), "[From user]\n$current")
        assertNoMedia(result.first().content)
        assertSame(last, result.last())
    }

    @Test fun roleBridgeIsNotTreatedAsCurrentUser() {
        val result = filter(listOf(user("[From role: 角色]\n$current")))
        assertNoMedia(result.single().content)
    }

    @Test fun continuationRemovesMediaFromEveryHistoricalTurnAndPreservesToolMetadata() {
        val tool = PromptTurn(PromptTurnKind.TOOL_RESULT, oldMedia, "screenshot", mapOf("callId" to "call-1"))
        val result = filter(listOf(user(current), tool), "")
        result.forEach { assertNoMedia(it.content) }
        assertEquals(tool.toolName, result.last().toolName)
        assertEquals(tool.metadata, result.last().metadata)
    }

    @Test fun historicalVoiceKeepsTranscriptWithoutReopeningAudio() {
        val voice = VoiceMessageMarkup.encode(VoiceMessageData(
            attachment = AttachmentInfo("/cleanOnExit/voice.wav", "voice.wav", "audio/wav", 32044),
            durationMs = 1000,
            waveform = listOf(0.5f),
            transcript = "已识别的文字",
            inputMode = VoiceInputMode.AUDIO,
            transcriptResolved = true,
        ))
        val saved = listOf(user(voice), user(current))
        val result = filter(saved)
        assertEquals("已识别的文字", result.first().content)
        assertEquals(voice, saved.first().content)
        assertSame(saved.last(), result.last())
    }

    @Test fun currentVoiceRemainsAvailableForAudioRequestFormatting() {
        val voice = VoiceMessageMarkup.encode(VoiceMessageData(
            AttachmentInfo("/cleanOnExit/new.wav", "new.wav", "audio/wav", 32044),
            1000, emptyList(), "", VoiceInputMode.AUDIO,
        ))
        assertEquals(voice, filter(listOf(user(voice)), voice).single().content)
    }

    @Test fun mediaOnlyHistoryUsesOmissionLabelAndPlainTextStaysUnchanged() {
        val text = user("普通文字")
        val result = filter(listOf(user("""<link type="video" id="old"/>"""), text), "")
        assertEquals("[媒体已省略]", result.first().content)
        assertSame(text, result.last())
    }
}
