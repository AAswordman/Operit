package com.ai.assistance.operit.ui.common.a2ui

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

/** Current-run write capabilities. Historical cards can only read the final chat-scoped snapshot. */
internal object CardLiveStore {
    private data class Entry(val run: String, val reference: String, val chat: String, val source: String, val fingerprint: String,
        val file: File, val state: MutableStateFlow<JsonObject>, var published: Boolean = false, var failed: Boolean = false)
    private val entries = mutableMapOf<String, Entry>()

    fun isLive(surface: String) = surface.matches(Regex("(live|await_live)_[0-9a-f]{32}"))
    fun surface(reference: String) = "live_${reference.replace("-", "")}"
    private fun fingerprint(document: String) = A2uiProtocol.fingerprint(document.trimEnd('\r', '\n'))
    fun userData(data: JsonObject) = JsonObject(data.filterKeys { it != "agent" })
    fun protectedPath(path: String) = path == "/" || path == "/agent" || path.startsWith("/agent/")

    fun validate(source: String, data: JsonObject) {
        require(data.toString().length <= 8192) { "Agent data exceeds 8K characters" }
        CardExpress.revise(source, "$/agent = $data", emptyList())
    }

    @Synchronized fun register(run: String, reference: String, chat: String, directory: File,
        source: String, document: String) {
        val batch = A2uiProtocol.parse(document, true)
        val id = batch.surfaceIds.single()
        require(isLive(id))
        // 缺少 Agent 数据属于源码输入错误，不能误报为聊天轮次失效。
        val data = requireNotNull(CardRepeat.initialData(batch.lines, id)["agent"] as? JsonObject) {
            "Live source must initialize $/agent as an object, for example $/agent={progress:10}."
        }
        validate(source, data)
        // User controls and local transitions must never target the Agent-owned branch.
        batch.componentsBySurface.values.flatMap { it.values }.forEach { c ->
            val kind = c.getValue("component").jsonPrimitive.content
            val writable = when (kind) {
                "Slider" -> "number"; "Switch" -> "checked"; "Expandable" -> "expanded"
                "TextField", "TextArea", "CheckBox", "RadioGroup", "Select", "ChoicePicker",
                "DatePicker", "Segmented", "Tabs", "Carousel", "Draft" -> "value"
                else -> null
            }
            val path = (writable?.let { c[it] } as? JsonObject)?.get("path")?.jsonPrimitive?.content
            require(path == null || !protectedPath(path)) { "User controls cannot write Agent data" }
        }
        val fingerprint = fingerprint(document)
        entries[id] = Entry(run, reference, chat, source, fingerprint, File(directory, "$id.json"),
            MutableStateFlow(record(fingerprint, data, 0, true)))
    }

    private fun record(fingerprint: String, data: JsonObject, version: Int, active: Boolean) =
        buildJsonObject { put("fingerprint", fingerprint); put("agent", data); put("version", version); put("active", active) }

    @Synchronized fun publish(run: String, reference: String): Boolean {
        val e = entries.values.singleOrNull { it.run == run && it.reference == reference } ?: return true
        if (e.failed) return false
        if (!e.published) {
            try {
                A2uiStateStore.writeNow(e.file, e.state.value)
                e.published = true
            } catch (_: Exception) {
                // A full disk must not abort the enclosing model stream.
                e.failed = true
                e.state.value = JsonObject(e.state.value + ("error" to JsonPrimitive(true)))
            }
        }
        return !e.failed
    }

    @Synchronized fun patch(run: String, reference: String, expected: Int, data: JsonObject): Int {
        val e = entries.values.singleOrNull { it.run == run && it.reference == reference } ?: error("No live card in this turn")
        check(!e.failed) { "Live data could not be saved; prepare a new card after checking storage" }
        val version = e.state.value.getValue("version").jsonPrimitive.int
        require(expected == version) { "Card data version conflict; current version=$version" }
        validate(e.source, data)
        val next = record(e.fingerprint, data, version + 1, true)
        if (e.published) A2uiStateStore.writeNow(e.file, next)
        e.state.value = next
        return version + 1
    }

    @Synchronized fun observe(chat: String, document: String, id: String): StateFlow<JsonObject>? =
        entries[id]?.takeIf { it.chat == chat && it.fingerprint == fingerprint(document) }?.state

    @Synchronized fun end(run: String) {
        val retiring = entries.filterValues { it.run == run }
        retiring.forEach { (id, e) ->
            val frozen = JsonObject(e.state.value + ("active" to JsonPrimitive(false)))
            if (e.published) runCatching { A2uiStateStore.writeNow(e.file, frozen) }
            e.state.value = frozen
            entries.remove(id)
        }
    }

    suspend fun read(directory: File, document: String, id: String): JsonObject? {
        if (!isLive(id)) return null
        return A2uiStateStore.read(File(directory, "$id.json"))?.takeIf {
            it["fingerprint"]?.jsonPrimitive?.content == fingerprint(document)
        }
    }
}
