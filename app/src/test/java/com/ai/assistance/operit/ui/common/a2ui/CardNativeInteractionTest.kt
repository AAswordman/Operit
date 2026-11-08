package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardNativeInteractionTest {
    @Test fun rapidInputReadsCurrentStateWithoutWaitingForRecomposition() {
        var current: Any = 0
        val previous = mutableListOf<Any?>()
        fun input(next: Int) = applyCardInput({ current }, next, { it(); true }, { current = next }) { previous.add(it) }
        input(1); input(0); input(1); input(2); input(2)
        assertEquals(2, current)
        assertEquals(listOf(0, 1, 0, 1), previous)
        assertTrue(sameCardInput(2, 2.0))
        applyCardInput({ current }, 3, { false }, { current = 3 }) { fail("Rejected mutation must not emit") }
        assertEquals(2, current)
    }

    @Test fun stringRowStateAcceptsBooleanLookingText() {
        val b = batch(rows.replace("CheckBox", "TextField").replace("default=false", "default=\"\""))
        val graph = b.componentsBySurface.getValue("card")
        var data = CardRowState.prepare(graph, CardRepeat.initialData(b.lines, "card"))
        for (value in listOf("true", "false", "普通文字", "")) {
            data = CardRowState.set(graph, data, "root", "a", "done", JsonPrimitive(value))
            assertEquals(JsonPrimitive(value), CardRepeat.field(data, CardRowState.path("root", "a", "done").drop(1)))
        }
    }
    private fun batch(source: String) = A2uiProtocol.parse(CardExpress.compile(source), true)
    private val rows = """
        ${'$'}/rows = [{id:"a",title:"Alpha"},{id:"b",title:"Beta"}]
        root = Repeat(items=${'$'}/rows,key="id",child=CheckBox(label=item(field="title"),value=item_state(field="done",default=false)))
    """.trimIndent()

    @Test fun rowsRemainIndependentAcrossReorderFilterAndRestore() {
        val b = batch(rows)
        val graph = b.componentsBySurface.getValue("card")
        val initial = CardRowState.prepare(graph, CardRepeat.initialData(b.lines, "card"))
        val marked = CardRowState.set(graph, initial, "root", "b", "done", JsonPrimitive(true))
        assertEquals(JsonPrimitive(false), CardRepeat.field(marked, CardRowState.path("root", "a", "done").drop(1)))
        assertEquals(JsonPrimitive(true), CardRepeat.field(marked, CardRowState.path("root", "b", "done").drop(1)))
        val reordered = CardRepeat.put(marked, "/rows", JsonArray(marked.getValue("rows").jsonArray.reversed()))
        assertEquals(marked[CardRowState.ROOT], CardRowState.prepare(graph, reordered)[CardRowState.ROOT])
        val hidden = CardRepeat.put(reordered, "/rows", JsonArray(emptyList()))
        assertEquals(marked[CardRowState.ROOT], CardRowState.prepare(graph, hidden)[CardRowState.ROOT])
        assertTrue(runCatching { CardRowState.set(graph, hidden, "root", "b", "done", JsonPrimitive(false)) }.isFailure)
        val restored = Json.parseToJsonElement(marked.toString()).jsonObject
        assertEquals(marked, CardRowState.prepare(graph, restored))
        val replaced = CardRowState.userWrite(marked, "/", buildJsonObject { put("rows", marked.getValue("rows")) })
        assertEquals(marked[CardRowState.ROOT], replaced[CardRowState.ROOT])
        assertTrue(runCatching { CardRowState.userWrite(marked, "/${CardRowState.ROOT}", JsonObject(emptyMap())) }.isFailure)
        assertEquals(3, CardRepeat.expand(graph, restored).size)
    }

    @Test fun rowFieldsMustBeDeclaredAndTypeStableEvenWhenEmpty() {
        val b = batch(rows)
        val graph = b.componentsBySurface.getValue("card")
        val initial = CardRowState.prepare(graph, CardRepeat.initialData(b.lines, "card"))
        assertTrue(runCatching { CardRowState.set(graph, initial, "root", "b", "unknown", JsonPrimitive(true)) }.isFailure)
        assertTrue(runCatching { CardRowState.set(graph, initial, "root", "b", "done", JsonPrimitive("yes")) }.isFailure)
        val bad = """
            ${'$'}/rows = []
            root = Repeat(items=${'$'}/rows,key="id",child=Column(children=[
                CheckBox(label="A",value=item_state(field="done",default=false)),
                CheckBox(label="B",value=item_state(field="done",default=true))]))
        """.trimIndent()
        assertFalse(CardExpress.report(bad).getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report("""root = CheckBox(label="x",value=item_state(field="done",default=false))""").getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report("""${'$'}/_operit_rows = {}
            root = Text(text="x")""").getValue("valid").jsonPrimitive.boolean)
    }

    @Test fun rowStateIsBudgetedIncludingHiddenItems() {
        val b = batch(rows)
        val graph = b.componentsBySurface.getValue("card")
        var data = CardRepeat.initialData(b.lines, "card")
        var rejected = false
        for (i in 1..200) {
            data = CardRepeat.put(data, "/rows", buildJsonArray {
                add(buildJsonObject { put("id", "id$i"); put("title", "Item") })
            })
            val attempt = runCatching { CardRowState.prepare(graph, data) }
            if (attempt.isFailure) { rejected = true; break }
            data = attempt.getOrThrow()
        }
        assertTrue("Hidden state must remain bounded", rejected)
    }

    @Test fun pressableRejectsInteractiveDescendantsButAllowsDiagramContent() {
        val prefix = """root = Pressable(label="step",action=Event(name="local_action",context={op:"set",path:"/step",value:1}),child="""
        assertTrue(CardExpress.report(prefix + """Column(children=[Text(text="Step"),Badge(text="Done")]))""").getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report(prefix + """TextField(label="Nested",value=""))""").getValue("valid").jsonPrimitive.boolean)
    }

    @Test fun vectorLeavesBindButStructureAndRangesStayBounded() {
        val b = batch("""
            ${'$'}/drawing = {end:20,tone:"success",label:"Ready"}
            root = Vector(label="Arrow",width=100,height=50,shapes=[
                {type:"arrow",x1:1,y1:10,x2:${'$'}/drawing/end,y2:10,tone:${'$'}/drawing/tone},
                {type:"text",x:1,y:20,text:${'$'}/drawing/label}])
        """.trimIndent())
        val component = b.componentsBySurface.getValue("card").getValue("root")
        val data = CardRepeat.initialData(b.lines, "card")
        val first = CardVectorData.parse(CardRepeat.evaluate(component, data).jsonObject)
        assertEquals(20f, first.shapes[0].values.getValue("x2"))
        val changed = CardRepeat.put(data, "/drawing/end", JsonPrimitive(90))
        assertEquals(90f, CardVectorData.parse(CardRepeat.evaluate(component, changed).jsonObject).shapes[0].values.getValue("x2"))
        val bad = CardRepeat.put(data, "/drawing/end", JsonPrimitive(500))
        assertTrue(runCatching { CardVectorData.parse(CardRepeat.evaluate(component, bad).jsonObject) }.isFailure)
        assertFalse(CardExpress.report("""
            ${'$'}/kind = "line"
            root = Vector(label="bad",width=20,height=20,shapes=[{type:${'$'}/kind,x1:0,y1:0,x2:5,y2:5}])
        """.trimIndent()).getValue("valid").jsonPrimitive.boolean)
    }

    @Test fun changeEventsAreLocalAndTheirValuesAreNotExecutablePayloads() {
        val source = """
            ${'$'}/form = {input:"",echo:""}
            root = TextField(label="Input",value=${'$'}/form/input,onChange=Event(name="local_action",
                context={op:"set",path:"/form/echo",value:event_value(field="value")}))
        """.trimIndent()
        val event = batch(source).componentsBySurface.getValue("card").getValue("root")
            .getValue("onChange").jsonObject.getValue("event").jsonObject.getValue("context")
        assertEquals(JsonPrimitive("typed"), cardEventValues(event, mapOf("value" to "typed")).jsonObject["value"])
        assertTrue(runCatching { cardEventValues(event, mapOf("value" to mapOf("call" to "unsafe"))) }.isFailure)
        assertFalse(CardExpress.report(source.replace("local_action", "tool_action")).getValue("valid").jsonPrimitive.boolean)
        assertFalse(CardExpress.report("""root = Text(text=event_value(field="value"))""").getValue("valid").jsonPrimitive.boolean)
    }

    @Test fun completedFlowStepsRemainSelectableAndCanAdvanceAgain() {
        val b = batch(javaClass.getResource("/a2ui/interactive_flow.express")!!.readText())
        val graph = b.componentsBySurface.getValue("card")
        var data = CardRowState.prepare(graph, CardRepeat.initialData(b.lines, "card"))
        data = CardRowState.set(graph, data, "steps", "step3", "done", JsonPrimitive(true))
        data = CardRepeat.put(data, "/flow/step", JsonPrimitive(3))
        val expanded = CardRepeat.expand(graph, data)
        A2uiProtocol.validateExpanded(expanded, data)
        val next = expanded.values.first { c ->
            c["component"]?.jsonPrimitive?.content == "Button" &&
                c["variant"]?.jsonPrimitive?.content == "primary"
        }
        val condition = next.getValue("checks").jsonArray[0].jsonObject.getValue("condition")
        assertEquals(JsonPrimitive(true), CardRepeat.evaluate(condition, data))
        val context = next.getValue("action").jsonObject.getValue("event").jsonObject.getValue("context")
        val action = CardRepeat.evaluate(context, data).jsonObject
        assertEquals(JsonPrimitive("add"), action["op"])
        assertEquals(JsonPrimitive(1), action["value"])
        assertTrue(expanded.values.filter { it["component"]?.jsonPrimitive?.content == "Pressable" }.all { "disabled" !in it })
        data = CardRepeat.put(data, "/flow/flavor", JsonPrimitive("甜口"))
        assertEquals(data, CardRowState.prepare(graph, Json.parseToJsonElement(data.toString()).jsonObject))
    }
}
