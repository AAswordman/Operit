package com.ai.assistance.operit.api.chat.llmprovider

import java.io.File
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTranscriptionProtocolTest {
    @Test
    fun siliconFlowTranscriptionEndpoint_isDetected() {
        assertTrue(
            AudioTranscriptionProtocol.isTranscriptionEndpoint(
                "https://api.siliconflow.cn/v1/audio/transcriptions"
            )
        )
    }

    @Test
    fun transcriptionEndpoint_detectionIgnoresCaseQueryAndTrailingSlash() {
        assertTrue(
            AudioTranscriptionProtocol.isTranscriptionEndpoint(
                "https://api.example.com/v1/Audio/Transcriptions/?api-version=1"
            )
        )
    }

    @Test
    fun chatCompletionsEndpoint_isNotTranscriptionEndpoint() {
        assertFalse(
            AudioTranscriptionProtocol.isTranscriptionEndpoint(
                "https://api.siliconflow.cn/v1/chat/completions"
            )
        )
    }

    @Test
    fun baseUrl_isNotTranscriptionEndpoint() {
        assertFalse(AudioTranscriptionProtocol.isTranscriptionEndpoint("https://api.siliconflow.cn/v1"))
    }

    @Test
    fun siliconFlowHosts_areRecognized() {
        assertTrue(
            AudioTranscriptionProtocol.isSiliconFlowEndpoint(
                "https://api.siliconflow.cn/v1/audio/transcriptions"
            )
        )
        assertTrue(
            AudioTranscriptionProtocol.isSiliconFlowEndpoint(
                "https://api.siliconflow.com/v1/audio/transcriptions"
            )
        )
        assertFalse(
            AudioTranscriptionProtocol.isSiliconFlowEndpoint(
                "https://api.openai.com/v1/audio/transcriptions"
            )
        )
    }

    @Test
    fun requestBody_isMultipartAndContainsOnlyFileAndModel() {
        val file = createTempAudioFile("sample.mp3")
        try {
            val body = AudioTranscriptionProtocol.buildRequestBody(file, "FunAudioLLM/SenseVoiceSmall")

            assertTrue(
                body.contentType()?.toString()?.startsWith("multipart/form-data") == true
            )
            val raw = bodyAsText(body)
            assertEquals(listOf("file", "model"), multipartFieldNames(raw))
            assertTrue(raw.contains("filename=\"sample.mp3\""))
            assertTrue(raw.contains("Content-Type: audio/mpeg"))
            assertTrue(raw.contains("FunAudioLLM/SenseVoiceSmall"))
            assertFalse(raw.contains("response_format"))
            assertFalse(raw.contains("language"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun requestBody_includesOptionalFieldsWhenProvided() {
        val file = createTempAudioFile("sample.wav")
        try {
            val body =
                AudioTranscriptionProtocol.buildRequestBody(
                    file = file,
                    model = "whisper-1",
                    language = "zh",
                    responseFormat = "json",
                )
            assertEquals(
                listOf("file", "model", "language", "response_format"),
                multipartFieldNames(bodyAsText(body)),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseResponseBody_readsTextField() {
        assertEquals(
            "你好，世界",
            AudioTranscriptionProtocol.parseResponseBody("""{"text":"你好，世界"}""")
        )
    }

    @Test
    fun parseResponseBody_returnsPlainTextForNonJsonBody() {
        assertEquals("plain transcript", AudioTranscriptionProtocol.parseResponseBody("plain transcript"))
    }

    @Test
    fun extractErrorMessage_readsSiliconFlowShape() {
        assertEquals(
            "Token is invalid.",
            AudioTranscriptionProtocol.extractErrorMessage(
                """{"code":30014,"data":null,"message":"Token is invalid."}"""
            )
        )
    }

    @Test
    fun extractErrorMessage_readsOpenAiShape() {
        assertEquals(
            "Invalid file format",
            AudioTranscriptionProtocol.extractErrorMessage(
                """{"error":{"message":"Invalid file format","type":"invalid_request_error"}}"""
            )
        )
    }

    @Test
    fun extractErrorMessage_returnsNullForEmptyBody() {
        assertNull(AudioTranscriptionProtocol.extractErrorMessage("  "))
    }

    private fun createTempAudioFile(name: String): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "audio-transcription-test")
        directory.mkdirs()
        val file = File(directory, name)
        file.writeBytes(byteArrayOf(0x49, 0x44, 0x33))
        return file
    }

    private fun bodyAsText(body: RequestBody): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun multipartFieldNames(rawBody: String): List<String> =
        Regex("""Content-Disposition: form-data; name="([^"]+)"""")
            .findAll(rawBody)
            .map { it.groupValues[1] }
            .toList()
}
