package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.processor.A2uiJsonMessageParser
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions for natural Catalog syntax, including the actual parser and event actor. */
class CardCatalogExpressionsTest {
    @Test
    fun inlineTableCellsPreserveValuesAndReactInOfficialEngine() = runBlocking {
        withExample { surface, graph, events ->
            val table = graph.getValue("table")
            fun rows(): List<*> = surface.evaluatePayload(
                "table", { surface.dataModel[it] }, A2uiDataPath("/"),
                A2uiProtocol.toValue(table.getValue("rows")),
            ) as List<*>

            assertEquals(listOf("GPU", "40", false), rows()[0])
            assertEquals(40.0, ((rows()[1] as List<*>)[1] as Number).toDouble(), 0.0)

            surface.dataModel.update(A2uiDataPath("/plan/gpu"), 65)
            surface.dataModel.update(A2uiDataPath("/plan/cuda"), 20)
            surface.dataModel.update(A2uiDataPath("/plan/done"), true)

            assertEquals(listOf("GPU", "65", true), rows()[0])
            assertEquals(25.0, ((rows()[1] as List<*>)[1] as Number).toDouble(), 0.0)
            A2uiProtocol.validateExpanded(graph, model(surface))
            assertTrue(events.tryReceive().isFailure)
        }
    }

    @Test
    fun structuredLocalReplacementUsesLatestStateWhenConsumed() = runBlocking {
        withExample { surface, graph, events ->
            surface.dataModel.update(A2uiDataPath("/plan/title"), "Edited")
            surface.dataModel.update(A2uiDataPath("/plan/gpu"), 60)
            surface.dataModel.update(A2uiDataPath("/plan/done"), true)
            dispatch(surface, graph, "reset")
            val event = withTimeout(5_000) { events.receive() }
            assertEquals("local_action", event.type)
            val clickValue = event.context.getValue("value") as Map<*, *>
            assertEquals(65.0, ((clickValue["details"] as Map<*, *>)["nextMinutes"] as Number).toDouble(), 0.0)

            // Local transitions consume current values even when the actor's click
            // snapshot was queued before another input changed the same data.
            surface.dataModel.update(A2uiDataPath("/plan/gpu"), 70)
            val context = resolveLocalCardContext(surface, "reset", graph.getValue("reset"), event.context)
            val replacement = context.getValue("value") as Map<*, *>
            assertEquals(75.0, ((replacement["details"] as Map<*, *>)["nextMinutes"] as Number).toDouble(), 0.0)
            assertEquals(listOf(mapOf("title" to "了解 GPU", "done" to false)), replacement["tasks"])
            surface.dataModel.update(A2uiDataPath(context.getValue("path") as String), replacement)

            assertEquals("GPU 入门", surface.dataModel[A2uiDataPath("/plan/title")])
            assertEquals(false, surface.dataModel[A2uiDataPath("/plan/done")])
            assertEquals(40.0, (surface.dataModel[A2uiDataPath("/plan/gpu")] as Number).toDouble(), 0.0)
            A2uiProtocol.validateExpanded(graph, model(surface))
            assertTrue(events.tryReceive().isFailure)
        }
    }

    @Test
    fun structuredSubmissionRetainsTheConfirmedClickSnapshot() = runBlocking {
        withExample { surface, graph, events ->
            surface.dataModel.update(A2uiDataPath("/plan/title"), "My plan")
            surface.dataModel.update(A2uiDataPath("/plan/gpu"), 60)
            surface.dataModel.update(A2uiDataPath("/plan/done"), true)
            dispatch(surface, graph, "submit")
            val event = withTimeout(5_000) { events.receive() }
            assertEquals("ai_submit", event.type)
            val submitted = event.context.getValue("plan") as Map<*, *>
            assertEquals("My plan", submitted["title"])
            val minutes = submitted["minutes"] as Map<*, *>
            assertEquals(95.0, (minutes["total"] as Number).toDouble(), 0.0)
            assertEquals(listOf(mapOf("title" to "了解 GPU", "done" to true)), submitted["tasks"])

            surface.dataModel.update(A2uiDataPath("/plan/title"), "Changed after click")
            surface.dataModel.update(A2uiDataPath("/plan/gpu"), 80)
            val context = resolveLocalCardContext(surface, "submit", graph.getValue("submit"), event.context)
            assertSame(event.context, context)
            assertEquals("My plan", (context.getValue("plan") as Map<*, *>)["title"])
            assertEquals(95.0, (minutes["total"] as Number).toDouble(), 0.0)
            assertTrue(events.tryReceive().isFailure)
        }
    }

    @Test
    fun tableRowsCanMixWholeRowBindingsAndInlineCellCalculations() {
        val source = """
            ${'$'}/first = ["GPU",40]
            ${'$'}/minutes = 35
            root = Table(columns=["Topic","Minutes"],rows=[${'$'}/first,["CUDA",add(a=${'$'}/minutes,b=5)]])
        """.trimIndent()
        val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
        val graph = batch.componentsBySurface.getValue("card")
        val rows = CardRepeat.evaluate(graph.getValue("root").getValue("rows"), CardRepeat.initialData(batch.lines, "card")) as JsonArray
        assertEquals(2, rows.size)
        assertEquals(JsonPrimitive("GPU"), (rows[0] as JsonArray)[0])
        assertEquals(JsonPrimitive(40), (rows[0] as JsonArray)[1])
    }

    @Test
    fun repeatedTablesAcceptInlineItemCalculations() {
        val source = """
            ${'$'}/topics = [{id:"GPU",minutes:40},{id:"CUDA",minutes:35}]
            root = Repeat(items=${'$'}/topics,key="id",child=Table(columns=["Topic","Minutes"],rows=[[item(field="id"),formatNumber(value=item(field="minutes"),decimals=0)]]))
        """.trimIndent()
        val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
        val graph = batch.componentsBySurface.getValue("card")
        val data = CardRowState.prepare(graph, CardRepeat.initialData(batch.lines, "card"), false)
        val tables = CardRepeat.expand(graph, data).values.filter { it["component"] == JsonPrimitive("Table") }
        assertEquals(2, tables.size)
        assertEquals(listOf("40", "35"), tables.map {
            val rows = CardRepeat.evaluate(it.getValue("rows"), data) as JsonArray
            ((rows[0] as JsonArray)[1] as JsonPrimitive).content
        })
    }

    @Test
    fun evaluatedCellsStayScalarAndRowDimensionsRemainBounded() {
        val invalid = listOf(
            "${'$'}/cell = {title:\"GPU\"}\nroot = Table(columns=[\"x\"],rows=[[${'$'}/cell]])",
            "${'$'}/cell = [1,2]\nroot = Table(columns=[\"x\"],rows=[[${'$'}/cell]])",
            "root = Table(columns=[\"x\"],rows=[[1,2]])",
            "${'$'}/row = [1,2]\nroot = Table(columns=[\"x\"],rows=[${'$'}/row])",
            "root = Table(columns=[\"x\"],rows=[" + List(33) { "[1]" }.joinToString(",") + "])",
        )
        invalid.forEach { source ->
            val report = CardExpress.report(source)
            assertEquals(report.toString(), JsonPrimitive(false), report["valid"])
        }
        val maximum = "root = Table(columns=[\"x\"],rows=[" + List(32) { "[1]" }.joinToString(",") + "])"
        assertTrue(A2uiProtocol.parse(CardExpress.compile(maximum), true).complete)
    }

    @Test
    fun changingABoundCellToAnObjectIsRejectedBeforeReRendering() {
        val batch = A2uiProtocol.parse(CardExpress.compile(
            "${'$'}/cell = \"ok\"\nroot = Table(columns=[\"x\"],rows=[[${'$'}/cell]])"
        ), true)
        val graph = batch.componentsBySurface.getValue("card")
        val changed = CardRepeat.put(CardRepeat.initialData(batch.lines, "card"), "/cell", JsonObject(mapOf("title" to JsonPrimitive("GPU"))))
        val error = assertThrows(IllegalArgumentException::class.java) {
            A2uiProtocol.validateExpanded(graph, changed)
        }
        assertEquals("Invalid table rows", error.message)
    }

    @Test
    fun structuredContextsKeepTheEventAndFunctionRestrictions() {
        val invalid = listOf(
            "root = Button(child=Text(text=\"Run\"),action=Event(name=\"shell\",context={plan:{title:\"GPU\"}}))",
            "root = Button(child=Text(text=\"Run\"),action=Event(name=\"ai_submit\",context={plan:{value:eval(text=\"no\")}}))",
            "root = Button(child=Text(text=\"Run\"),action=Event(name=\"ai_submit\",context=[1,2]))",
        )
        invalid.forEach { source ->
            val report = CardExpress.report(source)
            assertEquals(report.toString(), JsonPrimitive(false), report["valid"])
        }
    }

    private fun model(surface: A2uiCoreSurfaceModel) =
        A2uiProtocol.toJson(surface.dataModel[A2uiDataPath("/")]).jsonObject

    @Suppress("UNCHECKED_CAST")
    private fun dispatch(surface: A2uiCoreSurfaceModel, graph: Map<String, JsonObject>, id: String) {
        surface.dispatchAction(id, A2uiProtocol.toValue(graph.getValue(id).getValue("action")) as Map<String, Any?>)
    }

    private suspend fun withExample(
        block: suspend (A2uiCoreSurfaceModel, Map<String, JsonObject>, Channel<A2uiClientEventMessage>) -> Unit,
    ) = coroutineScope {
        val source = javaClass.getResource("/a2ui/catalog_inputs.express")!!.readText()
        val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
        val graph = batch.componentsBySurface.getValue("card")
        val processor = A2uiMessageProcessor(listOf(operitA2uiCatalog))
        val events = Channel<A2uiClientEventMessage>(Channel.UNLIMITED)
        val errors = CopyOnWriteArrayList<A2uiClientErrorMessage>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect {
                when (it) {
                    is A2uiClientEventMessage -> events.send(it)
                    is A2uiClientErrorMessage -> errors.add(it)
                    else -> Unit
                }
            }
        }
        val job = launch(Dispatchers.Default) { processor.collectMessages() }
        try {
            val parser = A2uiJsonMessageParser(::JvmJsonReader)
            CardRepeat.lowerLines(batch).forEach { processor.processInput(parser, it) }
            processor.processMessage(A2uiUpdateDataModelMessage("card", "/ready", true))
            val surface = withTimeout(5_000) {
                processor.activeSurfaces.first { it.isNotEmpty() }.first()
            } as A2uiCoreSurfaceModel
            withTimeout(5_000) { while (surface.dataModel[A2uiDataPath("/ready")] != true) delay(10) }
            assertTrue("Loading must not dispatch actions", events.tryReceive().isFailure)
            block(surface, graph, events)
            assertTrue(errors.toString(), errors.isEmpty())
        } finally {
            job.cancelAndJoin()
            collector.cancelAndJoin()
            events.close()
        }
    }
}
