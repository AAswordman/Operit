package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.util.UUID

/** Offline visual content: HTML/CSS only, with no app bridge or executable page code. */
internal object CardHtmlDocument {
    private val tags = setOf("div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6",
        "section", "article", "header", "footer", "main", "aside", "nav", "br", "hr",
        "b", "strong", "em", "i", "small", "sup", "sub", "code", "pre", "blockquote",
        "ul", "ol", "li", "dl", "dt", "dd", "table", "thead", "tbody", "tr", "th", "td",
        "details", "summary", "label", "input", "button", "style",
        "svg", "g", "path", "circle", "rect", "line", "polyline", "polygon", "ellipse", "title")
    private val attributes = setOf("id", "class", "style", "title", "role", "for", "type", "name", "value",
        "checked", "disabled", "open", "tabindex", "colspan", "rowspan",
        "viewbox", "width", "height", "d", "fill", "fill-rule", "stroke", "stroke-width",
        "stroke-linecap", "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset",
        "opacity", "fill-opacity", "stroke-opacity", "x", "y", "x1", "y1", "x2", "y2",
        "cx", "cy", "r", "rx", "ry", "points", "transform", "xmlns")

    fun validateDefinition(component: JsonObject) {
        for (name in listOf("html", "css")) {
            val value = component[name]
            require(value == null && name == "css" || value is JsonPrimitive && value.isString) {
                "Html $name must be a literal string"
            }
        }
        fragment(component.getValue("html").jsonPrimitive.content, component["css"]?.jsonPrimitive?.content.orEmpty())
    }

    private fun fragment(html: String, css: String): String {
        require(html.isNotBlank() && html.length <= 8192 && css.length <= 8192) { "Html and CSS must each fit 8192 characters; HTML cannot be empty" }
        val body = Jsoup.parseBodyFragment(html).body()
        require(body.getAllElements().size <= 256) { "Html exceeds 256 elements" }
        for (element in body.getAllElements().drop(1)) {
            require(element.normalName() in tags) { "Unsupported Html tag; use offline HTML/CSS without scripts, frames, images or links" }
            for (attr in element.attributes()) {
                val key = attr.key.lowercase()
                require(key in attributes || key.startsWith("aria-") || key.startsWith("data-")) {
                    "Unsupported Html attribute; event handlers and resource URLs are not supported"
                }
            }
            if (element.normalName() == "input") require(element.attr("type") in setOf("radio", "checkbox")) {
                "Html inputs support only radio/checkbox visual toggles; use native fields for user data"
            }
        }
        return body.html()
    }

    fun document(html: String, css: String, ink: String, surface: String, accent: String, dark: Boolean,
        heightToken: String = UUID.randomUUID().toString()): String {
        val content = fragment(html, css)
        val nonce = UUID.randomUUID().toString()
        require(heightToken.matches(Regex("[a-zA-Z0-9-]+"))) { "Invalid internal height token" }
        return """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$nonce'; style-src 'unsafe-inline'; img-src data:; form-action 'none'; base-uri 'none'">
<style>:root{color-scheme:${if (dark) "dark" else "light"};--operit-text:$ink;--operit-surface:$surface;--operit-accent:$accent}
*{box-sizing:border-box}html,body{margin:0;width:100%;overflow-wrap:anywhere}body{display:flow-root;font:16px/1.5 system-ui,sans-serif;color:var(--operit-text);background:transparent}img,svg{max-width:100%}
${css.replace("<", "\\3c ")}
</style></head><body>$content
<script nonce="$nonce">
(()=>{let last=-1,queued=false;const measure=()=>{queued=false;const height=Math.ceil(document.body.getBoundingClientRect().height);if(height!==last){last=height;document.title='$heightToken:'+height;}};
const schedule=()=>{if(!queued){queued=true;requestAnimationFrame(measure);}};
new ResizeObserver(schedule).observe(document.body);window.addEventListener('load',schedule);schedule();})();
</script></body></html>"""
    }
}
