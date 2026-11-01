package com.ai.assistance.operit.core.chat.voice

import com.ai.assistance.operit.data.model.AttachmentInfo
import java.io.IOException
import org.json.JSONObject
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class VoiceAnalysisTest {
    private val source = VoiceMessageData(
        AttachmentInfo("/tmp/voice.wav", "voice.wav", "audio/wav", 64044L),
        2000L, listOf(0.2f), "", VoiceInputMode.AUDIO,
    )

    @Test
    fun analysisKeepsSpokenWordsAndObservationsSeparate() {
        val decoded = VoiceAnalysisCodec.decode(JSONObject().put("transcript", "你是我的小猫吗")
            .put("audio_context", "测试观察字段").toString())
        val saved = VoiceMessageMarkup.withResolvedAnalysis(VoiceMessageMarkup.encode(source), source, decoded)
        val message = VoiceMessageMarkup.parseMessage(saved)!!
        assertEquals("你是我的小猫吗", message.transcript)
        assertEquals("测试观察字段", message.audioContext)
        assertEquals(source.attachment, message.attachment)
        assertTrue(message.transcriptResolved)
        assertEquals("你是我的小猫吗", VoiceMessageMarkup.toModelText(saved))
        assertTrue(decoded.toModelText().contains("测试观察字段"))
    }

    @Test(expected = IOException::class)
    fun backgroundDescriptionCannotReplaceMissingSpeech() {
        VoiceAnalysisCodec.decode(JSONObject().put("transcript", "").put("audio_context", "只有背景描述").toString())
    }

    @Test(expected = JSONException::class)
    fun missingTranscriptFieldIsRejected() {
        VoiceAnalysisCodec.decode(JSONObject().put("audio_context", "只有背景描述").toString())
    }

    @Test
    fun nonStringAnalysisFieldsAreRejectedInsteadOfBecomingSpokenWords() {
        for (value in listOf(JSONObject.NULL, 42, JSONObject())) {
            val transcript = JSONObject().put("transcript", value).put("audio_context", "")
            assertThrows(IOException::class.java) { VoiceAnalysisCodec.decode(transcript.toString()) }
            val context = JSONObject().put("transcript", "原话").put("audio_context", value)
            assertThrows(IOException::class.java) { VoiceAnalysisCodec.decode(context.toString()) }
        }
    }

    @Test
    fun emptyObservationsAreValidAndSttDoesNotInventThem() {
        val decoded = VoiceAnalysisCodec.decode(JSONObject().put("transcript", "原话").put("audio_context", "").toString())
        assertEquals("原话", decoded.toModelText())
        assertEquals("", VoiceTextResult("原话", VoiceTextSource.STT).audioContext)
    }

    @Test
    fun oldMessagesWithoutObservationTagsStillRoundTrip() {
        val old = source.copy(transcript = "历史转写", transcriptResolved = true)
        assertEquals(old, VoiceMessageMarkup.parse(VoiceMessageMarkup.encode(old)))
    }

    @Test
    fun historicalVoiceKeepsTextObservationsWithoutResendingRecording() {
        val analysis = VoiceAnalysis("原话", "测试观察字段")
        val saved = VoiceMessageMarkup.withResolvedAnalysis(VoiceMessageMarkup.encode(source), source, analysis)
        val history = com.ai.assistance.operit.core.chat.media.currentTurnMediaHistory(
            listOf(com.ai.assistance.operit.core.chat.hooks.PromptTurn(
                com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.USER, saved)),
            "下一条消息", "省略媒体",
        ).single().content
        assertTrue(history.contains("原话"))
        assertTrue(history.contains("测试观察字段"))
        assertFalse(history.contains(source.attachment.filePath))
        assertFalse(history.contains("<voice_bar"))
    }

    @Test
    fun observationsEscapeMarkupAndAdditionalTextStaysOutsideTranscript() {
        val hooked = source.copy(transcript = "额外请求文字")
        val analysis = VoiceAnalysis("用户原话", "观察 <声音> & 原样")
        val saved = VoiceMessageMarkup.withResolvedAnalysis(VoiceMessageMarkup.encode(hooked), hooked, analysis)
        val parsed = VoiceMessageMarkup.parse(saved)!!
        assertEquals("用户原话", parsed.transcript)
        assertEquals(analysis.audioContext, parsed.audioContext)
        assertEquals("额外请求文字", parsed.requestContext)
        assertTrue(analysis.toModelText(parsed.requestContext).contains("额外请求文字"))
        assertEquals(saved, VoiceMessageMarkup.withResolvedAnalysis(saved, hooked, analysis))
    }
    @Test
    fun resolvedAudioReusesTranscriptWhileUnresolvedAudioHonorsCapability() {
        assertTrue(source.canSendNativeAudio(true))
        assertFalse(source.canSendNativeAudio(false))
        val recognized = source.copy(transcript = "已识别原话", transcriptResolved = true)
        assertFalse(recognized.canSendNativeAudio(true))
        assertFalse(recognized.canSendNativeAudio(false))
        val hookText = source.copy(transcript = "额外提示", transcriptResolved = false)
        assertTrue(hookText.canSendNativeAudio(true))
    }
}
