package com.ai.assistance.operit.ui.common.a2ui

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CardLiveStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = """
        ${'$'}/agent = {progress:10}
        ${'$'}/form = {note:""}
        root = Column(children=[Progress(number=${'$'}/agent/progress),TextField(label="Note",value=${'$'}/form/note)])
    """.trimIndent()

    @Test fun versionOwnershipAndValidationAreAtomic() {
        val run = "live-test"
        CardDelivery.begin(run)
        try {
            val ref = CardDelivery.canonical(run, CardDelivery.register(run, CardExpress.compile(source), source, "chat", File("unused")))!!
            val data = buildJsonObject { put("progress", 20) }
            assertEquals(1, CardLiveStore.patch(run, ref, 0, data))
            assertTrue(runCatching { CardLiveStore.patch(run, ref, 0, data) }.isFailure)
            assertTrue(runCatching { CardLiveStore.patch("other", ref, 1, data) }.isFailure)
            assertTrue(runCatching { CardLiveStore.patch(run, ref, 1, buildJsonObject { put("progress", "bad") }) }.isFailure)
            assertEquals(2, CardLiveStore.patch(run, ref, 1, data))
        } finally { CardDelivery.end(run) }
    }

    @Test fun userControlsCannotBindAgentData() {
        CardDelivery.begin("protected")
        try {
            val bad = source.replace("value=$/form/note", "value=$/agent/note")
                .replace("{progress:10}", """{progress:10,note:""}""")
            assertTrue(runCatching { CardDelivery.register("protected", CardExpress.compile(bad), bad, "chat", File("unused")) }.isFailure)
        } finally { CardDelivery.end("protected") }
        assertTrue(CardLiveStore.protectedPath("/agent/x"))
        assertTrue(CardLiveStore.protectedPath("/"))
        assertFalse(CardLiveStore.protectedPath("/form"))
        assertFalse(CardLiveStore.userData(Json.parseToJsonElement("""{"agent":{},"form":{}}""").jsonObject).containsKey("agent"))
    }

    @Test fun failedPublicationNeverExposesAStaleCard() {
        val run = "failed-publication"
        // A regular file cannot be a state directory, regardless of available disk space.
        val directory = temporary.newFile("blocked-directory")
        CardDelivery.begin(run)
        try {
            val ref = CardDelivery.register(run, CardExpress.compile(source), source, "chat", directory)
            assertNull(CardDelivery.resolve(run, ref))
            assertNull(CardDelivery.resolve(run, ref))
            assertTrue(runCatching {
                CardLiveStore.patch(run, CardDelivery.canonical(run, ref)!!, 0, buildJsonObject { put("progress", 20) })
            }.isFailure)
            val expander = CardReferenceExpander({ CardDelivery.resolve(run, it) })
            assertEquals("```a2ui-ref\n$ref\n```\n", expander.push("```a2ui-ref\n$ref\n```\n"))
        } finally { CardDelivery.end(run) }
    }
}
