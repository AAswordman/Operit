package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*

/** Per-item user data uses stable identities, never array positions or Agent-owned fields. */
internal object CardRowState {
    const val ROOT = "_operit_rows"
    private val fieldPattern = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
    fun path(repeat: String, key: String, field: String): String {
        require(fieldPattern.matches(field)) { "Invalid row state field" }
        return "/$ROOT/$repeat/k${A2uiProtocol.fingerprint(key).take(40)}/$field"
    }
    fun placeholder(field: String, default: JsonElement): JsonObject {
        require(fieldPattern.matches(field) && default is JsonPrimitive && default != JsonNull) {
            "Row state requires a named field and scalar default"
        }
        return buildJsonObject { put("item_state", field); put("default", default) }
    }
    fun declarations(graph: Map<String, JsonObject>, repeat: JsonObject): Map<String, JsonElement> {
        val fields = linkedMapOf<String, JsonElement>()
        fun scan(value: JsonElement) {
            when (value) {
                is JsonObject -> if ("item_state" in value) {
                    require(value.keys == setOf("item_state", "default"))
                    val field = value.getValue("item_state").jsonPrimitive.content
                    val default = value.getValue("default")
                    placeholder(field, default)
                    require(field !in fields || fields[field] == default) { "Conflicting row state defaults" }
                    fields[field] = default
                } else value.values.forEach(::scan)
                is JsonArray -> value.forEach(::scan)
                else -> Unit
            }
        }
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (!visited.add(id)) return
            val c = graph[id] ?: error("Missing template component")
            if (c["component"]?.jsonPrimitive?.content == "Repeat") {
                // Its items belongs to this scope; its template belongs to a new scope.
                scan(c.getValue("items"))
                return
            }
            scan(c)
            c["child"]?.jsonPrimitive?.content?.let(::visit)
            (c["children"] as? JsonArray)?.forEach { visit(it.jsonPrimitive.content) }
        }
        visit(repeat.getValue("child").jsonPrimitive.content)
        return fields
    }
    fun rows(repeat: JsonObject, data: JsonObject): List<Pair<String, JsonObject>> {
        val rows = CardRepeat.evaluate(repeat.getValue("items"), data) as? JsonArray ?: error("Repeat items must be an array")
        val max = repeat["maxItems"]?.jsonPrimitive?.int ?: 20
        require(max in 1..32 && rows.size <= max) { "Repeat exceeds maxItems" }
        val keys = mutableSetOf<String>()
        return rows.map {
            val row = it as? JsonObject ?: error("Repeat items must be objects")
            val key = CardRepeat.field(row, repeat.getValue("key").jsonPrimitive.content) as? JsonPrimitive
            require(key != null && key.isString && key.content.isNotBlank() && key.content.length <= 80 &&
                keys.add(key.content)) { "Repeat keys must be unique nonempty strings" }
            key.content to row
        }
    }
    private fun sameType(a: JsonElement, b: JsonElement): Boolean =
        a is JsonPrimitive && b is JsonPrimitive && a != JsonNull && b != JsonNull &&
            a.isString == b.isString && (a.isString || (a.booleanOrNull != null) == (b.booleanOrNull != null))

    fun userWrite(data: JsonObject, path: String, value: JsonElement): JsonObject {
        A2uiProtocol.validatePath(path)
        require(path.split('/').none { it == ROOT }) { "Reserved row state" }
        if (path != "/") return CardRepeat.put(data, path, value)
        val root = value as? JsonObject ?: error("Root state must be an object")
        require(ROOT !in root) { "Reserved row state" }
        return JsonObject(root + data.filterKeys { it == ROOT })
    }

    fun prepare(graph: Map<String, JsonObject>, data: JsonObject, live: Boolean = false): JsonObject {
        var next = data
        val declarations = graph.filterValues { it["component"]?.jsonPrimitive?.content == "Repeat" }
            .mapValues { declarations(graph, it.value) }
        CardRepeat.expand(graph, data, dataSource = { next }) { id, template, _, key, _ ->
                val fields = declarations.getValue(template)
                fields.forEach { (field, default) ->
                    val path = path(id, key, field)
                    val existing = runCatching { CardRepeat.field(next, path.drop(1)) }.getOrNull()
                    require(existing == null || sameType(existing, default)) { "Invalid row state type" }
                    if (existing == null) next = CardRepeat.put(next, path, default)
                }
        }
        // Includes hidden rows: filters must not silently erase user edits.
        A2uiProtocol.toJson(A2uiProtocol.toValue(next))
        require((if (live) CardLiveStore.userData(next) else next).toString().length <= A2uiProtocol.MAX_STATE) { "Data model too large" }
        return next
    }
    fun set(graph: Map<String, JsonObject>, data: JsonObject, repeatId: String, key: String,
        field: String, value: JsonElement, live: Boolean = false, parents: List<String> = emptyList()): JsonObject {
        val repeat = graph[repeatId]?.takeIf { it["component"]?.jsonPrimitive?.content == "Repeat" }
            ?: error("Unknown Repeat")
        val matches = mutableSetOf<String>()
        CardRepeat.expand(graph, data) { instance, template, _, rowKey, parentKeys ->
            if (template == repeatId && key == rowKey && parentKeys == parents) matches += instance
        }
        require(matches.size == 1) { "Row no longer exists or scope is ambiguous" }
        val default = declarations(graph, repeat)[field] ?: error("Undeclared row state field")
        require(sameType(value, default)) { "Invalid row state type" }
        return prepare(graph, CardRepeat.put(data, path(matches.single(), key, field), value), live)
    }
}
