package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.TextStreamEvent
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import java.io.BufferedReader
import java.io.StringReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import com.ai.assistance.operit.api.chat.keypool.SingleApiKeyProvider

class OpenAIToolArgumentStreamingTest {
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    // 真实流解析会写日志；纯 JVM 关闭 Android 日志调用，结束后恢复以免影响其他测试。
    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    private fun provider(responses: Boolean = false) = object : OpenAIProvider(
        apiEndpoint = "https://example.test/v1/chat/completions",
        apiKeyProvider = SingleApiKeyProvider("test-key"),
        modelName = "probe", client = OkHttpClient(), enableToolCall = true,
    ) {
        override val useResponsesApi: Boolean = responses
    }

    private fun delta(index: Int, name: String? = null, args: String? = null) = JSONObject().apply {
        put("index", index)
        put("function", JSONObject().apply {
            name?.let { put("name", it) }
            args?.let { put("arguments", it) }
        })
    }

    private fun chat(vararg calls: JSONObject) = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("delta", JSONObject().put("tool_calls", JSONArray(calls.toList())))
    ))

    private fun finish() = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("delta", JSONObject()).put("finish_reason", "tool_calls")
    ))

    // 直接经过供应商的真实 SSE 解析与 JSON/XML 转换，不访问网络或执行工具。
    private suspend fun render(events: List<JSONObject>, responses: Boolean = false): String {
        val provider = provider(responses)
        val output = StringBuilder()
        val emit: suspend (String) -> Unit = { output.append(it); Unit }
        val tokens: suspend (Long, Long, Long) -> Unit = { _, _, _ -> }
        val emitterType = OpenAIProvider::class.java.declaredClasses.single { it.simpleName == "StreamEmitter" }
        val constructor = emitterType.declaredConstructors.single { it.parameterCount == 6 }.apply { isAccessible = true }
        val emitter = constructor.newInstance(provider, StringBuilder(), emit, MutableSharedStreamImpl<TextStreamEvent>(), tokens, {})
        val method = OpenAIProvider::class.java.declaredMethods.single { it.name == "processStreamingResponse" }.apply { isAccessible = true }
        val sse = events.joinToString("\n\n") { "data: $it" } + "\n\ndata: [DONE]\n\n"
        invokeSuspending(method, provider, BufferedReader(StringReader(sse)), emitter, tokens, mock<Context>(), null, 1)
        return output.toString()
    }

    private suspend fun invokeSuspending(method: Method, target: Any, vararg args: Any?) {
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            try { method.invoke(target, *args, continuation) }
            catch (error: InvocationTargetException) { throw error.targetException }
        }
    }

    private fun assertCalls(xml: String, count: Int) {
        val calls = Regex("<(tool(?:_[A-Za-z0-9]+)?) name=\"task_(\\d+)\">([\\s\\S]*?)</\\1>").findAll(xml).toList()
        assertEquals(xml, count, calls.size)
        assertEquals((0 until count).toList(), calls.map { it.groupValues[2].toInt() })
        calls.forEachIndexed { index, match ->
            val body = match.groupValues[3]
            assertTrue(xml, body.contains("<param name=\"value\">$index</param>"))
            assertFalse(xml, body.contains("name=\"task_"))
        }
    }

    @Test fun eightInterleavedCallsKeepEveryParameterInsideItsOwnTag() = runBlocking {
        val args = (0..7).map { "{\"value\":$it}" }
        val events = mutableListOf(chat(*(0..7).map { delta(it, "task_$it") }.toTypedArray()))
        for (position in args[0].indices) events += chat(*(0..7).map { delta(it, args = args[it][position].toString()) }.toTypedArray())
        events += finish()
        assertCalls(render(events), 8)
    }

    @Test fun argumentsMayArriveBeforeNames() = runBlocking {
        assertCalls(render(listOf(chat(delta(0, args = "{\"value\":0}"), delta(1, args = "{\"value\":1}")), chat(delta(0, "task_0"), delta(1, "task_1")), finish())), 2)
    }

    @Test fun singleCallStillStreamsAnUnchangedArgument() = runBlocking {
        assertCalls(render(listOf(chat(delta(0, "task_0", "{\"val")), chat(delta(0, args = "ue\":0}")), finish())), 1)
    }

    @Test fun noArgumentToolsCanStillUseAnEmptyObject() = runBlocking {
        val xml = render(listOf(chat(delta(0, "a2ui_catalog", "{}")), finish()))
        assertTrue(xml, xml.contains("name=\"a2ui_catalog\""))
        assertFalse(xml, xml.contains("<param"))
    }

    @Test fun anIncompleteFirstCallCannotContainTheNextCall() = runBlocking {
        val xml = render(listOf(chat(delta(0, "task_0", "{\"value\":\"unfinished"), delta(1, "task_1", "{\"value\":1}")), finish()))
        assertTrue(xml, xml.contains("name=\"task_0\""))
        assertFalse(xml, xml.contains("name=\"task_1\""))
    }

    @Test fun responsesFinalSnapshotsAreKeptWithoutAnyDelta() = runBlocking {
        val events = mutableListOf<JSONObject>()
        for (index in 0..7) events += JSONObject().put("type", "response.output_item.added").put("output_index", index).put("item", JSONObject().put("type", "function_call").put("name", "task_$index").put("call_id", "call_$index"))
        for (index in 7 downTo 0) events += JSONObject().put("type", "response.function_call_arguments.done").put("output_index", index).put("arguments", "{\"value\":$index}")
        events += JSONObject().put("type", "response.completed").put("response", JSONObject().put("output", JSONArray()))
        assertCalls(render(events, responses = true), 8)
    }

    @Test fun responsesSnapshotOnlyAppendsItsMissingSuffix() = runBlocking {
        val events = listOf(
            JSONObject().put("type", "response.output_item.added").put("output_index", 0).put("item", JSONObject().put("type", "function_call").put("name", "task_0")),
            JSONObject().put("type", "response.function_call_arguments.delta").put("output_index", 0).put("delta", "{\"value\":"),
            JSONObject().put("type", "response.function_call_arguments.done").put("output_index", 0).put("arguments", "{\"value\":0}"),
            JSONObject().put("type", "response.completed").put("response", JSONObject().put("output", JSONArray())),
        )
        assertCalls(render(events, responses = true), 1)
    }

    @Test fun nonStreamingCorruptArgumentsAreNotConvertedToAnEmptyObject() {
        val provider = provider()
        val method = OpenAIProvider::class.java.declaredMethods.single { it.name == "convertToolCallsToXml" }.apply { isAccessible = true }
        val calls = JSONArray().put(JSONObject().put("function", JSONObject().put("name", "task_0").put("arguments", "{\"value\":\"unfinished")))
        val xml = method.invoke(provider, calls, false) as String
        assertTrue(xml, xml.contains("_raw_arguments"))
        assertTrue(xml, xml.contains("unfinished"))
    }

    @Test fun genuinelyEmptyUpstreamArgumentsDoNotInventAnyParameters() = runBlocking {
        val xml = render(listOf(chat(delta(0, "task_0", "")), finish()))
        assertTrue(xml, xml.contains("name=\"task_0\""))
        assertFalse(xml, xml.contains("<param"))
    }
}
