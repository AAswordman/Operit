package com.ai.assistance.operit.core.chat.voice

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceInputFallbackTest {
    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("预期抛出异常")
    }

    @Test
    fun cachedTranscriptDoesNotReadConfigurationOrRecognizeAgain() = runBlocking {
        val result = resolveVoiceText(
            hasAudioModel = { throw AssertionError("已解析的文字不再读取音频配置") },
            audioModel = { throw AssertionError("已解析的文字不再调用音频理解") },
            stt = { throw AssertionError("已解析的文字不再调用 STT") },
            cachedTranscript = "轻声说的原始请求",
        )
        assertEquals(VoiceTextResult("轻声说的原始请求", VoiceTextSource.CACHED), result)
    }

    @Test
    fun configuredAudioModelWinsWithoutStt() = runBlocking {
        val calls = mutableListOf<String>()
        val result = resolveVoiceText(
            hasAudioModel = { true },
            audioModel = { calls.add("audio"); "  带有轻声语气的请求  " },
            stt = { calls.add("stt"); "转写" },
        )
        assertEquals(VoiceTextResult("带有轻声语气的请求", VoiceTextSource.AUDIO_MODEL), result)
        assertEquals(listOf("audio"), calls)
    }

    @Test
    fun unconfiguredAudioModelUsesStt() = runBlocking {
        val result = resolveVoiceText(
            hasAudioModel = { false },
            audioModel = { throw AssertionError("不应调用音频理解模型") },
            stt = { "  普通话转写  " },
        )
        assertEquals(VoiceTextResult("普通话转写", VoiceTextSource.STT), result)
    }

    @Test
    fun failedAudioModelUsesSttAndReportsItsError() = runBlocking {
        val audioError = IOException("音频理解不可用")
        var observed: Exception? = null
        val result = resolveVoiceText(
            hasAudioModel = { true },
            audioModel = { throw audioError },
            stt = { "转写成功" },
            onAudioModelFailure = { observed = it },
        )
        assertEquals(VoiceTextSource.STT, result.source)
        assertSame(audioError, observed)
    }

    @Test
    fun emptyAudioUnderstandingUsesStt() = runBlocking {
        val result = resolveVoiceText({ true }, { " \n " }, { "有效转写" })
        assertEquals(VoiceTextSource.STT, result.source)
        assertEquals("有效转写", result.text)
    }

    @Test
    fun unreadableAudioConfigurationUsesStt() = runBlocking {
        val result = resolveVoiceText(
            { throw IOException("配置读取失败") },
            { throw AssertionError("不应调用无效模型") },
            { "有效转写" },
        )
        assertEquals(VoiceTextSource.STT, result.source)
    }

    @Test
    fun audioModelCancellationDoesNotStartStt() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            resolveVoiceText({ true }, { throw cancelled }, { throw AssertionError("取消后不应转写") })
        }
        assertSame(cancelled, error)
    }

    @Test
    fun configurationCancellationDoesNotStartStt() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            resolveVoiceText({ throw cancelled }, { "音频" }, { throw AssertionError("取消后不应转写") })
        }
        assertSame(cancelled, error)
    }

    @Test
    fun sttCancellationIsPropagated() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure { resolveVoiceText({ false }, { "音频" }, { throw cancelled }) }
        assertSame(cancelled, error)
    }

    @Test
    fun bothFailuresKeepAudioErrorAsEvidence() = runBlocking {
        val audioError = IOException("音频错误")
        val sttError = IOException("识别错误")
        val error = failure { resolveVoiceText({ true }, { throw audioError }, { throw sttError }) }
        assertSame(sttError, error)
        assertEquals(listOf(audioError), error.suppressed.toList())
    }

    @Test
    fun emptySttIsAnError() = runBlocking {
        val error = failure { resolveVoiceText({ false }, { "音频" }, { "  " }) }
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun directAudioSuccessNeverStartsTextPath() = runBlocking {
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { emit, _ -> emit("回答"); emit("完成") },
            text = { _, _ -> throw AssertionError("直接音频成功后不应切换") },
            emit = { output.add(it) },
        )
        assertEquals(listOf("回答", "完成"), output)
    }

    @Test
    fun directAudioFailureBeforeContentStartsTextPath() = runBlocking {
        val calls = mutableListOf<String>()
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { _, _ -> calls.add("native"); throw IOException("不支持音频") },
            text = { emit, _ -> calls.add("text"); emit("文字回答") },
            emit = { output.add(it) },
        )
        assertEquals(listOf("native", "text"), calls)
        assertEquals(listOf("文字回答"), output)
    }

    @Test
    fun emptyDirectAudioCompletionStartsTextPath() = runBlocking {
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { emit, _ -> emit("") },
            text = { emit, _ -> emit("文字回答") },
            emit = { if (it.isNotEmpty()) output.add(it) },
        )
        assertEquals(listOf("文字回答"), output)
    }

    @Test
    fun partialAnswerOrToolContentIsNeverReplayed() = runBlocking {
        for (content in listOf("已经开始回答", "<tool name=\"terminal\">")) {
            val connectionError = IOException("响应中断")
            val output = mutableListOf<String>()
            val error = failure {
                collectVoiceResponse(
                    native = { emit, _ -> emit(content); throw connectionError },
                    text = { _, _ -> throw AssertionError("已有内容时不能重放") },
                    emit = { output.add(it) },
                )
            }
            assertSame(connectionError, error)
            assertEquals(listOf(content), output)
        }
    }

    @Test
    fun directAudioCancellationDoesNotStartTextPath() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            collectVoiceResponse(
                native = { _, _ -> throw cancelled },
                text = { _, _ -> throw AssertionError("取消后不能重试") },
                emit = {},
            )
        }
        assertSame(cancelled, error)
    }

    @Test
    fun emptyTextResponseFailsWithOriginalDirectAudioError() = runBlocking {
        val audioError = IOException("直接音频不可用")
        val error = failure {
            collectVoiceResponse(native = { _, _ -> throw audioError }, text = { _, _ -> }, emit = {})
        }
        assertTrue(error is IllegalStateException)
        assertEquals(listOf(audioError), error.suppressed.toList())
    }

    @Test
    fun textResponseCancellationIsNotConvertedToRequestFailure() = runBlocking {
        val cancelled = CancellationException("用户取消")
        val error = failure {
            collectVoiceResponse(
                native = { _, _ -> throw IOException("直接音频不可用") },
                text = { _, _ -> throw cancelled },
                emit = {},
            )
        }
        assertSame(cancelled, error)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test
    fun threePathsFollowUserSpecifiedOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { _, _ -> calls.add("chat-audio"); throw IOException("直接音频失败") },
            text = { emit, _ ->
                val resolved = resolveVoiceText(
                    hasAudioModel = { true },
                    audioModel = { calls.add("audio-model"); throw IOException("音频理解失败") },
                    stt = { calls.add("stt"); "转写内容" },
                )
                calls.add("chat-text")
                emit(resolved.text)
            },
            emit = { output.add(it) },
        )
        assertEquals(listOf("chat-audio", "audio-model", "stt", "chat-text"), calls)
        assertEquals(listOf("转写内容"), output)
    }
    @Test
    fun recoveredVoiceTextDoesNotPublishRejectedAudioRetryNotice() = runBlocking {
        val notices = mutableListOf<String>()
        val output = mutableListOf<String>()
        collectVoiceResponse(
            native = { _, notice ->
                notice("503 input_audio")
                throw IOException("unsupported content type at index 0: input_audio")
            },
            text = { emit, _ -> emit("转写后的回答") },
            emit = { output.add(it) },
            onNonFatalError = { notices.add(it) },
        )
        assertEquals(listOf("转写后的回答"), output)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun acceptedNativeAnswerPublishesItsNoticesBeforeContent() = runBlocking {
        val events = mutableListOf<String>()
        collectVoiceResponse(
            native = { emit, notice ->
                notice("第一次重试")
                emit("回答")
                notice("回答后的重试")
            },
            text = { _, _ -> throw AssertionError("音频响应成功时不应再转写") },
            emit = { events.add(it) },
            onNonFatalError = { events.add(it) },
        )
        assertEquals(listOf("第一次重试", "回答", "回答后的重试"), events)
    }

    @Test
    fun textRequestRetriesStillPublishTheirOwnNotice() = runBlocking {
        val notices = mutableListOf<String>()
        collectVoiceResponse(
            native = { _, notice -> notice("音频重试"); throw IOException("音频拒绝") },
            text = { emit, notice -> notice("文字重试"); emit("成功") },
            emit = {},
            onNonFatalError = { notices.add(it) },
        )
        assertEquals(listOf("文字重试"), notices)
    }

    @Test
    fun finalFailureKeepsBothAttemptsWithoutPublishingIntermediateNotice() = runBlocking {
        val audioError = IOException("音频拒绝")
        val textError = IOException("识别请求失败")
        val notices = mutableListOf<String>()
        val error = failure {
            collectVoiceResponse(
                native = { _, notice -> notice("音频重试"); throw audioError },
                text = { _, _ -> throw textError },
                emit = {},
                onNonFatalError = { notices.add(it) },
            )
        }
        assertSame(textError, error)
        assertEquals(listOf(audioError), error.suppressed.toList())
        assertTrue(notices.isEmpty())
    }
}
