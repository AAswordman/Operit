package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CardHtmlDocumentTest {
    private fun report(html: String, css: String = "") = CardExpress.report(
        "root=Html(label=\"Visual\",html=${JsonPrimitive(html)},css=${JsonPrimitive(css)})")

    @Test fun acceptsCssTogglesSvgAndComplexBackgrounds() {
        val html = """<input type="radio" id="a" name="mode" checked><label for="a">A</label><section><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor"/></svg><details><summary>Info</summary>Details</details></section>"""
        val css = "section{background:radial-gradient(red,blue);backdrop-filter:blur(12px)}#a:checked~section{color:white}"
        val report = report(html, css)
        assertEquals(report.toString(), JsonPrimitive(true), report["valid"])
        val doc = Jsoup.parse(CardHtmlDocument.document(html, css, "#111111", "#ffffff", "#4488ff", false))
        assertEquals(1, doc.select("input[checked]").size)
        assertEquals(1, doc.select("svg circle").size)
        assertTrue(doc.select("style").html().contains("radial-gradient"))
        assertTrue(doc.select("meta[http-equiv=Content-Security-Policy]").attr("content").contains("default-src 'none'"))
    }

    @Test fun rejectsExecutableContentNavigationAndUnboundedMarkup() {
        for (html in listOf("<script>alert(1)</script>", "<iframe src='file:///x'></iframe>",
            "<div onclick='go()'>x</div>", "<a href='https://example.com'>Go</a>",
            "<form action='https://example.com'><button>Send</button></form>",
            "<input type='text'>", "<meta http-equiv='refresh' content='0;url=https://example.com'>",
            "<svg><use href='file:///x'/></svg>", "<div></div>".repeat(256))) {
            assertEquals(html.take(80), JsonPrimitive(false), report(html)["valid"])
        }
        assertEquals(JsonPrimitive(false), report("")["valid"])
        assertEquals(JsonPrimitive(false), report("<p>x</p>", "a".repeat(8193))["valid"])
        assertEquals(JsonPrimitive(false), CardExpress.report(
            "${'$'}/html=\"<p>x</p>\"\nroot=Html(label=\"x\",html=${'$'}/html)")["valid"])
    }

    @Test fun cssCannotCloseItsStyleElementAndInjectMarkup() {
        val doc = Jsoup.parse(CardHtmlDocument.document("<p>Kept</p>",
            "</style><script>alert(1)</script><style>", "#111111", "#ffffff", "#4488ff", false))
        assertEquals(1, doc.select("script").size)
        assertFalse(doc.select("script").html().contains("alert(1)"))
        assertTrue(doc.select("script").attr("nonce").isNotBlank())
        assertEquals("Kept", doc.body().text())
    }

    @Test fun diagnosticsExplainUnsupportedMarkupAndPagesUseIndependentMeasurementTokens() {
        assertTrue(report("<script>oops</script>")["message"]!!.jsonPrimitive.content.contains("Unsupported Html tag"))
        assertTrue(report("<p onclick='x()'>x</p>")["message"]!!.jsonPrimitive.content.contains("Unsupported Html attribute"))
        assertTrue(report("<div></div>".repeat(256))["message"]!!.jsonPrimitive.content.contains("256 elements"))
        val a = Jsoup.parse(CardHtmlDocument.document("<p>x</p>", "", "#111111", "#ffffff", "#4488ff", false, "page-one"))
        val b = Jsoup.parse(CardHtmlDocument.document("<p>x</p>", "", "#111111", "#ffffff", "#4488ff", false, "page-two"))
        val nonce = a.select("script").attr("nonce")
        assertNotEquals(nonce, b.select("script").attr("nonce"))
        assertTrue(a.select("meta[http-equiv=Content-Security-Policy]").attr("content").contains("'nonce-$nonce'"))
        assertTrue(a.select("script").html().contains("document.body.getBoundingClientRect().height"))
        assertTrue(a.select("script").html().contains("page-one:"))
        assertFalse(a.select("script").html().contains("page-two:"))
    }

    @Test fun repeatedReferencesAlsoRespectWebViewCountLimit() {
        val source = """visual=Html(label="x",html="<p>x</p>")
root=Column(children=[visual,visual,visual,visual,visual])"""
        assertEquals(JsonPrimitive(false), CardExpress.report(source)["valid"])
        assertEquals(JsonPrimitive(false), CardExpress.report(
            """root=Html(label="x",html="<p>x</p>",maxHeight=901)""")["valid"])
    }
}
