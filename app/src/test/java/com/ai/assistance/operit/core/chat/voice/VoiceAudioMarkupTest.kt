package com.ai.assistance.operit.core.chat.voice

import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import org.junit.Assert.*
import org.junit.Test

class VoiceAudioMarkupTest {
    private fun audio(text: String = "") = VoiceMessageData(
        AttachmentInfo("/data/user/0/example/files/media_pool/voice/voice-new.wav", "voice-new.wav", "audio/wav", 64044),
        2000L,
        listOf(0f, 0.2f, 0.35f, 1f),
        text,
        VoiceInputMode.AUDIO,
    )

    @Test
    fun newAudioWithoutTranscriptRoundTrips() {
        val source = audio()
        val encoded = VoiceMessageMarkup.encode(source)
        assertTrue(encoded.startsWith("<voice_bar version=\"2\" input=\"audio\""))
        assertEquals(source, VoiceMessageMarkup.parse(encoded))
        assertEquals(encoded, VoiceMessageMarkup.toRequestContent(encoded))
    }

    @Test
    fun oldVoiceStillBecomesTextWhileNewAudioSurvivesRequestPreparation() {
        val old = audio("旧转写").copy(inputMode = VoiceInputMode.TEXT)
        val new = VoiceMessageMarkup.encode(audio())
        val workspace = "<workspace_attachment>工作区信息</workspace_attachment>"
        assertEquals("旧转写\n$new\n$workspace", VoiceMessageMarkup.toRequestContent(
            "${VoiceMessageMarkup.encode(old)}\n$new\n$workspace"))
    }

    @Test
    fun requestPreparationDoesNotRewriteRawAudioOrUnrelatedMarkup() {
        val raw = VoiceMessageMarkup.encode(audio()).replace("0.0,0.2,0.35,1.0", "0.00,0.20,0.350,1.00")
        val value = "<proxy_sender name=\"测试\"/> $raw <voice_bar>损坏数据</voice_bar>"
        assertEquals(value, VoiceMessageMarkup.toRequestContent(value))
        assertEquals(value, VoiceMessageMarkup.withResolvedTranscript(value, audio().copy(durationMs = 3000), "无关结果"))
    }

    @Test
    fun resolvedTextRetainsRecordingAndMetadataAndIsOnlyWrittenOnce() {
        val source = audio("钩子补充")
        val workspace = "<workspace_attachment>工作区信息</workspace_attachment>"
        val saved = "${VoiceMessageMarkup.encode(source)} $workspace"
        val text = "轻声说：明天提醒我 <检查> & 确认\n钩子补充"
        val updated = VoiceMessageMarkup.withResolvedTranscript(saved, source, text)
        val expected = source.copy(transcript = text, transcriptResolved = true)
        assertEquals(expected, VoiceMessageMarkup.parseMessage(updated))
        assertTrue(updated.endsWith(workspace))
        assertEquals(updated, VoiceMessageMarkup.toRequestContent(updated))
        assertEquals(text, VoiceMessageMarkup.parseMessage(updated)!!.transcript)
        assertEquals(source.attachment, VoiceMessageMarkup.parseMessage(updated)!!.attachment)
        assertEquals(updated, VoiceMessageMarkup.withResolvedTranscript(updated, source, "过期结果"))
    }

    @Test
    fun textRequestRetainsOriginalAttachmentWithoutPollutingSavedTranscript() {
        val source = audio()
        val text = "识别出的语音内容"
        val saved = VoiceMessageMarkup.withResolvedTranscript(VoiceMessageMarkup.encode(source), source, text)
        val resolved = VoiceMessageMarkup.parse(saved)!!
        val request = VoiceMessageMarkup.withRequestAttachment(resolved, resolved.transcript)
        assertTrue(request.startsWith("$text\n"))
        assertTrue(request.contains("id=\"${source.attachment.filePath}\""))
        assertTrue(request.contains("filename=\"${source.attachment.fileName}\""))
        assertTrue(request.contains("type=\"audio/wav\""))
        assertFalse(request.contains("<voice_bar"))
        assertEquals(text, resolved.transcript)
        assertEquals(source.attachment, resolved.attachment)
    }

    @Test
    fun nativeAudioRequestRetainsAudioLinkAndTheSameEscapedFileReference() {
        val source = audio().copy(attachment = audio().attachment.copy(
            filePath = "/shared/voice & \"sample\".wav", fileName = "voice & \"sample\".wav",
        ))
        val link = "<link type=\"audio\" id=\"request-audio\">Audio</link>"
        val request = VoiceMessageMarkup.withRequestAttachment(source, link)
        assertTrue(request.startsWith("$link\n"))
        val attachment = request.substringAfter('\n')
        assertTrue(VoiceMessageMarkup.encode(source).contains(attachment))
        assertTrue(attachment.contains("voice &" + "amp; &" + "quot;sample&" + "quot;.wav"))
        assertEquals(source.attachment, VoiceMessageMarkup.parse(VoiceMessageMarkup.encode(source))!!.attachment)
    }

    @Test
    fun hookTextIsNotMarkedAsResolvedAndInvalidCacheIsRejected() {
        val hook = VoiceMessageMarkup.encode(audio("钩子补充"))
        assertFalse(VoiceMessageMarkup.parse(hook)!!.transcriptResolved)
        val empty = VoiceMessageMarkup.encode(audio())
        assertNull(VoiceMessageMarkup.parse(empty.replace(" input=\"audio\"", " input=\"audio\" resolved=\"true\"")))
        assertNull(VoiceMessageMarkup.parse(hook.replace(" input=\"audio\"", " input=\"audio\" resolved=\"unknown\"")))
    }

    @Test
    fun editedOrRemovedVoiceIsNotReplacedByStaleRecognition() {
        val source = audio()
        val edited = VoiceMessageMarkup.encode(source.copy(transcript = "后来编辑的内容"))
        assertEquals(edited, VoiceMessageMarkup.withResolvedTranscript(edited, source, "过期结果"))
        assertEquals("编辑成普通文本", VoiceMessageMarkup.withResolvedTranscript("编辑成普通文本", source, "过期结果"))
        assertEquals("", VoiceMessageMarkup.withResolvedTranscript("", source, "过期结果"))
    }

    @Test
    fun unknownAudioVersionOrInputIsRejected() {
        val valid = VoiceMessageMarkup.encode(audio())
        assertNull(VoiceMessageMarkup.parse(valid.replace("version=\"2\"", "version=\"3\"")))
        assertNull(VoiceMessageMarkup.parse(valid.replace("input=\"audio\"", "input=\"unknown\"")))
        assertNull(VoiceMessageMarkup.parse(valid.replace(" input=\"audio\"", "")))
        assertNull(VoiceMessageMarkup.parse(valid.replace("version=\"2\" input=\"audio\"", "version=\"1\"")))
    }

    @Test
    fun textOnlyViewsUseAvailableTextAndNeverIncludeAudioPathsOrWaveforms() {
        val encoded = VoiceMessageMarkup.encode(audio())
        assertEquals("[Voice message]", VoiceMessageMarkup.toModelText(encoded))
        assertEquals("钩子补充", VoiceMessageMarkup.toModelText(VoiceMessageMarkup.encode(audio("钩子补充"))))
    }

    @Test
    fun waveformEncodingIsStableAcrossRepeatedStorage() {
        val source = audio().copy(waveform = listOf(0.29f, 0.57f, 0.58f, 0.99f))
        val encoded = VoiceMessageMarkup.encode(source)
        assertEquals(encoded, VoiceMessageMarkup.encode(VoiceMessageMarkup.parse(encoded)!!))
        assertEquals(source.waveform, VoiceMessageMarkup.parse(encoded)!!.waveform)
    }
}