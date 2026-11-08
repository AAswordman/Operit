package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardRepeatTest {
    private val source = """
        ${'$'}/rows = [{id:"a",title:"Alpha"},{id:"b",title:"Beta"}]
        root = Repeat(items=${'$'}/rows,key="id",maxItems=3,child=Text(text=item(field="title")))
    """.trimIndent()
    private fun graph() = A2uiProtocol.parse(CardExpress.compile(source), true).componentsBySurface.getValue("card")
    private fun data(rows: String) = Json.parseToJsonElement("""{"rows":$rows}""").jsonObject

    @Test fun reorderKeepsIdentityAndRemovalDropsNodes() {
        val g = graph()
        val first = CardRepeat.expand(g, data("""[{"id":"a","title":"Alpha"},{"id":"b","title":"Beta"}]"""))
        val swapped = CardRepeat.expand(g, data("""[{"id":"b","title":"New"},{"id":"a","title":"Alpha"}]"""))
        val ids = first.getValue("root").getValue("children").jsonArray
        assertEquals(ids.reversed(), swapped.getValue("root").getValue("children").jsonArray)
        assertEquals("New", swapped.getValue(ids[1].jsonPrimitive.content).getValue("text").jsonPrimitive.content)
        val removed = CardRepeat.expand(g, data("""[{"id":"a","title":"Alpha"}]"""))
        assertFalse(ids[1].jsonPrimitive.content in removed)
    }

    @Test fun emptyListCanGrowWithoutChangingTemplate() {
        val g = graph()
        assertEquals(1, CardRepeat.expand(g, data("[]")).size)
        assertEquals(2, CardRepeat.expand(g, data("""[{"id":"new","title":"New"}]""")).size)
    }

    @Test fun badItemsAndBudgetsAreRejected() {
        val g = graph()
        for (rows in listOf(
            """[{"id":"a","title":"A"},{"id":"a","title":"B"}]""",
            """[{"id":"a"}]""",
            """[{"id":5,"title":"A"}]""",
            """[{"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"c","title":"C"},{"id":"d","title":"D"}]""",
        )) assertTrue(runCatching { CardRepeat.expand(g, data(rows)) }.isFailure)
        assertFalse(CardExpress.report("""root = Text(text=item(field="title"))""").getValue("valid").jsonPrimitive.boolean)
    }

    @Test fun itemFunctionsAndAbsoluteBindingsWork() {
        val s = """
            ${'$'}/rows = [{id:"a",price:2}]
            ${'$'}/quantity = 3
            root = Repeat(items=${'$'}/rows,key="id",child=Display(text=formatNumber(value=multiply(a=item(field="price"),b=${'$'}/quantity))))
        """.trimIndent()
        val batch = A2uiProtocol.parse(CardExpress.compile(s), true)
        val data = CardRepeat.initialData(batch.lines, "card")
        val expanded = CardRepeat.expand(batch.componentsBySurface.getValue("card"), data)
        val value = expanded.values.first { it["component"]?.jsonPrimitive?.content == "Display" }.getValue("text")
        assertEquals("6.00", CardRepeat.evaluate(value, data).jsonPrimitive.content)
    }

    @Test fun itemCannotInjectBindingsAndChartDataIsValidated() {
        val injection = """
            ${'$'}/rows = [{id:"a",binding:{path:"/agent/note"}}]
            ${'$'}/agent = {note:"private"}
            root = Repeat(items=${'$'}/rows,key="id",child=TextField(label="x",value=item(field="binding")))
        """.trimIndent()
        assertFalse(CardExpress.report(injection).getValue("valid").jsonPrimitive.boolean)
        val chart = """
            ${'$'}/rows = [{id:"a",series:[{label:"x",values:["bad"]}]}]
            root = Repeat(items=${'$'}/rows,key="id",child=Chart(label="Chart",type="line",series=item(field="series")))
        """.trimIndent()
        assertFalse(CardExpress.report(chart).getValue("valid").jsonPrimitive.boolean)
    }
}
