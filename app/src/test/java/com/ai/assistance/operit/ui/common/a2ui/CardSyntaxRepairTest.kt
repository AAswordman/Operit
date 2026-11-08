package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardSyntaxRepairTest {
    private fun repaired(source: String, expected: String) {
        val result = CardExpress.prepare(source)
        assertNotNull(result.repair)
        assertEquals(CardExpress.compile(expected), result.document)
        assertEquals(result.document, CardExpress.compile(result.source))
    }

    @Test fun repairsOnlyPunctuationAndKeepsQuotedText() {
        repaired("""root = Text(text:"a ) = : [ \" b")""", """root = Text(text="a ) = : [ \" b")""")
        repaired("$/x = {value=1}\nroot = Text(text=\"ok\")", "$/x = {value:1}\nroot = Text(text=\"ok\")")
        val valid = """root = Text(text=") : =")"""
        assertEquals(valid, CardExpress.prepare(valid).source)
        assertNull(CardExpress.prepare(valid).repair)
    }

    @Test fun removesExtraClosingBeforeDisplayLabel() {
        repaired(
            """root = Display(text=formatNumber(value=multiply(a=2,b=subtract(a=10,b=3)))),label="Total")""",
            """root = Display(text=formatNumber(value=multiply(a=2,b=subtract(a=10,b=3))),label="Total")""",
        )
        repaired("""root = Text(text="ok"))""", """root = Text(text="ok")""")
    }

    @Test fun completesOnlyBoundedClosingSuffix() {
        repaired("""root = Text(text="ok" """.trimEnd(), """root = Text(text="ok")""")
        assertFalse(CardExpress.report("""root = Column(children=[Text(text="ok")""")["valid"]!!.jsonPrimitive.boolean)
        assertFalse(CardExpress.report("root = Text(text=\"truncated")["valid"]!!.jsonPrimitive.boolean)
    }

    @Test fun invalidMeaningAndResourceLimitsStillFail() {
        for (source in listOf(
            """root = Text(text=eval(value="x")))""",
            """root = Text(text=$/missing))""",
            """root = Button(child=Text(text="go"),action=Event(name="shell")))""",
            """root = Text(text="ok",unknown=1))""",
        )) assertFalse(source, CardExpress.report(source)["valid"]!!.jsonPrimitive.boolean)
        val oversized = """root = Text(text="${"x".repeat(8193)}"))"""
        assertFalse(CardExpress.report(oversized)["valid"]!!.jsonPrimitive.boolean)
    }

    @Test fun refusesAmbiguousOrUnsearchedCandidates() {
        val ambiguous = "root = Display(text=formatNumber(value=formatNumber(value=1)\n,grouping=true),decimals=2))"
        assertFalse(CardExpress.report(ambiguous)["valid"]!!.jsonPrimitive.boolean)
        // Validator double isolates the uniqueness/budget rule from component schemas.
        val error = CardExpress.SyntaxFailure(1, 1, "Put each declaration on a new line.")
        assertNull(CardSyntaxRepair.attempt("(a)(b))", error) {
            CardExpress.Prepared(it, it)
        })
        var attempts = 0
        assertNull(CardSyntaxRepair.attempt("()".repeat(65) + ")", error) {
            attempts++
            CardExpress.Prepared(it, "same")
        })
        assertEquals(0, attempts)
    }

    @Test fun repairedSourceCanBeRevisedAgain() {
        val first = CardExpress.prepare("""root = Text(text:"first")""")
        val second = CardExpress.prepareRevision(first.source, """root = Text(text:"second")""", emptyList())
        assertNotNull(second.repair)
        assertEquals(CardExpress.compile("""root = Text(text="second")"""), second.document)
        val third = CardExpress.prepareRevision(second.source, """root = Text(text="third")""", emptyList())
        assertNull(third.repair)
    }

    @Test fun completeDocumentStillHasCalculationCountBudget() {
        var formula = "1"
        repeat(4) { formula = "add(a=1,b=$formula)" }
        val child = "Text(text=formatNumber(value=$formula))"
        fun source(count: Int) = "root = Column(children=[${List(count) { child }.joinToString(",")}])"
        assertTrue(CardExpress.report(source(25))["valid"]!!.jsonPrimitive.boolean)
        val report = CardExpress.report(source(26))
        assertFalse(report["valid"]!!.jsonPrimitive.boolean)
        assertTrue(report.toString(), report["message"]!!.jsonPrimitive.content.contains("count exceeds 128"))
    }
}
