package com.ai.assistance.operit.core.chat.voice

import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import org.junit.Assert.*
import org.junit.Test

class VoiceMessageMarkupTest {
    private fun message(transcript: String = "帮我安排明天的任务") = VoiceMessageData(
        AttachmentInfo("/data/user/0/example/files/media_pool/voice/voice-1.wav", "voice-1.wav", "audio/wav", 64044),
        2000L,
        listOf(0f, 0.25f, 0.5f, 1f),
        transcript,
    )

    @Test
    fun chineseAndXmlCharactersRoundTrip() {
        val source = message("中文 \"引号\" '单引号' <voice_bar> \u0026 >\n第二行")
        assertEquals(source, VoiceMessageMarkup.parse(VoiceMessageMarkup.encode(source)))
    }

    @Test
    fun entityLookingTextIsDecodedOnlyOnce() {
        val source = message("保留实体文字：\u0026lt; \u0026amp; 与符号 \u0026 < >")
        assertEquals(source.transcript, VoiceMessageMarkup.toModelText(VoiceMessageMarkup.encode(source)))
    }

    @Test
    fun standardAttachmentScannerCanReadRecordingReference() {
        val source = message()
        val attachment = ChatMarkupRegex.attachmentDataSelfClosingTag.find(VoiceMessageMarkup.encode(source))!!
        assertEquals(source.attachment.filePath, attachment.groupValues[1])
        assertEquals("audio/wav", attachment.groupValues[3])
        assertEquals("64044", attachment.groupValues[4])
    }

    @Test
    fun modelReceivesTranscriptAndKeepsWorkspaceContext() {
        val source = message()
        val workspace = "<workspace_attachment>工作区变动</workspace_attachment>"
        val saved = "${VoiceMessageMarkup.encode(source)} $workspace"
        assertEquals("${source.transcript} $workspace", VoiceMessageMarkup.toModelText(saved))
        assertEquals(source, VoiceMessageMarkup.parseMessage(saved))
        assertNull(VoiceMessageMarkup.parse(saved))
    }

    @Test
    fun knownMessageMetadataDoesNotHideVoiceBar() {
        val source = message()
        val saved = "<proxy_sender name=\"助手\"/> ${VoiceMessageMarkup.encode(source)} " +
            "<reply_to sender=\"AI\" timestamp=\"1\">引用文字</reply_to>"
        assertEquals(source, VoiceMessageMarkup.parseMessage(saved))
    }

    @Test
    fun extraTextOrAnotherVoiceIsNotAStandaloneVoiceMessage() {
        val encoded = VoiceMessageMarkup.encode(message())
        assertNull(VoiceMessageMarkup.parseMessage("额外文字 $encoded"))
        assertNull(VoiceMessageMarkup.parse("$encoded $encoded"))
        assertNull(VoiceMessageMarkup.parseMessage("$encoded $encoded"))
    }

    @Test
    fun invalidVersionDurationAndWaveformsAreRejected() {
        val valid = VoiceMessageMarkup.encode(message())
        val invalid = listOf(
            valid.replace("version=\"1\"", "version=\"2\""),
            valid.replace("duration_ms=\"2000\"", "duration_ms=\"0\""),
            valid.replace("0.0,0.25,0.5,1.0", "NaN"),
            valid.replace("0.0,0.25,0.5,1.0", "Infinity"),
            valid.replace("0.0,0.25,0.5,1.0", "1.01"),
            valid.replace("0.0,0.25,0.5,1.0", List(65) { "0.5" }.joinToString(",")),
            valid.replace("size=\"64044\"", "size=\"43\""),
            valid.replace("audio/wav", "image/png"),
        )
        invalid.forEach { assertNull(it, VoiceMessageMarkup.parse(it)) }
    }

    @Test
    fun malformedVoiceAndOrdinaryTextArePreservedForRequest() {
        listOf("普通消息", "<voice_bar>损坏数据</voice_bar>", "<attachment id=\"plain\"/>").forEach {
            assertEquals(it, VoiceMessageMarkup.toModelText(it))
        }
    }

    @Test
    fun unrecognizedBodyContentCannotBeSilentlyDropped() {
        val valid = VoiceMessageMarkup.encode(message())
        assertNull(VoiceMessageMarkup.parse(valid.replace("</voice_bar>", "额外内容</voice_bar>")))
        assertNull(VoiceMessageMarkup.parse(valid.replace("</voice_bar>", "<transcript>另一段</transcript></voice_bar>")))
    }

    @Test
    fun multipleHistoricalBarsBecomeTheirOwnTranscripts() {
        val first = message("第一段")
        val second = message("第二段")
        assertEquals("第一段\n第二段", VoiceMessageMarkup.toModelText(
            "${VoiceMessageMarkup.encode(first)}\n${VoiceMessageMarkup.encode(second)}"))
    }
}
