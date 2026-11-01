package com.ai.assistance.operit.core.tools.builtin.a2ui

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.ToolRuntimeContext
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.common.a2ui.CardDelivery
import com.ai.assistance.operit.ui.common.a2ui.A2uiProtocol
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class A2uiToolsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = "root=Text(text=\"Native card\")"
    private val liveSource = """
        ${'$'}/agent={progress:10}
        root=Card(child=Column(children=[Display(label="progress",text=concat(values=[${'$'}/agent/progress])),Progress(label="实时进度",number=divide(a=${'$'}/agent/progress,b=100))]))
        """.trimIndent()

    private fun withRun(action: (ToolRuntimeContext) -> Unit) {
        val run = "native-" + UUID.randomUUID()
        CardDelivery.begin(run)
        try {
            action(ToolRuntimeContext(callerChatId = "chat", a2uiRunId = run))
        } finally {
            CardDelivery.end(run)
        }
    }

    private fun call(name: String, context: ToolRuntimeContext?, vararg parameters: Pair<String, String>): ToolResult =
        A2uiTools.execute(
            AITool(name, parameters.map { ToolParameter(it.first, it.second) }),
            temporary.root,
            context,
        )

    private fun payload(result: ToolResult): JsonObject =
        Json.parseToJsonElement((result.result as StringResultData).value).jsonObject

    @Test fun nativeGenerationDoesNotRequireGuideOrJsEngine() = withRun { context ->
        val result = call("a2ui_prepare", context, "source" to source)
        assertTrue(result.success)
        val reference = payload(result).getValue("reference").jsonPrimitive.content
        val document = CardDelivery.resolve(context.a2uiRunId!!, reference)
        assertNotNull(document)
        assertTrue(A2uiProtocol.parse(document!!, true).complete)
    }

    @Test fun generationRequiresTrustedRuntimeContext() {
        val result = call("a2ui_prepare", null, "source" to source)
        assertFalse(result.success)
        assertEquals("inactive_turn", payload(result).getValue("code").jsonPrimitive.content)
        withRun { context ->
            val forged = call("a2ui_prepare", context, "source" to source, "callerChatId" to "other")
            assertFalse(forged.success)
            assertEquals("invalid_arguments", payload(forged).getValue("code").jsonPrimitive.content)
        }
    }

    @Test fun completedTurnsCannotCreateCards() = withRun { context ->
        CardDelivery.end(context.a2uiRunId!!)
        val result = call("a2ui_prepare", context, "source" to source)
        assertFalse(result.success)
        assertEquals("inactive_turn", payload(result).getValue("code").jsonPrimitive.content)
    }

    @Test fun invalidSourceDoesNotConsumeAReference() = withRun { context ->
        val invalid = call("a2ui_prepare", context, "source" to "root=UnknownComponent()")
        assertFalse(invalid.success)
        val valid = call("a2ui_prepare", context, "source" to source)
        assertTrue(valid.success)
        assertEquals("card-1", payload(valid).getValue("reference").jsonPrimitive.content)
    }

    @Test fun revisionsPreserveOldCardsAndCannotUseAnotherTurn() = withRun { context ->
        val reference = payload(call("a2ui_prepare", context, "source" to source)).getValue("reference").jsonPrimitive.content
        val original = CardDelivery.resolve(context.a2uiRunId!!, reference)
        withRun { other ->
            val invalid = call("a2ui_revise", other, "reference" to reference, "changes" to "root=Text(text=\"Other\")")
            assertFalse(invalid.success)
            assertEquals("unknown_reference", payload(invalid).getValue("code").jsonPrimitive.content)
        }
        val revised = call("a2ui_revise", context, "reference" to reference, "changes" to "root=Text(text=\"Revised\")")
        assertTrue(revised.success)
        val next = payload(revised).getValue("reference").jsonPrimitive.content
        assertNotEquals(reference, next)
        assertEquals(original, CardDelivery.resolve(context.a2uiRunId!!, reference))
        assertTrue(CardDelivery.resolve(context.a2uiRunId!!, next)!!.contains("Revised"))
    }

    @Test fun invalidNativeParameterShapesAreRejected() = withRun { context ->
        val cases = listOf(
            "a2ui_prepare" to listOf("source" to source, "live" to "yes"),
            "a2ui_prepare" to listOf("source" to source, "source" to source),
            "a2ui_revise" to listOf("reference" to "card-1", "changes" to source, "remove" to "[1]"),
            "a2ui_patch_data" to listOf("reference" to "card-1", "expected_version" to "0", "data" to "[]"),
            "a2ui_icons" to listOf("limit" to "0"),
        )
        cases.forEach { (name, parameters) ->
            val result = call(name, context, *parameters.toTypedArray())
            assertFalse(name, result.success)
            assertEquals(name, "invalid_arguments", payload(result).getValue("code").jsonPrimitive.content)
        }
    }

    @Test fun liveInputErrorsDoNotInvalidateTheActiveTurn() = withRun { context ->
        val invalidSources = listOf(
            """
            ${'$'}/progress=10
            root=Card(child=Progress(number=${'$'}/progress,label="代理数据 progress"))
            """.trimIndent(),
            """
            ${'$'}/agent=10
            root=Progress(number=${'$'}/agent)
            """.trimIndent(),
        )
        invalidSources.forEach { invalidSource ->
            val invalid = call("a2ui_prepare", context, "source" to invalidSource, "live" to "true")
            assertFalse(invalid.success)
            assertEquals("invalid_card", payload(invalid).getValue("code").jsonPrimitive.content)
            assertTrue(payload(invalid).getValue("message").jsonPrimitive.content.contains("$/agent"))
        }
        val corrected = call("a2ui_prepare", context, "source" to liveSource, "live" to "true")
        assertTrue(corrected.success)
        assertEquals("card-1", payload(corrected).getValue("reference").jsonPrimitive.content)
        assertEquals(0, payload(corrected).getValue("version").jsonPrimitive.int)
    }

    @Test fun nativeLiveUpdatesEnforceExpectedVersion() = withRun { context ->
        val prepared = call("a2ui_prepare", context, "source" to liveSource, "live" to "true")
        assertTrue(prepared.success)
        val reference = payload(prepared).getValue("reference").jsonPrimitive.content
        // 发布前的 live 更新同样执行版本校验，普通 JVM 没有 Android AtomicFile 的实现。
        assertEquals(0, payload(prepared).getValue("version").jsonPrimitive.int)
        assertNotNull(CardDelivery.source(context.a2uiRunId!!, reference))
        fun patch(version: String) = call(
            "a2ui_patch_data", context,
            "reference" to reference,
            "expected_version" to version,
            "data" to "{\"progress\":20}",
        )
        val first = patch("0")
        assertTrue(first.success)
        assertEquals(1, payload(first).getValue("version").jsonPrimitive.int)
        assertFalse(patch("0").success)
        val next = patch("1")
        assertTrue(next.success)
        assertEquals(2, payload(next).getValue("version").jsonPrimitive.int)
    }
}