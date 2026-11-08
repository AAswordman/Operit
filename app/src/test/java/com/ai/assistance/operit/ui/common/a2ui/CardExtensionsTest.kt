package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardExtensionsTest {
    @Test fun collectionAndDateOperationsAreBoundedAndDeterministic() {
        val items = listOf(mapOf("value" to 3, "name" to "A"), mapOf("value" to 8, "name" to "B"))
        fun run(name: String, vararg args: Pair<String, Any>) = CardFunctions.evaluate(name, mapOf(*args))
        val filtered = run("arrayFilter", "items" to items, "field" to "value", "operator" to "gt", "value" to 4)!!
        assertEquals(listOf(8), run("arrayMap", "items" to filtered, "field" to "value"))
        assertEquals(11.0, run("arraySum", "items" to items, "field" to "value"))
        assertEquals(5.5, run("arrayAverage", "items" to items, "field" to "value"))
        assertNull(run("arrayAverage", "items" to emptyList<Int>()))
        assertNull(run("arrayMap", "items" to items, "field" to "missing"))
        assertNull(run("arrayLength", "items" to List(129){ 1 }))
        assertNull(run("stringReplace", "text" to "a".repeat(8192), "search" to "a", "replacement" to "xx"))
        assertEquals("2024-02-29", run("dateAddDays","date" to "2024-02-28","days" to 1))
        assertNull(run("dateAddDays","date" to "2024-02-30","days" to 1))
        assertEquals(2L, run("dateDiffDays","start" to "2024-02-28","end" to "2024-03-01"))
        assertEquals("2024-02",run("formatDate","date" to "2024-02-29","format" to "yearMonth"))
        assertEquals(items, run("if","condition" to true,"then" to items,"else" to emptyList<Any>()))
    }

    @Test fun revisionsAreAtomicAndRunScoped() {
        val source = "root = Column(children=[title])\ntitle = Text(text=\"Before\")"
        val run = "revision-test"
        CardDelivery.begin(run)
        try {
            val first = CardDelivery.register(run,CardExpress.compile(source),source)
            val (updated, document) = CardExpress.revise(CardDelivery.source(run,first)!!,
                "title = Text(text=\"After\")",emptyList())
            val second = CardDelivery.register(run,document,updated)
            assertNotEquals(first,second)
            assertTrue(CardDelivery.resolve(run,first)!!.contains("Before"))
            assertTrue(CardDelivery.resolve(run,second)!!.contains("After"))
            assertNull(CardDelivery.source("another-run",first))
            assertTrue(runCatching { CardExpress.revise(updated,"",listOf("title")) }.isFailure)
            assertTrue(CardDelivery.resolve(run,second)!!.contains("After"))
            assertTrue(runCatching { CardExpress.revise(updated,"title = Text(text=\"x\")",listOf("title")) }.isFailure)
        } finally { CardDelivery.end(run) }
    }

    @Test fun layoutAndVectorsRejectUnboundedOrInvalidValues() {
        for (source in listOf(
            "root = Scroll(child=Text(text=\"hi\"),maxHeight=9999)",
            "root = Panel(child=Text(text=\"hi\"),padding=\"huge\")",
            "root = Text(text=\"hi\",lineClamp=0)",
            "root = Popover(label=\"Outer\",child=Popover(label=\"Inner\",child=Text(text=\"hi\")))",
            "root = Vector(label=\"test\",width=100,height=100,shapes=[{type:\"circle\",cx:90,cy:50,r:30}])",
            "root = Vector(label=\"test\",width=100,height=100,shapes=[{type:\"script\",text:\"alert(1)\"}])"
        )) assertEquals(source, JsonPrimitive(false), CardExpress.report(source)["valid"])
        assertEquals(1,cardAutoColumns(280f,160f,1f))
        assertEquals(2,cardAutoColumns(360f,160f,1f))
        assertEquals(1,cardAutoColumns(360f,160f,1.5f))
        assertEquals(4,cardAutoColumns(1000f,80f,1f))
    }
}
