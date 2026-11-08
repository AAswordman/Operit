package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.api.chat.error.RequestFailure
import com.ai.assistance.operit.api.chat.error.RequestFailureDomain
import com.ai.assistance.operit.api.chat.error.RequestFailureTrace
import com.ai.assistance.operit.api.chat.keypool.SingleApiKeyProvider
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import com.ai.assistance.operit.util.stream.TextStreamEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.SocketException
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.stubbing.Answer

class OpenAIStreamCompletionTest {
    private var previousSystemLogging = true
    private var previousFileLogging = true

    @Before
    fun disableAndroidLogging() {
        previousSystemLogging = AppLogger.enableSystemLog
        previousFileLogging = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogging
        AppLogger.enableFileLogging = previousFileLogging
    }

    private fun provider(responses: Boolean = false) = object : OpenAIProvider(
        apiEndpoint = "https://example.test/v1/chat/completions",
        apiKeyProvider = SingleApiKeyProvider("test-key"),
        modelName = "probe", client = OkHttpClient(),
    ) {
        override val useResponsesApi = responses
    }

    private fun text(content: String) = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("delta", JSONObject().put("content", content)),
    ))

    private fun finish(reason: Any = "stop") = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("delta", JSONObject()).put("finish_reason", reason),
    ))

    private fun usage() = JSONObject().put("choices", JSONArray()).put("usage", JSONObject()
        .put("prompt_tokens", 10).put("completion_tokens", 42).put("total_tokens", 52))

    private fun responsesText(content: String) = JSONObject()
        .put("type", "response.output_text.delta").put("output_index", 0).put("delta", content)

    private fun responsesFinish(incomplete: Boolean = false) = JSONObject()
        .put("type", if (incomplete) "response.incomplete" else "response.completed")
        .put("response", JSONObject().put("output", JSONArray())
            .put("status", if (incomplete) "incomplete" else "completed")
            .put("incomplete_details", JSONObject().put("reason", "max_output_tokens"))
            .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 42)))

    // 直接经过真实供应商解析入口，连接尾部由可控 Reader 提供，不访问网络。
    private suspend fun render(
        reader: BufferedReader,
        provider: OpenAIProvider = provider(),
        output: StringBuilder = StringBuilder(),
        trace: RequestFailureTrace = RequestFailureTrace("stream-test"),
        onTokens: suspend (Long, Long, Long) -> Unit = { _, _, _ -> },
    ): String {
        val context = Mockito.mock(Context::class.java, Answer { call ->
            if (call.method.name == "getString") "probe error" else Answers.RETURNS_DEFAULTS.answer(call)
        })
        val emit: suspend (String) -> Unit = { output.append(it); Unit }
        val emitterType = OpenAIProvider::class.java.declaredClasses.single { it.simpleName == "StreamEmitter" }
        val constructor = emitterType.declaredConstructors.single { it.parameterCount == 6 }.apply { isAccessible = true }
        val emitter = constructor.newInstance(provider, StringBuilder(), emit, MutableSharedStreamImpl<TextStreamEvent>(), onTokens, {})
        emitterType.getDeclaredField("failureTrace").apply { isAccessible = true }.set(emitter, trace)
        val method = OpenAIProvider::class.java.declaredMethods.single { it.name == "processStreamingResponse" }.apply { isAccessible = true }
        invokeSuspending(method, provider, reader, emitter, onTokens, context, null, 1)
        return output.toString()
    }

    private suspend fun invokeSuspending(method: Method, target: Any, vararg args: Any?) {
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            try { method.invoke(target, *args, continuation) }
            catch (failure: InvocationTargetException) { throw failure.targetException }
        }
    }

    private fun reader(vararg events: JSONObject, tail: () -> Nothing): BufferedReader {
        val lines = events.map { "data: $it" }.iterator()
        return object : BufferedReader(StringReader("")) {
            override fun readLine(): String? = if (lines.hasNext()) lines.next() else tail()
        }
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable {
        try { block() } catch (failure: Throwable) { return failure }
        throw AssertionError("Expected an explicit failure")
    }

    @Test
    fun longCompletedReplySurvivesSocketAbortWhileReadingTail() = runBlocking {
        val content = "示例🙂".repeat(10_000)
        var tailReads = 0
        val trace = RequestFailureTrace("completed")
        val output = render(reader(text(content), finish()) {
            tailReads++
            throw SocketException("Software caused connection abort")
        }, trace = trace)
        assertEquals(content, output)
        assertEquals(1, tailReads)
        assertEquals(true, trace.protocolCompleted)
        assertEquals(content.length.toLong(), trace.outputCharsParsed)
        assertEquals(2L, trace.streamEventsParsed)
    }

    @Test
    fun unfinishedReplyPreservesTheExact5003AndPartialContent() = runBlocking {
        val interrupted = SocketException("Software caused connection abort")
        val output = StringBuilder()
        val trace = RequestFailureTrace("unfinished")
        val actual = failure { render(reader(text("partial")) { throw interrupted }, output = output, trace = trace) }
        assertSame(interrupted, actual)
        assertEquals(5003, RequestFailure.describe(actual)!!.reason!!.code)
        assertEquals("partial", output.toString())
        assertEquals(false, trace.protocolCompleted)
        assertEquals(7L, trace.outputCharsParsed)
    }

    @Test
    fun nullFinishReasonDoesNotConvertInterruptionIntoCompletion() = runBlocking {
        val interrupted = SocketException("Software caused connection abort")
        val trace = RequestFailureTrace("null-finish")
        val actual = failure { render(reader(text("partial"), finish(JSONObject.NULL)) { throw interrupted }, trace = trace) }
        assertSame(interrupted, actual)
        assertEquals(false, trace.protocolCompleted)
    }

    @Test
    fun chatFinishKeepsLateUsageWithoutReadingBeyondIt() = runBlocking {
        var outputTokens = 0L
        val output = render(reader(text("answer"), finish(), usage()) {
            throw AssertionError("Already received the final usage")
        }, onTokens = { _, _, value -> outputTokens = value })
        assertEquals("answer", output)
        assertEquals(42L, outputTokens)
    }

    @Test
    fun responsesCompletionDoesNotWaitForDoneOrSocketClose() = runBlocking {
        var outputTokens = 0L
        val trace = RequestFailureTrace("responses-complete")
        val output = render(reader(responsesText("answer"), responsesFinish()) {
            throw AssertionError("Responses ended with its final event")
        }, provider = provider(responses = true), trace = trace,
            onTokens = { _, _, value -> outputTokens = value })
        assertEquals("answer", output)
        assertEquals(42L, outputTokens)
        assertEquals(true, trace.protocolCompleted)
    }

    @Test
    fun responsesOutputLimitKeepsContentAndUsageAtItsFinalEvent() = runBlocking {
        val trace = RequestFailureTrace("responses-limit")
        var outputTokens = 0L
        val output = render(reader(responsesText("limited"), responsesFinish(incomplete = true)) {
            throw AssertionError("The incomplete event is terminal")
        }, provider = provider(responses = true), trace = trace,
            onTokens = { _, _, value -> outputTokens = value })
        assertEquals("limited", output)
        assertEquals(42L, outputTokens)
        assertEquals(true, trace.protocolCompleted)
    }

    @Test
    fun serverErrorAfterFinishIsNotSwallowedAsATransportTail() = runBlocking {
        val error = JSONObject().put("error", JSONObject().put("message", "upstream failed").put("code", "upstream_error"))
        val actual = failure { render(reader(text("answer"), finish(), error) {
            throw AssertionError("The provider error must propagate")
        }) }
        assertEquals(RequestFailureDomain.PROVIDER, RequestFailure.describe(actual)!!.domain)
    }

    @Test
    fun manualCancellationAfterFinishRemainsCancellation() = runBlocking {
        val provider = provider()
        val actual = failure { render(reader(text("answer"), finish()) {
            provider.cancelStreaming()
            throw SocketException("Software caused connection abort")
        }, provider = provider) }
        assertTrue(actual.toString(), actual is UserCancellationException)
    }

    @Test
    fun usageCallbackFailureAfterFinishStillPropagates() = runBlocking {
        val storageFailure = IOException("usage callback failed")
        val actual = failure { render(reader(text("answer"), finish(), usage()) {
            throw AssertionError("The callback failure must propagate")
        }, onTokens = { _, _, output -> if (output == 42L) throw storageFailure }) }
        assertSame(storageFailure, actual)
    }

    @Test
    fun doneMarkerStillCompletesAReplyWithoutFinishReason() = runBlocking {
        val sse = "data: ${text("answer")}\n\ndata: [DONE]\n\n"
        val trace = RequestFailureTrace("done")
        assertEquals("answer", render(BufferedReader(StringReader(sse)), trace = trace))
        assertEquals(true, trace.protocolCompleted)
    }
}
