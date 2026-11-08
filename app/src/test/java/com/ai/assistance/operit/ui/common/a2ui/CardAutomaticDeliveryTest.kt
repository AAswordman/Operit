package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.ui.common.markdown.extractFencedCodeBlockContent
import com.ai.assistance.operit.util.markdown.MarkdownFence
import java.util.UUID
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardAutomaticDeliveryTest {
    private val first = CardExpress.compile("root=Text(text=\"First\")")
    private val second = CardExpress.compile("root=Text(text=\"Second\")")

    private inline fun withRun(block: (String) -> Unit) {
        val run = UUID.randomUUID().toString()
        CardDelivery.begin(run)
        try { block(run) } finally { CardDelivery.end(run) }
    }

    private fun decode(source: String, resolve: (String) -> String?, size: Int = 1): String {
        val decoder = CardReferenceExpander(resolve)
        return source.chunked(size).joinToString("") { decoder.push(it) } + decoder.finish()
    }

    @Test fun supportedFenceStylesHaveTheSameResultAtEveryChunkBoundary() {
        for (delimiter in listOf("```", "````", "~~~", "~~~~")) {
            for (indent in 0..3) {
                val input = " ".repeat(indent) + delimiter + "a2ui-ref \r\ncard-1\r\n" + " ".repeat(indent) + delimiter + "  \r\n"
                for (size in 1..input.length) {
                    assertEquals(input, "```a2ui\n$first\n```\n", decode(input, { if (it == "card-1") first else null }, size))
                }
            }
        }
    }

    @Test fun exampleFencesAndHiddenSectionsNeverExpand() {
        val inner = "~~~a2ui-ref\ncard-1\n~~~"
        for (input in listOf("````text\n$inner\n````", "<think>\n$inner\n</think>", "<tool_results>\n$inner\n</tool_results>", "    ```a2ui-ref\ncard-1\n    ```")) {
            assertEquals(input, decode(input, { first }))
        }
    }

    @Test fun unknownOrIncompleteReferencesKeepTheirOriginalFence() {
        for (input in listOf("  ~~~~a2ui-ref\ncard-9\n  ~~~~", "~~~a2ui-ref\ncard-1", "````a2ui-ref\ncard-1\n```")) {
            assertEquals(input, decode(input, { null }))
        }
    }

    @Test fun rollbackRestoresAnIndentedTildeReference() {
        val decoder = CardReferenceExpander { if (it == "card-1") first else null }
        assertEquals("", decoder.push("  ~~~~a2ui-ref\ncard-"))
        decoder.savepoint("before-id")
        decoder.push("2\n~~~~\n")
        decoder.rollback("before-id")
        assertEquals("```a2ui\n$first\n```", decoder.push("1\n~~~~") + decoder.finish())
    }

    @Test fun aCompiledCardIsDeliveredWhenTheModelOnlyWritesProse() = withRun { run ->
        CardDelivery.register(run, first)
        val suffix = CardDelivery.unplaced(run, "The chart is ready.")
        val block = MarkdownFence.blocks(suffix).single()
        assertEquals(first, block.body(suffix))
        assertTrue(A2uiProtocol.parse(block.body(suffix), true).complete)
        assertEquals("", CardDelivery.unplaced(run, "The chart is ready." + suffix))
    }

    @Test fun anExplicitPositionDoesNotProduceAnotherAutomaticCard() = withRun { run ->
        val ref = CardDelivery.register(run, first)
        val displayed = decode("~~~a2ui-ref\n$ref\n~~~", { CardDelivery.resolve(run, it) })
        assertEquals("", CardDelivery.unplaced(run, displayed))
    }

    @Test fun resolvingAReferenceDoesNotConsumeADeliveryRemovedByRollback() = withRun { run ->
        val ref = CardDelivery.register(run, first)
        assertEquals(first, CardDelivery.resolve(run, ref))
        assertTrue(CardDelivery.unplaced(run, "replacement after rollback").contains(first))
    }

    @Test fun onlyTheLatestUnplacedRevisionIsAutomaticallyDelivered() = withRun { run ->
        val ref = CardDelivery.register(run, first)
        CardDelivery.register(run, second, supersedesReference = ref)
        val suffix = CardDelivery.unplaced(run, "")
        assertFalse(suffix.contains(first))
        assertTrue(suffix.contains(second))
    }

    @Test fun aDisplayedOldRevisionRemainsAlongsideItsNewRevision() = withRun { run ->
        val ref = CardDelivery.register(run, first)
        val displayed = "```a2ui\n${CardDelivery.resolve(run, ref)}\n```"
        CardDelivery.register(run, second, supersedesReference = ref)
        val suffix = CardDelivery.unplaced(run, displayed)
        assertEquals(2, MarkdownFence.blocks(displayed + suffix).count { it.fence.language == "a2ui" })
        assertEquals("", CardDelivery.unplaced(run, displayed + suffix))
    }

    @Test fun independentCardsAreDeliveredOnceInRegistrationOrder() = withRun { run ->
        CardDelivery.register(run, first)
        CardDelivery.register(run, second)
        CardDelivery.register(run, first)
        val suffix = CardDelivery.unplaced(run, "")
        assertEquals(listOf(first, second), MarkdownFence.blocks(suffix).map { it.body(suffix) })
    }

    @Test fun hiddenCardExamplesAreNotMistakenForDeliveredCards() = withRun { run ->
        CardDelivery.register(run, first)
        assertTrue(CardDelivery.unplaced(run, "<think>\n```a2ui\n$first\n```\n</think>").contains(first))
    }

    @Test fun anUnclosedExampleCannotSwallowTheAutomaticCard() = withRun { run ->
        CardDelivery.register(run, first)
        val text = "````text\nunfinished example"
        val result = text + CardDelivery.unplaced(run, text)
        val card = MarkdownFence.blocks(result).last()
        assertEquals("a2ui", card.fence.language)
        assertEquals(first, card.body(result))
    }

    @Test fun referencesAndAutomaticDeliveryCannotCrossEndedTurns() = withRun { run ->
        CardDelivery.register(run, first)
        assertEquals("", CardDelivery.unplaced("another-run", ""))
        CardDelivery.end(run)
        assertEquals("", CardDelivery.unplaced(run, ""))
    }

    @Test fun historySummariesAreExplicitAndDoNotLookLikeLinks() {
        val source = "   ~~~~a2ui\n$first\n   ~~~~"
        val history = CardTranscript.forModel(source)
        assertTrue(history, history.startsWith("Historical A2UI summary"))
        assertTrue(history, history.contains("context only"))
        assertFalse(history, history.startsWith("["))
        assertTrue(history, history.contains("First"))
        val parsed = extractFencedCodeBlockContent(source)
        assertEquals("a2ui", parsed.language)
        assertEquals(first, parsed.code)
    }

    @Test fun expressRejectsMisspelledSeriesNamesButOldDocumentsStillRender() {
        val good = "root=Chart(label=\"Chart\",type=\"bar\",labels=[\"A\"],series=[{label:\"Revenue\",values:[12]}])"
        val invalid = CardExpress.report(good.replace("label:\"Revenue\"", "name:\"Revenue\""))
        assertFalse(invalid.toString(), invalid.getValue("valid").jsonPrimitive.boolean)
        assertTrue(invalid.toString(), invalid.getValue("message").jsonPrimitive.content.contains("label"))
        val legacy = CardExpress.compile(good).replace("\"label\":\"Revenue\"", "\"name\":\"Revenue\"")
        assertTrue(A2uiProtocol.parse(legacy, true).complete)
    }

    @Test fun slidersAcceptPracticalAmountsWithoutRemovingTheEndpointLimits() {
        val source = "$/amount=1200\nroot=Slider(label=\"Amount\",min=1000,max=3000,number=$/amount,steps=19)"
        assertTrue(A2uiProtocol.parse(CardExpress.compile(source), true).complete)
        assertFalse(CardExpress.report(source.replace("max=3000", "max=1000001")).getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report(source.replace("steps=19", "steps=1000")).getValue("valid").jsonPrimitive.boolean)
    }
    @Test fun argumentErrorsIncludeTheActualRequiredSignature() {
        val report = CardExpress.report("root=Text(value=\"wrong\")")
        val message = report.getValue("message").jsonPrimitive.content
        assertFalse(report.getValue("valid").jsonPrimitive.boolean)
        assertTrue(message, message.contains("text:string"))
        assertTrue(message, message.contains("Unknown: value"))
        assertTrue(message, message.contains("missing: text"))
    }

}