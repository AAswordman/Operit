package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.api.chat.keypool.SingleApiKeyProvider
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.files.OperitPaths
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.media.MediaPoolManager
import java.io.File
import java.nio.file.Files
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock

class ProviderAudioContentTest {
    private lateinit var poolRoot: File
    private lateinit var base64Mock: MockedStatic<Base64>
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true
    private val context = mock<Context>()

    @Before
    fun setUpMediaPool() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false

        // 只验证出站内容结构，固定字节不用于真实音频解码或语音识别。
        val bytes = byteArrayOf(1, 2, 3)
        base64Mock = mockStatic(Base64::class.java)
        base64Mock.`when`<ByteArray> { Base64.decode(MEDIA_BASE64, Base64.DEFAULT) }.thenReturn(bytes)
        base64Mock.`when`<String> { Base64.encodeToString(bytes, Base64.NO_WRAP) }.thenReturn(MEDIA_BASE64)

        poolRoot = Files.createTempDirectory("provider-audio-content-test").toFile()
        MediaPoolManager.initialize(poolRoot, preloadNow = false)
        val poolDirectory = OperitPaths.mediaPoolDir(poolRoot)
        for ((id, mime) in MEDIA_TYPES) {
            File(poolDirectory, "$id.b64").writeText(MEDIA_BASE64)
            File(poolDirectory, "$id.meta").writeText(mime)
        }
    }

    @After
    fun tearDownMediaPool() {
        for (id in MEDIA_TYPES.keys) MediaPoolManager.removeMedia(id)
        poolRoot.deleteRecursively()
        base64Mock.close()
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun siliconFlowWavUsesAudioUrlAndKeepsText() {
        val content = qwenProvider(ApiProviderType.SILICONFLOW)
            .buildContentField(context, "${link(WAV_ID)} spoken request") as JSONArray

        assertEquals(2, content.length())
        assertAudioUrl(content.getJSONObject(0), "audio/wav")
        assertEquals("text", content.getJSONObject(1).getString("type"))
        assertEquals("spoken request", content.getJSONObject(1).getString("text"))
    }

    @Test
    fun siliconFlowMpegPreservesMimeTypeAndBase64() {
        val content = qwenProvider(ApiProviderType.SILICONFLOW)
            .buildContentField(context, link(MPEG_ID)) as JSONArray

        assertAudioUrl(content.getJSONObject(0), "audio/mpeg")
    }

    @Test
    fun openAiStillUsesRawBase64InputAudio() {
        val provider = OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "audio-model",
            client = OkHttpClient(),
            supportsAudio = true,
        )
        val part = (provider.buildContentField(context, link(WAV_ID)) as JSONArray).getJSONObject(0)

        assertEquals("input_audio", part.getString("type"))
        assertEquals(MEDIA_BASE64, part.getJSONObject("input_audio").getString("data"))
        assertEquals("wav", part.getJSONObject("input_audio").getString("format"))
        assertFalse(part.has("audio_url"))
    }

    @Test
    fun aliyunStillUsesDataUrlInputAudio() {
        val content = qwenProvider(ApiProviderType.ALIYUN)
            .buildContentField(context, link(WAV_ID)) as JSONArray
        val part = content.getJSONObject(0)

        assertEquals("input_audio", part.getString("type"))
        assertEquals("data:audio/wav;base64,$MEDIA_BASE64", part.getJSONObject("input_audio").getString("data"))
        assertEquals("wav", part.getJSONObject("input_audio").getString("format"))
        assertFalse(part.has("audio_url"))
    }

    @Test
    fun siliconFlowVideoKeepsVideoUrlAlongsideAudio() {
        val content = qwenProvider(ApiProviderType.SILICONFLOW)
            .buildContentField(context, "${link(WAV_ID)} ${link(VIDEO_ID, "video")} describe") as JSONArray

        assertAudioUrl(content.getJSONObject(0), "audio/wav")
        assertEquals("video_url", content.getJSONObject(1).getString("type"))
        assertEquals("data:video/mp4;base64,$MEDIA_BASE64", content.getJSONObject(1).getJSONObject("video_url").getString("url"))
        assertEquals("describe", content.getJSONObject(2).getString("text"))
    }

    @Test
    fun siliconFlowDisabledAudioKeepsTextOnly() {
        val content = qwenProvider(ApiProviderType.SILICONFLOW, supportsAudio = false)
            .buildContentField(context, "${link(WAV_ID)} text only")

        assertEquals("text only", content)
    }

    @Test
    fun siliconFlowNonUserRolesDoNotAttachAudio() {
        val provider = qwenProvider(ApiProviderType.SILICONFLOW)
        for (role in listOf("assistant", "system", "tool")) {
            assertEquals("history text", provider.buildContentField(context, "${link(WAV_ID)} history text", role))
        }
    }

    @Test
    fun siliconFlowSummaryUsesAudioUrl() {
        val content = qwenProvider(ApiProviderType.SILICONFLOW)
            .buildContentField(context, link(WAV_ID), role = "summary") as JSONArray

        assertAudioUrl(content.getJSONObject(0), "audio/wav")
    }

    private fun assertAudioUrl(part: JSONObject, mimeType: String) {
        assertEquals(setOf("type", "audio_url"), part.keys().asSequence().toSet())
        assertEquals("audio_url", part.getString("type"))
        val payload = part.getJSONObject("audio_url")
        assertEquals(setOf("url"), payload.keys().asSequence().toSet())
        assertEquals("data:$mimeType;base64,$MEDIA_BASE64", payload.getString("url"))
    }

    private fun qwenProvider(type: ApiProviderType, supportsAudio: Boolean = true): QwenAIProvider =
        QwenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "Qwen/Qwen3-Omni-30B-A3B-Instruct",
            client = OkHttpClient(),
            qwenProviderType = type,
            supportsAudio = supportsAudio,
            supportsVideo = true,
        )

    private fun link(id: String, type: String = "audio"): String = "<link type=\"$type\" id=\"$id\"></link>"

    companion object {
        private const val MEDIA_BASE64 = "AQID"
        private const val WAV_ID = "provider-audio-wav"
        private const val MPEG_ID = "provider-audio-mpeg"
        private const val VIDEO_ID = "provider-audio-video"
        private val MEDIA_TYPES = mapOf(WAV_ID to "audio/wav", MPEG_ID to "audio/mpeg", VIDEO_ID to "video/mp4")
    }
}
