package com.ai.assistance.operit.core.tools.builtin.a2ui

import com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.ToolRuntimeContext
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolParameter
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.ui.common.a2ui.A2uiOwner
import com.ai.assistance.operit.ui.common.a2ui.A2uiProtocol
import com.ai.assistance.operit.ui.common.a2ui.CardDelivery
import com.ai.assistance.operit.ui.common.a2ui.CardLiveStore
import com.ai.assistance.operit.ui.common.a2ui.submission.CardSubmissionStore
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class A2uiAwaitToolsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = """
        ${'$'}/form={answer:""}
        root=Form(label="确认方案",children=[TextField(label="回答",value=${'$'}/form/answer),Button(child=Text(text="提交"),action=Event(name="ai_submit",context={form:${'$'}/form,required:["/form/answer"]}))])
    """.trimIndent()
    private val answer = buildJsonObject { put("answer", "继续") }
    private val owner = A2uiOwner("chat", 7, 0, "")

    private suspend fun withRun(action: suspend (ToolRuntimeContext) -> Unit) {
        val run = "await-" + UUID.randomUUID()
        CardDelivery.begin(run)
        try { action(ToolRuntimeContext(callerChatId = owner.chatId, a2uiRunId = run)) }
        finally { CardDelivery.end(run) }
    }

    private fun tool(timeout: String = "180", live: Boolean = false): AITool = AITool("a2ui_prepare", listOf(
        ToolParameter("source", if (live) "${'$'}/agent={progress:0}\n$source" else source),
        ToolParameter("wait_timeout_seconds", timeout),
        ToolParameter("live", live.toString()),
    ))
    private fun payload(result: ToolResult) = Json.parseToJsonElement((result.result as StringResultData).value).jsonObject

    @Test fun formIsPublishedBeforeTheToolReturnsAndEarlySubmissionReachesTheSameResult() = runBlocking {
        withRun { context ->
            var published = ""
            val runtime = context.copy(publishA2ui = { document ->
                published = document
                val surface = A2uiProtocol.parse(document, true).surfaceIds.single()
                assertTrue(CardSubmissionStore.isAwaitable(surface))
                assertTrue(CardSubmissionStore.current.submit(owner, document, surface, "ai_submit", answer))
            })
            val result = A2uiTools.executeStreaming(tool(), temporary.root, runtime)
            assertTrue(result.success)
            val report = payload(result)
            assertEquals("submitted", report.getValue("status").jsonPrimitive.content)
            assertEquals(answer, report["data"])
            assertEquals("card-1", report.getValue("reference").jsonPrimitive.content)
            assertTrue(published.isNotEmpty())
            assertEquals("", CardDelivery.unplaced(context.a2uiRunId!!, "```a2ui\n$published\n```"))
        }
    }

    @Test fun liveAndWaitingCardsKeepVersionChecksAndAgentOwnership() = runBlocking {
        withRun { context ->
            val runtime = context.copy(publishA2ui = { document ->
                val surface = A2uiProtocol.parse(document, true).surfaceIds.single()
                assertTrue(CardLiveStore.isLive(surface))
                assertTrue(CardSubmissionStore.isAwaitable(surface))
                val patched = A2uiToolRuntime.patchData(context, "card-1", 0, buildJsonObject { put("progress", 50) })
                assertEquals(1, patched.getValue("version").jsonPrimitive.int)
                assertTrue(CardSubmissionStore.current.submit(owner, document, surface, "ai_submit", answer))
            })
            assertEquals("submitted", payload(A2uiTools.executeStreaming(tool(live = true), temporary.root, runtime)).getValue("status").jsonPrimitive.content)
        }
    }

    @Test fun missingStreamingPublisherDoesNotConsumeACardReference() = runBlocking {
        withRun { context ->
            assertFalse(A2uiTools.executeStreaming(tool(), temporary.root, context).success)
            val normal = A2uiTools.execute(tool(timeout = "0"), temporary.root, context)
            assertEquals("card-1", payload(normal).getValue("reference").jsonPrimitive.content)
        }
    }

    @Test fun invalidTimeoutsAndSynchronousWaitingAreRejectedBeforeRegistration() = runBlocking {
        withRun { context ->
            val runtime = context.copy(publishA2ui = {})
            listOf("-1", "601", "1.5").forEach { timeout ->
                assertFalse(A2uiTools.executeStreaming(tool(timeout), temporary.root, runtime).success)
            }
            assertFalse(A2uiTools.execute(tool(), temporary.root, runtime).success)
            assertEquals("card-1", payload(A2uiTools.execute(tool("0"), temporary.root, runtime)).getValue("reference").jsonPrimitive.content)
        }
    }

    @Test fun publicationFailureClosesTheWaitingCapability() = runBlocking {
        withRun { context ->
            var surface = ""
            var document = ""
            val runtime = context.copy(publishA2ui = { value ->
                document = value
                surface = A2uiProtocol.parse(value, true).surfaceIds.single()
                throw IllegalStateException("Publication failed")
            })
            assertFalse(A2uiTools.executeStreaming(tool(), temporary.root, runtime).success)
            assertEquals(CardSubmissionStore.Status.Cancelled, CardSubmissionStore.current.observe(owner, document, surface)!!.value)
            assertFalse(CardSubmissionStore.current.submit(owner, document, surface, "ai_submit", answer))
        }
    }

    @Test fun stoppingTheToolCancelsThePendingForm() = runBlocking {
        withRun { context ->
            var surface = ""
            var document = ""
            val runtime = context.copy(publishA2ui = { value ->
                document = value
                surface = A2uiProtocol.parse(value, true).surfaceIds.single()
            })
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                A2uiTools.executeStreaming(tool(), temporary.root, runtime)
            }
            assertTrue(document.isNotEmpty())
            assertFalse(job.isCompleted)
            job.cancelAndJoin()
            assertEquals(CardSubmissionStore.Status.Cancelled, CardSubmissionStore.current.observe(owner, document, surface)!!.value)
            assertFalse(CardSubmissionStore.current.submit(owner, document, surface, "ai_submit", answer))
        }
    }

    @Test fun toolTimeoutReportsAStatusWithoutInventingAnAnswer() = runBlocking {
        withRun { context ->
            val runtime = context.copy(publishA2ui = {})
            val result = A2uiTools.executeStreaming(tool("1"), temporary.root, runtime)
            assertTrue(result.success)
            val report = payload(result)
            assertEquals("timed_out", report.getValue("status").jsonPrimitive.content)
            assertFalse(report.containsKey("data"))
        }
    }

    @Test fun defaultDeliveryDoesNotPublishOrWaitDuringTheToolCall() = runBlocking {
        withRun { context ->
            var publications = 0
            val runtime = context.copy(publishA2ui = { publications++ })
            val result = A2uiTools.executeStreaming(tool("0"), temporary.root, runtime)
            assertTrue(result.success)
            assertEquals(0, publications)
            assertFalse(payload(result).containsKey("status"))
        }
    }
}