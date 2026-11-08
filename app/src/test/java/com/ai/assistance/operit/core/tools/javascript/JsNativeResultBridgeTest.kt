package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.ADBResultData
import com.ai.assistance.operit.core.tools.SandboxScriptExecutionResultData
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class JsNativeResultBridgeTest {
    @Test
    fun `structured shell result preserves legacy fields and discriminator`() {
        val result = ADBResultData("printf 中文", "引号\"、反斜线\\、换行\n和零字符\u0000", 3)
        val encoded = JsToolResultJson.encodeStructured(result)

        assertEquals(Json.parseToJsonElement(result.toJson()), encoded)
        assertTrue(encoded.jsonObject.containsKey("__type"))
        assertEquals(result.output, encoded.jsonObject.getValue("output").jsonPrimitive.content)
    }

    @Test
    fun `large shell output is reused directly by the json tree`() {
        val output = "音频结果\\\"\n".repeat(150_000) + "完整结尾"
        val result = ADBResultData("shell", output, 0)
        val encoded = JsToolResultJson.encodeStructured(result)

        // 编码树应引用现有正文，不能通过字符串编码和重新解析再造一份正文。
        assertSame(output, encoded.jsonObject.getValue("output").jsonPrimitive.content)
        assertEquals(output.length, encoded.jsonObject.getValue("output").jsonPrimitive.content.length)
    }

    @Test
    fun `sandbox results retain their concrete json shape`() {
        val result = SandboxScriptExecutionResultData(
            success = true,
            scriptPath = "/tmp/example.js",
            functionName = "main",
            startedAtMs = 1,
            finishedAtMs = 2,
            durationMs = 1,
            result = JsonPrimitive("完成")
        )
        val encoded = JsToolResultJson.encodeStructured(result)

        assertEquals(
            Json.parseToJsonElement(Json.encodeToString(SandboxScriptExecutionResultData.serializer(), result)),
            encoded
        )
        assertFalse(encoded.jsonObject.containsKey("__type"))
    }

    @Test
    fun `tool callback arguments preserve objects arrays and json strings`() {
        listOf(" {\"output\":\"完整\\n正文\"} ", "[1,true,null]", "\"JSON 字符串\"").forEach { result ->
            val args = Json.parseToJsonElement(buildToolResultCallbackArgs("callback", result, false)).jsonArray
            assertEquals(JsonPrimitive("callback"), args[0])
            assertEquals(Json.parseToJsonElement(result), args[1])
            assertEquals(JsonPrimitive(false), args[2])
        }
    }

    @Test
    fun `plain errors and scalar text remain strings with exact escaping`() {
        val callbackId = "callback'];globalThis.injected=true;//"
        listOf("", " \n ", "true", "42", "null", "错误\"\\\n\r\t\u0000\u2028\u2029").forEach { result ->
            val args = Json.parseToJsonElement(buildToolResultCallbackArgs(callbackId, result, true)).jsonArray
            assertEquals(callbackId, args[0].jsonPrimitive.content)
            assertEquals(result, args[1].jsonPrimitive.content)
            assertTrue(args[1].jsonPrimitive.isString)
            assertEquals(JsonPrimitive(true), args[2])
        }
    }

    @Test
    fun `ipc keeps json envelopes as strings and large tool payloads stay complete`() {
        val output = "语音\\\"\n".repeat(150_000) + "最后一个字符"
        val result = buildJsonObject { put("output", JsonPrimitive(output)) }.toString()
        val tool = Json.parseToJsonElement(buildToolResultCallbackArgs("tool", result, false)).jsonArray
        val ipc = Json.parseToJsonElement(buildStringResultCallbackArgs(" ipc ", result, true)).jsonArray

        assertEquals(output, tool[1].jsonObject.getValue("output").jsonPrimitive.content)
        assertEquals("ipc", ipc[0].jsonPrimitive.content)
        assertEquals(result, ipc[1].jsonPrimitive.content)
        assertTrue(ipc[1].jsonPrimitive.isString)
    }

    @Test
    fun `fixed runtime delivers tool and ipc callbacks through their original namespaces`() {
        val directory = Files.createTempDirectory("operit-native-result-").toFile()
        val runtime = File(directory, "runtime.js")
        val suite = File(directory, "regression.cjs")
        val fixtures = File(directory, "fixtures.json")
        val output = File(directory, "result.log")
        try {
            runtime.writeText(buildNativeResultCallbackRuntime())
            val toolResult = "{\"success\":true,\"data\":{\"output\":\"中文\\n\\\"\\\\\"}}"
            val ipcResult = "{\"success\":false,\"message\":\"完整错误\"}"
            fixtures.writeText(buildJsonObject {
                put("toolFunction", JsonPrimitive(TOOL_RESULT_CALLBACK_FUNCTION))
                put("ipcFunction", JsonPrimitive(STRING_RESULT_CALLBACK_FUNCTION))
                put("toolArgs", Json.parseToJsonElement(buildToolResultCallbackArgs("tool'];globalThis.injected=true;//", toolResult, false)))
                put("ipcArgs", Json.parseToJsonElement(buildStringResultCallbackArgs("ipc", ipcResult, true)))
            }.toString())
            suite.writeText("""
                'use strict';
                const assert = require('node:assert/strict');
                const fs = require('node:fs');
                const vm = require('node:vm');
                const fixture = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
                const errors = [];
                const context = vm.createContext({ window: {}, console: { error: value => errors.push(value) } });
                vm.runInContext('function __operitExpose(name, value) { globalThis[name] = value; }', context);
                vm.runInContext(fs.readFileSync(process.argv[2], 'utf8'), context);
                let tools = 0;
                let ipcs = 0;
                context.window[fixture.toolArgs[0]] = function(result, isError) {
                    assert.equal(this, context.window);
                    assert.deepEqual(result, fixture.toolArgs[1]);
                    assert.equal(isError, false);
                    tools++;
                };
                context[fixture.ipcArgs[0]] = function(result, isError) {
                    assert.equal(this, undefined);
                    assert.equal(typeof result, 'string');
                    assert.equal(result, fixture.ipcArgs[1]);
                    assert.equal(isError, true);
                    ipcs++;
                };
                context[fixture.toolFunction](...fixture.toolArgs);
                context[fixture.toolFunction](...fixture.toolArgs);
                context[fixture.ipcFunction](...fixture.ipcArgs);
                assert.equal(tools, 2);
                assert.equal(ipcs, 1);
                assert.equal(context.injected, undefined);
                context[fixture.toolFunction]('missing', {}, false);
                assert.equal(errors.length, 1);
            """.trimIndent())
            // CI 使用现有 Node，运行实际生成的回调脚本与参数，不调用网络或 Android UI。
            val process = ProcessBuilder("node", suite.absolutePath, runtime.absolutePath, fixtures.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            try {
                assertTrue("原生结果回调回归超时", process.waitFor(45, TimeUnit.SECONDS))
                assertEquals(output.readText(), 0, process.exitValue())
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
