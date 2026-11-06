package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardExpressTest {
    @Test fun nestedComponentsAndCallsCompileWithoutManualIds() {
        val source = """
            ${'$'}/form = {years:4}
            root = Column(children=[title,Display(text=formatNumber(value=multiply(a=${'$'}/form/years,b=10)))])
            title = Heading(text="Cost")
        """.trimIndent()
        val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
        val graph = batch.componentsBySurface.getValue("card")
        assertEquals(3, graph.size)
        assertEquals(listOf("title", "inline_1"), graph.getValue("root")["children"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(CardAuthoringCatalog.guide().contains("Display"))
    }

    @Test fun examplesPassNativeProtocol() {
        for (name in listOf("shopping_list", "trip_budget", "filtered_chart", "calculator", "cost_calculator", "charts", "form", "info", "stats", "progress", "responsive", "data", "vector", "repeat", "curves", "interactive_flow", "p2_controls", "p2_lists", "flow_steps", "flowchart", "data_story", "mixed_branch", "profile", "backgrounds", "surfaces", "catalog_inputs")) {
            val source = javaClass.getResource("/a2ui/$name.express")!!.readText()
            val report = CardExpress.report(source)
            assertEquals("$name: $report", JsonPrimitive(true), report["valid"])
            assertTrue(A2uiProtocol.parse(report["document"]!!.jsonPrimitive.content, true).complete)
        }
    }

    @Test fun rejectsUnknownOrUnsafeSyntaxWithLocations() {
        val bad = listOf(
            "root = Text(\"hi\")",
            "root = Text(text=\"hi\",unknown=1)",
            "root = Column(children=[missing])",
            "root = Column(children=[root])",
            "root = Text(text=${'$'}/missing)",
            "${'$'}/form = {x:1}\n${'$'}/form/x = 2\nroot = Text(text=\"hi\")",
            "${'$'}/__proto__/x = 1\nroot = Text(text=\"hi\")",
            "${'$'}/form = {constructor:1}\nroot = Text(text=\"hi\")",
            "${'$'}/x = add(a=1,b=2)\nroot = Text(text=\"hi\")",
            "root = Text(text=\"hi\")\nunused = Text(text=\"bye\")",
            "root = Text(text=eval(value=\"boom\"))",
            "root = Button(child=Text(text=\"go\"),action=Event(name=\"shell\"))",
            "root = Text(text=\"hi\") root2 = Text(text=\"bad\")",
            "root = Text(text=\"hi\",text=\"bad\")",
            "root = Text(text=" + "[".repeat(30) + "1" + "]".repeat(30) + ")",
            "${'$'}/style = \"h1\"\nroot = Text(text=\"Hello\",variant=${'$'}/style)",
            "${'$'}/flag = null\nroot = CheckBox(label=\"Flag\",value=${'$'}/flag)",
            "root = Chart(label=\"Bad\",type=\"bogus\",series=[])",
            "root = Chart(label=\"Bad\",type=\"bar\",series=[{label:\"A\",values:[\"bad\"]}],labels=[\"x\"])",
            "${'$'}/rows = [[1,2]]\nroot = Table(columns=[\"x\"],rows=${'$'}/rows)",
            "root = Text(text=add(a=1,b=2))",
            "root = Text(text=if(condition=true,then=\"ok\",else=1))",
            "root = Header(text=\"Title\",tone=\"pink\")",
            "root = Display(text=\"42\",align=\"center\")",
            "root = Flow(label=\"x\",steps=[])",
            "root = Flow(label=\"x\",steps=[{title:\"\"}])",
            "root = Flow(label=\"x\",steps=[{title:\"a\",icon:\"not-an-icon\"}])",
            "root = Flow(label=\"x\",steps=[{title:\"a\",onClick:\"b\"}])",
            "root = Flow(label=\"x\",steps=[{title:\"a\"}],direction=\"diagonal\")",
            "root = Flow(label=\"x\",steps=[" + List(9) { "{title:\"s$it\"}" }.joinToString(",") + "])",
        )
        bad.forEach {
            val report = CardExpress.report(it)
            assertEquals("$it: $report", JsonPrimitive(false), report["valid"])
            assertTrue(report.toString(), report["line"]!!.jsonPrimitive.int >= 1)
            assertTrue(report.toString(), report["column"]!!.jsonPrimitive.int >= 1)
        }
    }

    @Test fun flowStepsMayComeFromStateButStayValidated() {
        val ok = CardExpress.report("${'$'}/plan = {steps:[{title:\"A\"},{title:\"B\"}]}\nroot = Flow(label=\"x\",steps=${'$'}/plan/steps)")
        assertEquals(ok.toString(), JsonPrimitive(true), ok["valid"])
        val bad = CardExpress.report("${'$'}/plan = {steps:[{title:\"A\",icon:\"not-an-icon\"}]}\nroot = Flow(label=\"x\",steps=${'$'}/plan/steps)")
        assertEquals(bad.toString(), JsonPrimitive(false), bad["valid"])
    }

    @Test fun displayOnlyExamplesNeedNoStateOrActions() {
        for (name in listOf("flow_steps", "flowchart", "data_story")) {
            val source = javaClass.getResource("/a2ui/$name.express")!!.readText()
            assertFalse(name, source.contains("$/"))
            assertFalse(name, source.contains("Event("))
            val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
            val graph = batch.componentsBySurface.getValue("card")
            assertTrue(name, graph.values.none { "action" in it || "onChange" in it })
            assertNotEquals(name, "Card", graph.getValue("root")["component"]!!.jsonPrimitive.content)
        }
    }

    @Test fun catalogMatchesRendererNamesAndPropertyKeys() {
        assertEquals(A2uiProtocol.components, CardAuthoringCatalog.components.keys)
        val actual = operitA2uiCatalog.components.associateBy { it.name }
        CardAuthoringCatalog.components.forEach { (name, fields) ->
            // Repeat is a bounded compiler template, lowered to official Column/components.
            if (name == "Repeat") return@forEach
            assertNotNull(name, actual[name])
            assertTrue(name, actual.getValue(name).properties.map { it.key }.containsAll(fields.map { it.name }))
        }
    }
}
