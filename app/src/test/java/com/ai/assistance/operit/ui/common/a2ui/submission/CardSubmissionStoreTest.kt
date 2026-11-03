package com.ai.assistance.operit.ui.common.a2ui.submission

import com.ai.assistance.operit.ui.common.a2ui.A2uiOwner
import com.ai.assistance.operit.ui.common.a2ui.CardExpress
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardSubmissionStoreTest {
    private data class Fixture(val store: CardSubmissionStore, val document: String, val surface: String, val owner: A2uiOwner)

    private fun document(surface: String): String =
        CardExpress.report("root=Text(text=\"等待\")").getValue("document").jsonPrimitive.content
            .lineSequence().filter(String::isNotBlank).joinToString("\n") { line ->
                val event = Json.parseToJsonElement(line).jsonObject
                val kind = event.keys.single { it != "version" }
                JsonObject(event + (kind to JsonObject(event.getValue(kind).jsonObject + ("surfaceId" to JsonPrimitive(surface))))).toString()
            }

    private fun fixture(timeoutMillis: Long = 180_000, clock: () -> Long = System::nanoTime): Fixture {
        val store = CardSubmissionStore(clock)
        val surface = "await_" + "a".repeat(32)
        val document = document(surface)
        store.register("run", "ref", "chat", document, timeoutMillis)
        return Fixture(store, document, surface, A2uiOwner("chat", 1, 0, ""))
    }

    private val answer = buildJsonObject { put("answer", "继续"); put("number", 3) }
    private fun Fixture.submit(data: JsonObject = answer) = store.submit(owner, document, surface, "ai_submit", data)

    @Test fun submissionBeforeAwaitIsPreservedAndConsumedOnce() = runBlocking {
        val f = fixture()
        assertTrue(f.submit())
        assertFalse(f.submit())
        val outcome = f.store.await("run", "ref")
        assertEquals(CardSubmissionStore.Status.Submitted, outcome.status)
        assertEquals(answer, outcome.data)
        assertEquals("ai_submit", outcome.action)
    }

    @Test fun waitingSuspendsUntilTheMatchingSubmission() = runBlocking {
        val f = fixture()
        val result = async(start = CoroutineStart.UNDISPATCHED) { f.store.await("run", "ref") }
        assertFalse(result.isCompleted)
        assertTrue(f.submit())
        assertEquals(answer, result.await().data)
    }

    @Test fun deadlineRejectsLateSubmissionWithoutInventingData() = runBlocking {
        var now = 0L
        val f = fixture(timeoutMillis = 10, clock = { now })
        now = 10_000_000
        assertFalse(f.submit())
        val result = f.store.await("run", "ref")
        assertEquals(CardSubmissionStore.Status.TimedOut, result.status)
        assertNull(result.data)
        assertNull(result.action)
    }

    @Test fun elapsedWaitReturnsTimeoutWithoutPolling() = runBlocking {
        val f = fixture(timeoutMillis = 1)
        assertEquals(CardSubmissionStore.Status.TimedOut, f.store.await("run", "ref").status)
        assertFalse(f.submit())
    }

    @Test fun acceptedSubmissionWinsIfAwaitStartsAfterTheDeadline() = runBlocking {
        var now = 0L
        val f = fixture(timeoutMillis = 10, clock = { now })
        now = 9_999_999
        assertTrue(f.submit())
        now = 10_000_001
        assertEquals(answer, f.store.await("run", "ref").data)
    }

    @Test fun chatDocumentMessageAndVariantCannotStealAWait() {
        val f = fixture()
        assertNotNull(f.store.observe(f.owner, f.document, f.surface))
        assertFalse(f.store.submit(f.owner.copy(chatId = "other"), f.document, f.surface, "ai_submit", answer))
        assertFalse(f.store.submit(f.owner.copy(timestamp = 2), f.document, f.surface, "ai_submit", answer))
        assertFalse(f.store.submit(f.owner.copy(variant = 1), f.document, f.surface, "ai_submit", answer))
        assertFalse(f.store.submit(f.owner, f.document + " ", f.surface, "ai_submit", answer))
        assertFalse(f.store.submit(f.owner, f.document, "await_" + "b".repeat(32), "ai_submit", answer))
        assertTrue(f.submit())
    }

    @Test fun userCancellationReturnsImmediatelyAndRejectsLaterSubmission() = runBlocking {
        val f = fixture()
        assertTrue(f.store.cancel(f.owner, f.document, f.surface))
        assertFalse(f.store.cancel(f.owner, f.document, f.surface))
        assertEquals(CardSubmissionStore.Status.Cancelled, f.store.await("run", "ref").status)
        assertFalse(f.submit())
    }

    @Test fun parentCancellationStopsTheWaitAndClosesItsSubmission() = runBlocking {
        val f = fixture()
        val state = f.store.observe(f.owner, f.document, f.surface)!!
        val job = launch(start = CoroutineStart.UNDISPATCHED) { f.store.await("run", "ref") }
        job.cancelAndJoin()
        assertEquals(CardSubmissionStore.Status.Cancelled, state.value)
        assertFalse(f.submit())
    }

    @Test fun endingOneRunDoesNotCancelAnotherRun() = runBlocking {
        val f = fixture()
        val otherSurface = "await_" + "b".repeat(32)
        val otherDocument = document(otherSurface)
        f.store.register("other-run", "other-ref", "other-chat", otherDocument, 180_000)
        val result = async(start = CoroutineStart.UNDISPATCHED) { f.store.await("run", "ref") }
        f.store.end("run")
        assertEquals(CardSubmissionStore.Status.Cancelled, result.await().status)
        assertNull(f.store.observe(f.owner, f.document, f.surface))
        assertTrue(f.store.submit(A2uiOwner("other-chat", 2, 0, ""), otherDocument, otherSurface, "ai_submit", answer))
        assertEquals(answer, f.store.await("other-run", "other-ref").data)
    }

    @Test fun invalidActionsAndOversizedPayloadsDoNotConsumeTheWait() {
        val f = fixture()
        assertFalse(f.store.submit(f.owner, f.document, f.surface, "local_action", answer))
        assertFalse(f.submit(buildJsonObject { put("answer", "x".repeat(33_000)) }))
        assertTrue(f.submit())
    }
}