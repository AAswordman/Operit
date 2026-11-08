package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardP2Test {
    private fun valid(source: String): Boolean = CardExpress.report(source)["valid"] == JsonPrimitive(true)
    @Test fun fieldsDatesTablesAndFormulaValidateBeforeRendering() {
        assertTrue(valid("""root = DatePicker(label="Date",value="",minDate="2026-01-01",maxDate="2026-12-31",clearable=true)"""))
        assertFalse(valid("""root = DatePicker(label="Date",value="",minDate="2026-12-31",maxDate="2026-01-01")"""))
        assertFalse(valid("""root = DatePicker(label="Date",value="",minDate="2026-02-30")"""))
        assertTrue(valid("""root = TextArea(label="Notes",value="",minLines=2,maxLines=6,error="Required")"""))
        assertFalse(valid("""root = TextArea(label="Notes",value="",minLines=8,maxLines=2)"""))
        assertTrue(valid("""root = TextField(label="Code",value="",mask="digits")"""))
        assertTrue(valid("""root = Button(child=Text(text="Save"),loading=true,action=Event(name="local_action",context={op:"save"}))"""))
        assertTrue(valid("""root = Table(columns=["Name","Value"],rows=[["A",12]],columnWidths=[100,80],maxHeight=160)"""))
        assertFalse(valid("""root = Table(columns=["Name","Value"],rows=[],columnWidths=[100])"""))
        assertTrue(valid("""root = Formula(label="Ratio",text="\\frac{1}{2}")"""))
        assertFalse(valid("""root = Formula(label="Bad",text="\\def\\a{\\a}\\a")"""))
        assertTrue(valid("""root = Tooltip(label="Help",text="A short explanation")"""))
        assertTrue(valid("""root = Stepper(label="Steps",labels=["First","Second"],current=1)"""))
        assertFalse(valid("""root = Stepper(label="Steps",labels=["First"],current=2)"""))
    }
    @Test fun mathAndDateBudgets() {
        assertNotNull(cardDateBounds("", ""))
        assertNull(cardDateBounds("1800-01-01", ""))
        assertTrue(validCardFormula("""\sqrt{x^2+y^2}"""))
        assertFalse(validCardFormula("""\input{secret}"""))
        assertFalse(validCardFormula("{".repeat(13) + "x" + "}".repeat(13)))
        assertFalse(validCardFormula("x".repeat(1025)))
        assertFalse(validCardFormula("x}"))
    }
    @Test fun boundedArrayFunctionsKeepStableOrderAndRejectAmbiguousUpdates() {
        val rows = listOf(mapOf("id" to "b", "n" to 2), mapOf("id" to "a", "n" to 1), mapOf("id" to "c", "n" to 2))
        fun call(name: String, vararg args: Pair<String, Any>) =
            CardFunctions.evaluate(name, mapOf("items" to rows) + args)
        assertEquals(listOf(rows[1],rows[0],rows[2]), call("arraySort","field" to "n"))
        assertEquals(listOf(rows[0],rows[2],rows[1]), call("arraySort","field" to "n","direction" to "desc"))
        assertNull(call("arraySort","field" to "n","direction" to "random"))
        assertEquals(rows[1],call("arrayFind","field" to "id","value" to "a","fallback" to "none"))
        assertEquals("none",call("arrayFind","field" to "id","value" to "missing","fallback" to "none"))
        assertEquals(listOf(rows[0],rows[1]+("n" to 3),rows[2]),
            call("arrayUpdateByKey","key" to "id","value" to "a","patch" to mapOf("n" to 3)))
        assertNull(call("arrayUpdateByKey","key" to "id","value" to "a","patch" to mapOf("id" to "z")))
        assertNull(CardFunctions.evaluate("arrayUpdateByKey", mapOf("items" to listOf(rows[0],rows[0]),"key" to "id",
            "value" to "b","patch" to mapOf("n" to 3))))
        assertNull(CardFunctions.evaluate("arraySort",mapOf("items" to listOf("1",2))))
    }
    private val nested = """
        ${'$'}/groups = [{id:"a",children:[{id:"same",title:"A"}]},{id:"b",children:[{id:"same",title:"B"}]}]
        root = Repeat(items=${'$'}/groups,key="id",child=inner)
        inner = Repeat(items=item(field="children"),key="id",child=row)
        row = CheckBox(label=item(field="title"),value=item_state(field="done",default=false))
    """.trimIndent()
    @Test fun nestedStableStateSurvivesParentReorderAndRejectsStaleParent() {
        val report = CardExpress.report(nested)
        assertEquals(report.toString(), JsonPrimitive(true), report["valid"])
        val batch = A2uiProtocol.parse(CardExpress.compile(nested),true)
        val graph = batch.componentsBySurface.getValue("card")
        var data = CardRowState.prepare(graph,CardRepeat.initialData(batch.lines,"card"))
        data = CardRowState.set(graph,data,"inner","same","done",JsonPrimitive(true),parents=listOf("a"))
        fun values(d: JsonObject) = CardRepeat.expand(graph,d).values.filter { it["component"] == JsonPrimitive("CheckBox") }
            .associate { it.getValue("label").jsonPrimitive.content to CardRepeat.evaluate(it.getValue("value"),d).jsonPrimitive.boolean }
        assertEquals(mapOf("A" to true,"B" to false),values(data))
        val groups = data.getValue("groups").jsonArray
        data = CardRepeat.put(data,"/groups",JsonArray(groups.reversed()))
        assertEquals(mapOf("A" to true,"B" to false),values(CardRowState.prepare(graph,data)))
        val hidden = CardRepeat.put(data,"/groups",JsonArray(listOf(groups[1])))
        assertTrue(runCatching { CardRowState.set(graph,hidden,"inner","same","done",JsonPrimitive(false),parents=listOf("a")) }.isFailure)
        assertEquals(mapOf("A" to true,"B" to false),values(CardRowState.prepare(graph,Json.parseToJsonElement(data.toString()).jsonObject)))
    }
    @Test fun nestedExpansionSharesNodeBudget() {
        val rows = (0..31).joinToString { """{id:"k$it",children:[${(0..31).joinToString { n -> """{id:"c$n",title:"X"}""" }}]}""" }
        val huge = CardExpress.report(nested.substringAfter('\n').let { "${'$'}/groups=[$rows]\n$it" }
            .replace("""key="id",child=""", """key="id",maxItems=32,child="""))
        assertEquals(JsonPrimitive(false), huge["valid"])
        assertTrue(huge.toString(), huge["message"]!!.jsonPrimitive.content.contains("rendered components"))
        assertFalse(valid(nested.replace("""default=false""","""default=false""")
            .replace("child=row)", "child=Column(children=[row,TextField(label=\"Conflict\",value=item_state(field=\"done\",default=\"\"))]))")
            .replace(Regex("""\$\x2fgroups = .*""")) { "${'$'}/groups=[]" }))
    }
    @Test fun nestedItemCalculationsShareTheRenderedCallBudget() {
        var expression = """item(field="children")"""
        repeat(3) { expression = """if(condition=equals(a=1,b=1),then=$expression,else=[])""" }
        val source = """
            ${'$'}/groups=[${(0..31).joinToString { """{id:"g$it",children:[{id:"a"}]}""" }}]
            root=Repeat(items=${'$'}/groups,key="id",maxItems=32,child=inner)
            inner=Repeat(items=$expression,key="id",child=Text(text="Row"))
        """.trimIndent()
        val report = CardExpress.report(source)
        assertEquals(JsonPrimitive(false), report["valid"])
        assertTrue(report.toString(), report["message"]!!.jsonPrimitive.content.contains("calculation count"))
    }
    @Test fun itemAndLeafCalculationsShareOne128CallLimit() {
        val data = (0..31).joinToString { """{id:"k$it",n:$it}""" }
        val expression = """concat(values=[concat(values=[concat(values=[formatNumber(value=item(field="n"))])])])"""
        val source = """
            ${'$'}/rows=[$data]
            root=Repeat(items=${'$'}/rows,key="id",maxItems=32,child=Text(text=$expression))
        """.trimIndent()
        assertTrue(CardExpress.report(source).toString(),valid(source))
        val report=CardExpress.report(source.replace("items=${'$'}/rows",
            """items=arrayFilter(items=${'$'}/rows,field="n",operator="gte",value=0)"""))
        assertEquals(JsonPrimitive(false),report["valid"])
        assertTrue(report.toString(),report["message"]!!.jsonPrimitive.content.contains("calculation count"))
    }
    @Test fun liveNewRowsInitializeBeforeEvaluatingNestedItems() {
        val source = """
            ${'$'}/agent={groups:[]}
            root=Repeat(items=${'$'}/agent/groups,key="id",child=inner)
            inner=Repeat(items=if(condition=item_state(field="show",default=true),then=item(field="children"),else=[]),key="id",child=row)
            row=Text(text=item(field="title"))
        """.trimIndent()
        val batch = A2uiProtocol.parse(CardExpress.compile(source),true)
        val graph = batch.componentsBySurface.getValue("card")
        val data = CardRepeat.put(CardRepeat.initialData(batch.lines,"card"),"/agent/groups",
            Json.parseToJsonElement("""[{"id":"new","children":[{"id":"child","title":"Appeared"}]}]"""))
        val prepared = CardRowState.prepare(graph,data,live=true)
        assertTrue(CardRepeat.expand(graph,prepared).values.any { it["text"] == JsonPrimitive("Appeared") })
    }
    @Test fun formulasCanBeEditedThroughInvalidIntermediateValuesBesideRepeat() {
        val source = """
            ${'$'}/formula="x"
            ${'$'}/items=[{id:"a"}]
            root=Column(children=[Formula(label="Math",text=${'$'}/formula),Repeat(items=${'$'}/items,key="id",child=Text(text="Row"))])
        """.trimIndent()
        val batch=A2uiProtocol.parse(CardExpress.compile(source),true)
        val graph=batch.componentsBySurface.getValue("card")
        for (text in listOf("", "\\", "\\frac{", "\\frac{1}{2}")) {
            val data=CardRepeat.put(CardRepeat.initialData(batch.lines,"card"),"/formula",JsonPrimitive(text))
            A2uiProtocol.validateExpanded(CardRepeat.expand(graph,data),data)
        }
    }
}
