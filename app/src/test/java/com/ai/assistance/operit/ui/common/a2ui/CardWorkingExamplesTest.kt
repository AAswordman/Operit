package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Checks the authored examples' actual calculations and state transitions, not just syntax. */
class CardWorkingExamplesTest {
    private fun example(name: String) =
        A2uiProtocol.parse(CardExpress.compile(javaClass.getResource("/a2ui/$name.express")!!.readText()), true)

    @Test fun shoppingQuantitiesReactWithoutLosingCheckedRows() {
        val batch = example("shopping_list")
        val graph = batch.componentsBySurface.getValue("card")
        var data = CardRowState.prepare(graph, CardRepeat.initialData(batch.lines, "card"))
        fun rows() = CardRepeat.expand(graph, data).values.filter {
            it["component"]?.jsonPrimitive?.content == "CheckBox"
        }
        fun labels() = rows().map { CardRepeat.evaluate(it.getValue("label"), data).jsonPrimitive.content }
        assertEquals(listOf("大米 · 300 克", "鸡蛋 · 2 个", "青菜 · 400 克"), labels())
        data = CardRowState.set(graph, data, "ingredients", "rice", "done", JsonPrimitive(true))
        for (people in listOf(1, 4, 8)) {
            data = CardRowState.userWrite(data, "/form/people", JsonPrimitive(people))
            data = CardRowState.prepare(graph, data)
            assertEquals("大米 · ${150 * people} 克", labels().first().replace(",", ""))
            assertEquals(listOf(true, false, false), rows().map {
                CardRepeat.evaluate(it.getValue("value"), data).jsonPrimitive.boolean
            })
            A2uiProtocol.validateExpanded(CardRepeat.expand(graph, data), data)
        }
    }

    @Test fun travelTotalsUseSameInputsAtMinimumAndMaximum() {
        val batch = example("trip_budget")
        val graph = batch.componentsBySurface.getValue("card")
        var data = CardRepeat.initialData(batch.lines, "card")
        // Read the numeric expression inside formatNumber, avoiding locale-specific formatting.
        fun amount(id: String) = CardRepeat.evaluate(
            graph.getValue(id).getValue("text").jsonObject.getValue("args").jsonObject.getValue("value"), data
        ).jsonPrimitive.double
        assertEquals(800.0, amount("total"), 0.001)
        assertEquals(400.0, amount("perPerson"), 0.001)
        for ((people, nights, rooms) in listOf(Triple(1, 1, 1), Triple(6, 7, 3), Triple(2, 3, 2))) {
            data = CardRowState.userWrite(data, "/form", buildJsonObject {
                put("people", people); put("nights", nights); put("rooms", rooms)
            })
            val total = people * 120.0 + nights * rooms * 280.0
            assertEquals(total, amount("total"), 0.001)
            assertEquals(total / people, amount("perPerson"), 0.001)
            A2uiProtocol.validateExpanded(graph, data)
        }
    }

    @Test fun chartFilterKeepsLabelsValuesAndSumTogether() {
        val batch = example("filtered_chart")
        val graph = batch.componentsBySurface.getValue("card")
        var data = CardRepeat.initialData(batch.lines, "card")
        for ((region, values) in listOf("北区" to listOf(12, 18, 15), "南区" to listOf(9, 14, 24), "北区" to listOf(12, 18, 15))) {
            data = CardRowState.userWrite(data, "/filter/region", JsonPrimitive(region))
            val chart = CardRepeat.evaluate(graph.getValue("chart"), data).jsonObject
            assertEquals(listOf("一月", "二月", "三月"), chart.getValue("labels").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(values, chart.getValue("series").jsonArray.single().jsonObject.getValue("values").jsonArray.map { it.jsonPrimitive.int })
            val total = graph.getValue("total").getValue("text").jsonObject.getValue("args").jsonObject.getValue("value")
            assertEquals(values.sum().toDouble(), CardRepeat.evaluate(total, data).jsonPrimitive.double, 0.001)
            A2uiProtocol.validateExpanded(graph, data)
        }
    }
}
