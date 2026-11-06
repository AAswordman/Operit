package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.engine.schema.A2uiCoreSchemaValidator
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardRendererContractTest {
    @Test fun sharedOutlinedIconsAcceptNewNamesLegacyAliasAndBindings() {
        androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.Icon.BuiltIn.entries.forEach {
            assertTrue("Missing legacy icon ${it.value}", it.value in cardFlowIcons)
        }
        for (name in listOf("phone", "globe", "browser", "calendarToday", "info", "check")) {
            assertTrue(name in cardFlowIcons)
            validateRenderer("""root = Row(children=[Icon(name="$name"),Text(text="Label")])""")
            validateRenderer("""root = Flow(label="Steps",steps=[{title:"Device",icon:"$name"}])""")
        }
        validateRenderer("${'$'}/icon = \"phone\"\nroot = Icon(name=${'$'}/icon)")
    }

    @Test fun preflightRejectsPayloadAcceptedByProtocolButNotOfficialSchema() {
        val valid = CardExpress.compile("""
            root = Button(child=Text(text="Submit"),
                action=Event(name="ai_submit",context={value:"plain"}))
        """.trimIndent())
        // Context itself must remain an object even though its values may be structured.
        val incompatible = valid.replace("\"context\":{\"value\":\"plain\"}", "\"context\":[\"do-not-relay\"]")
        assertNotEquals(valid, incompatible)
        val report = A2uiProtocol.validationReport(incompatible)
        assertEquals(false, report.getValue("valid").jsonPrimitive.boolean)
        assertTrue(report.getValue("message").jsonPrimitive.content.contains("Button.action"))
        assertFalse(report.toString().contains("do-not-relay"))
    }

    @Test fun clickableFlowWithInputChangeMatchesRendererSchema() {
        val source = """
            ${'$'}/ui = {current:"first", choice:"A"}
            root = Column(children=[choice, first])
            choice = Segmented(label="Choice",options=["A","B"],value=${'$'}/ui/choice,
                onChange=Event(name="local_action",context={op:"set",path:"/ui/choice",value:event_value(field="value")}))
            first = Pressable(label="First step",child=Text(text="Select"),
                selected=equals(a=${'$'}/ui/current,b="first"),
                action=Event(name="local_action",context={op:"set",path:"/ui/current",value:"first"}))
        """.trimIndent()
        validateRenderer(source)
    }

    @Test fun allShippedExamplesMatchRendererSchemas() {
        for (name in listOf("shopping_list", "trip_budget", "filtered_chart", "calculator", "cost_calculator", "charts", "form", "info", "stats",
            "progress", "responsive", "data", "vector", "repeat", "curves", "interactive_flow",
            "p2_controls", "p2_lists", "flow_steps", "flowchart", "data_story", "mixed_branch", "profile", "catalog_inputs")) {
            try {
                validateRenderer(javaClass.getResource("/a2ui/$name.express")!!.readText())
            } catch (e: Exception) {
                throw AssertionError("Renderer rejected example $name", e)
            }
        }
    }

    @Test fun actualCatalogExamplesMatchRendererSchemas() {
        CardAuthoringCatalog.authoringExamples.forEach { (name, source) ->
            assertTrue(name, CardAuthoringCatalog.guide().contains(source))
            validateRenderer(source)
        }
    }

    private fun validateRenderer(source: String) {
        val batch = A2uiProtocol.parse(CardExpress.compile(source), true)
        val catalog = operitA2uiCatalog as androidx.a2ui.engine.catalog.A2uiCoreCatalog
        val validator = A2uiCoreSchemaValidator(catalog)
        batch.componentsBySurface.forEach { (surface, graph) ->
            val data = CardRowState.prepare(graph, CardRepeat.initialData(batch.lines, surface), false)
            CardRepeat.payloads(CardRepeat.expand(graph, data)).forEach { payload ->
                validator.validateSchema(payload.properties,
                    requireNotNull(catalog.componentDefinitions[payload.type]).propertySchema,
                    "/components/${payload.id}")
            }
        }
    }
}
