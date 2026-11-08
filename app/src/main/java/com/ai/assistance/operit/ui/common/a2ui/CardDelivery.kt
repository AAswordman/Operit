package com.ai.assistance.operit.ui.common.a2ui

import com.ai.assistance.operit.core.chat.protocol.ChatMarkupRegex
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.util.markdown.MarkdownFence
import java.util.UUID
import com.ai.assistance.operit.ui.common.a2ui.submission.CardSubmissionStore
import kotlinx.serialization.json.*

/** Temporary capabilities: never resolve another run's card or use history as a fallback.
 * Expanded messages contain their initial data; live final data uses a chat-scoped sidecar.
 * This temporary reference store is not needed to render history.
 * Models see short per-run names (card-1, card-2, ...); a 36-character UUID was easy to miscopy.
 * The UUID stays internal because live surfaces and state files are keyed by it across turns.
 */
internal object CardDelivery {
    // 仅标识真实的轮次失效，卡片内容错误由输入校验单独报告。
    class InactiveRunException : IllegalStateException("Card generation is unavailable outside an active chat turn.")

    private data class Revision(val document: String, val source: String, var superseded: Boolean = false) {
        val size get() = document.length + source.length
    }
    private val runs = mutableMapOf<String, MutableMap<String, Revision>>()
    @Synchronized fun begin(run: String) {
        if (runs.size < 64) runs.putIfAbsent(run, linkedMapOf())
    }
    @Synchronized fun end(run: String) {
        runs.remove(run)
        CardLiveStore.end(run)
        CardSubmissionStore.current.end(run)
    }
    @Synchronized fun register(run: String, document: String, source: String = "",
        liveChat: String? = null, liveDirectory: java.io.File? = null, supersedesReference: String? = null,
        waitChat: String? = null, waitTimeoutMillis: Long = 0): String {
        val cards = runs[run] ?: throw InactiveRunException()
        val id = UUID.randomUUID().toString()
        val surface = when {
            waitChat != null -> CardSubmissionStore.surface(id, live = liveChat != null)
            liveChat != null -> CardLiveStore.surface(id)
            else -> null
        }
        val stored = if (surface == null) document else document.lines().filter(String::isNotBlank).joinToString("\n") { line ->
            val event = Json.parseToJsonElement(line).jsonObject
            val kind = event.keys.single { it != "version" }
            val body = event.getValue(kind).jsonObject
            JsonObject(event + (kind to JsonObject(body + ("surfaceId" to JsonPrimitive(surface))))).toString()
        }
        val revision = Revision(stored, source)
        require(cards.size < 16 && cards.values.sumOf { it.size } + revision.size <= 524_288) {
            "Too many cards in this turn."
        }
        require(runs.values.sumOf { map -> map.values.sumOf { it.size } } + revision.size <= 4_194_304) {
            "Card generation is busy. Try again after other tasks finish."
        }
        if (liveChat != null) CardLiveStore.register(run, id, liveChat, requireNotNull(liveDirectory), source, stored)
        if (waitChat != null) CardSubmissionStore.current.register(run, id, waitChat, stored, waitTimeoutMillis)
        cards[id] = revision
        supersedesReference?.let { canonical(run, it) }?.let {
            cards[it]?.superseded = true
            CardSubmissionStore.current.cancel(run, it)
        }
        return "card-${cards.size}"
    }
    /** Maps a model-facing name (or an internal UUID from this run) to the internal UUID. */
    @Synchronized fun canonical(run: String, reference: String): String? {
        val cards = runs[run] ?: return null
        val ref = reference.trim().lowercase()
        if (ref in cards) return ref
        val index = SHORT_NAME.matchEntire(ref)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return cards.keys.elementAtOrNull(index - 1)
    }
    @Synchronized fun resolve(run: String, reference: String): String? {
        val id = canonical(run, reference) ?: return null
        val document = runs[run]?.get(id)?.document ?: return null
        if (!CardLiveStore.publish(run, id)) return null
        return document
    }
    @Synchronized fun source(run: String, reference: String): String? =
        canonical(run, reference)?.let { runs[run]?.get(it)?.source?.takeIf(String::isNotEmpty) }
    /** 从实际保留的正文判断是否已展示，流式回滚不会误消耗投递记录。 */
    @Synchronized fun unplaced(run: String, transcript: String): String {
        val cards = runs[run] ?: return ""
        fun normalized(source: String) = source.lineSequence().filter(String::isNotBlank).joinToString("\n") { it.trim() }
        val displayed = MessageSectionCodec.parse(transcript).filterIsInstance<MessageSection.Text>()
            .flatMap { section -> MarkdownFence.blocks(section.content).filter { it.closed && it.fence.language == "a2ui" }
                .map { normalized(it.body(section.content)) } }.toMutableSet()
        val pending = mutableListOf<String>()
        cards.entries.forEachIndexed { index, (id, revision) ->
            if (!revision.superseded && displayed.add(normalized(revision.document))) {
                if (CardLiveStore.publish(run, id)) pending += "```a2ui\n${revision.document}\n```"
                else pending += "```a2ui-ref\ncard-${index + 1}\n```"
            }
        }
        if (pending.isEmpty()) return ""
        val currentText = MessageSectionCodec.parse(transcript).filterIsInstance<MessageSection.Text>().lastOrNull()?.content.orEmpty()
        val openFence = MarkdownFence.blocks(currentText).lastOrNull()?.takeUnless { it.closed }
        val separator = if (openFence == null) "\n\n" else "\n${openFence.fence.delimiter}\n\n"
        return separator + pending.joinToString("\n\n") + "\n"
    }
    private val SHORT_NAME = Regex("card-(\\d{1,2})")
    val REFERENCE = Regex("[0-9a-f-]{36}|card-\\d{1,2}", RegexOption.IGNORE_CASE)
}

/**
 * Model-facing form of an expanded card. History keeps the full document for rendering, but
 * sending it back each turn costs tokens and shows the model JSON it never wrote. The summary
 * keeps visible text and data so later turns can still discuss the card. Deterministic, so the
 * same message always produces the same prompt prefix.
 */
internal object CardTranscript {
    private const val MAX_SUMMARY = 1200
    private val structural = setOf(
        "version", "surfaceId", "catalogId", "id", "component", "child", "children", "path", "call",
        "action", "event", "context", "columnWidths", "maxHeight", "minItemWidth", "steps", "min", "max",
        "variant", "tone", "icon", "type", "weight", "align", "justify", "css", "key", "template",
        "style", "size", "color", "background", "span", "gap", "padding", "url", "src", "fit", "shape",
    )
    private val markup = Regex("<(style|script)\\b[\\s\\S]*?</\\1\\s*>|<[^>]*>", RegexOption.IGNORE_CASE)

    /** [maxSummary] bounds chat prompts; memory learning passes a larger bound to keep the whole card. */
    fun forModel(content: String, maxSummary: Int = MAX_SUMMARY): String {
        if (!content.contains("a2ui")) return content
        // 使用本分支的消息片段，只压缩正文卡片，保留思考、工具和协议数据。
        return MessageSectionCodec.render(MessageSectionCodec.parse(content).map { section ->
            if (section is MessageSection.Text) section.copy(content = summarizeBlocks(section.content, maxSummary))
            else section
        })
    }

    private fun summarizeBlocks(text: String, maxSummary: Int): String = buildString {
        var offset = 0
        MarkdownFence.blocks(text).forEach { block ->
            append(text.substring(offset, block.start))
            if (block.closed && block.fence.language == "a2ui") {
                append("Historical A2UI summary (context only; do not output this text as a card)")
                append(summary(block.body(text), maxSummary))
            } else append(text.substring(block.start, block.end))
            offset = block.end
            if (block.closed && block.fence.language == "a2ui" && offset > 0 && text[offset - 1] == '\n') append('\n')
        }
        append(text.substring(offset))
    }

    private fun summary(document: String, maxSummary: Int): String {
        val parts = mutableListOf<String>()
        document.lineSequence().filter(String::isNotBlank).forEach { line ->
            val event = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
            event["updateComponents"]?.jsonObject?.get("components")?.jsonArray?.forEach { component ->
                (component as? JsonObject)?.let { flatten(it) }?.takeIf(String::isNotBlank)?.let(parts::add)
            }
            event["updateDataModel"]?.jsonObject?.let { flatten(it) }?.takeIf(String::isNotBlank)?.let(parts::add)
        }
        if (parts.isEmpty()) return ""
        val text = parts.joinToString("; ")
        return ". Visible content: " + if (text.length <= maxSummary) text else text.take(maxSummary) + "…"
    }

    private fun flatten(element: JsonElement): String = when (element) {
        is JsonNull -> ""
        is JsonPrimitive -> if (element.isString || element.booleanOrNull == null) element.content else ""
        is JsonArray -> element.map(::flatten).filter(String::isNotBlank)
            .joinToString(if (element.any { it is JsonArray || it is JsonObject }) " | " else ", ")
        is JsonObject -> element.filterKeys { it !in structural }.map { (key, value) ->
            // Html cards keep their readable text; tags and styles are layout.
            if (key == "html" && value is JsonPrimitive && value.isString)
                value.content.replace(markup, " ").replace(Regex("\\s+"), " ").trim()
            else flatten(value)
        }.filter(String::isNotBlank).joinToString(" ")
    }
}

/** Incremental Markdown reference decoder. Ordinary prose is emitted immediately; only a
 * possible fence header / short reference is buffered. It runs before ALL transcript sinks.
 * An unresolved reference stays as its a2ui-ref fence; the renderer shows a short notice and the
 * model later sees exactly what it wrote.
 */
internal class CardReferenceExpander(private val resolve: (String) -> String?) {
    private data class Snapshot(val pending: String, val reference: String?, val referenceHeader: String,
        val referenceFence: MarkdownFence?, val start: Boolean, val fence: MarkdownFence?,
        val tag: String, val hidden: Int, val expanded: Int)
    private var pending = ""
    private var reference: String? = null
    private var referenceHeader = ""
    private var referenceFence: MarkdownFence? = null
    private var start = true
    private var fence: MarkdownFence? = null
    private var tag = ""
    private var hidden = 0
    private var expanded = 0
    private val snapshots = mutableMapOf<String, Snapshot>()

    fun savepoint(id: String) {
        snapshots[id] = Snapshot(pending, reference, referenceHeader, referenceFence, start, fence, tag, hidden, expanded)
    }
    fun rollback(id: String) {
        val state = snapshots[id] ?: return
        pending = state.pending; reference = state.reference; referenceHeader = state.referenceHeader
        referenceFence = state.referenceFence; start = state.start; fence = state.fence
        tag = state.tag; hidden = state.hidden; expanded = state.expanded
    }
    fun push(chunk: String): String = buildString {
        for (char in chunk) {
            if (reference != null) {
                pending += char
                if (char == '\n') append(referenceLine())
                else if (pending.length > 128) append(abandonReference())
                continue
            }
            if (pending.isNotEmpty() || start && hidden == 0 && char in " \t`~") {
                pending += char
                if (char == '\n') append(fenceLine())
                else {
                    val indent = pending.takeWhile { it == ' ' || it == '\t' }.length
                    val rest = pending.drop(indent)
                    val candidate = indent <= 3 && (rest.isEmpty() || rest.first() in "`~")
                    if (!candidate || pending.length > 256) {
                        if (candidate && fence == null) {
                            val delimiter = rest.takeWhile { it == rest.first() }
                            if (delimiter.length >= 3) fence = MarkdownFence(delimiter, "")
                        }
                        append(ordinary(pending)); pending = ""
                    }
                }
                continue
            }
            append(ordinary(char.toString()))
        }
    }
    private fun ordinary(text: String): String {
        text.forEach { char ->
            if (fence == null) trackMarkup(char)
            start = char == '\n'
        }
        return text
    }
    private fun trackMarkup(char: Char) {
        if (char == '<') tag = "<"
        else if (tag.isNotEmpty()) {
            if (tag.length < 512) tag += char else { tag = ""; return }
            if (char == '>') {
                val match = Regex("^<(/?)([A-Za-z_][A-Za-z0-9_:-]*)(?:\\s[^>]*)?>$").matchEntire(tag)
                if (match != null) {
                    val name = match.groupValues[2].lowercase()
                    val local = name.substringAfterLast(':')
                    if (name in setOf("think", "thinking", "analysis", "reasoning", "tool_results", "tools") ||
                        ChatMarkupRegex.isToolTagName(local) || ChatMarkupRegex.isToolResultTagName(local)) {
                        if (match.groupValues[1] == "/") hidden = (hidden - 1).coerceAtLeast(0)
                        else if (!tag.endsWith("/>")) hidden++
                    }
                }
                tag = ""
            }
        }
    }
    private fun fenceLine(): String {
        val raw = pending
        pending = ""; start = raw.endsWith('\n')
        val line = raw.trimEnd('\r', '\n')
        if (fence == null) {
            val opening = MarkdownFence.parse(line)
            if (opening?.language == "a2ui-ref") {
                reference = ""; referenceHeader = raw; referenceFence = opening
                return ""
            }
            if (opening != null) fence = opening
        } else if (fence!!.closing.matches(line)) fence = null
        return raw
    }
    private fun referenceLine(): String {
        val raw = pending
        pending = ""
        if (referenceFence!!.closing.matches(raw.trimEnd('\r', '\n'))) {
            val original = referenceHeader + reference + raw
            val id = reference!!.trim()
            reference = null; referenceFence = null; start = raw.endsWith('\n')
            val source = if (id.matches(CardDelivery.REFERENCE)) resolve(id) else null
            if (source == null || expanded + source.length > 524_288) return original
            expanded += source.length
            return "```a2ui\n$source\n```" + if (start) "\n" else ""
        }
        reference += raw
        if (reference!!.length > 128) return abandonReference()
        return ""
    }
    private fun abandonReference(): String {
        val raw = referenceHeader + reference + pending
        fence = referenceFence; reference = null; referenceFence = null; pending = ""
        start = raw.endsWith('\n')
        return raw
    }
    fun finish(): String {
        if (reference != null) {
            if (referenceFence!!.closing.matches(pending)) return referenceLine()
            return abandonReference()
        }
        if (pending.isEmpty()) return ""
        val raw = fenceLine()
        return if (reference == null) raw else abandonReference()
    }
}
