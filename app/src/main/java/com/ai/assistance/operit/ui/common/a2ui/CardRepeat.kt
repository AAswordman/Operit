package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*

/** Lowers read-only item templates to ordinary A2UI components with stable identities. */
internal object CardRepeat {
    fun payloads(graph: Map<String, JsonObject>) = graph.map { (id, c) ->
        androidx.a2ui.model.protocol.A2uiComponentPayload(id, c.getValue("component").jsonPrimitive.content,
            c.filterKeys { it !in setOf("id", "component") }.mapValues { A2uiProtocol.toValue(it.value) })
    }

    fun lowerLines(batch: A2uiProtocol.Batch): List<String> {
        val lowered = batch.componentsBySurface.mapValues { (id, graph) ->
            expand(graph, CardRowState.prepare(graph, initialData(batch.lines, id), CardLiveStore.isLive(id)))
        }
        return batch.lines.map { line ->
            val event = Json.parseToJsonElement(line).jsonObject
            val body = event["updateComponents"] as? JsonObject ?: return@map line
            val id = body.getValue("surfaceId").jsonPrimitive.content
            buildJsonObject {
                put("version", "v0.9")
                put("updateComponents", JsonObject(body + ("components" to JsonArray(lowered.getValue(id).values.toList()))))
            }.toString()
        } + batch.componentsBySurface.mapNotNull { (id, graph) ->
            val data = CardRowState.prepare(graph, initialData(batch.lines, id), CardLiveStore.isLive(id))
            data[CardRowState.ROOT]?.let { rows -> buildJsonObject {
                put("version", "v0.9")
                put("updateDataModel", buildJsonObject {
                    put("surfaceId", id); put("path", "/${CardRowState.ROOT}"); put("value", rows)
                })
            }.toString() }
        }
    }
    fun field(value: JsonElement, path: String): JsonElement {
        require(path.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(/[A-Za-z_][A-Za-z0-9_]*)*")) &&
            path.split('/').none { it in setOf("__proto__", "constructor", "prototype") }) { "Invalid item field" }
        var current = value
        path.split('/').forEach { current = (current as? JsonObject)?.get(it) ?: error("Missing item field: $path") }
        return current
    }

    fun evaluate(value: JsonElement, data: JsonObject): JsonElement =
        evaluate(value) { path -> if (path == "/") data else field(data, path.removePrefix("/")) }

    fun evaluate(value: JsonElement, resolve: (String) -> JsonElement): JsonElement = when (value) {
        is JsonObject -> when {
            value.keys == setOf("path") -> {
                val path = value.getValue("path").jsonPrimitive.content
                resolve(path)
            }
            "call" in value -> {
                val args = value.getValue("args").jsonObject.mapValues { A2uiProtocol.toValue(evaluate(it.value, resolve)) }
                require(args.values.none { it == null }) { "Unresolved Repeat calculation" }
                @Suppress("UNCHECKED_CAST")
                A2uiProtocol.toJson(CardFunctions.evaluate(value.getValue("call").jsonPrimitive.content, args as Map<String, Any>))
            }
            else -> JsonObject(value.mapValues { evaluate(it.value, resolve) })
        }
        is JsonArray -> JsonArray(value.map { evaluate(it, resolve) })
        else -> value
    }

    fun initialData(lines: List<String>, surface: String): JsonObject {
        var data = JsonObject(emptyMap())
        lines.forEach { line ->
            val event = Json.parseToJsonElement(line).jsonObject["updateDataModel"] as? JsonObject
            if (event != null && event["surfaceId"]?.jsonPrimitive?.content == surface)
                data = put(data, event["path"]?.jsonPrimitive?.content ?: "/", event.getValue("value"))
        }
        return data
    }

    fun put(data: JsonObject, path: String, value: JsonElement): JsonObject {
        if (path == "/") return value.jsonObject
        val parts = path.removePrefix("/").split('/')
        fun write(node: JsonObject, index: Int): JsonObject = JsonObject(node + (parts[index] to
            if (index == parts.lastIndex) value else write(node[parts[index]] as? JsonObject ?: JsonObject(emptyMap()), index + 1)))
        return write(data, 0)
    }

    fun expand(graph: Map<String, JsonObject>, data: JsonObject): Map<String, JsonObject> =
        expand(graph, data, dataSource = { data }, onRow = { _, _, _, _, _ -> })

    fun expand(graph: Map<String, JsonObject>, data: JsonObject,
        dataSource: () -> JsonObject = { data },
        onRow: (String, String, JsonObject, String, List<String>) -> Unit,
    ): Map<String, JsonObject> {
        val output = linkedMapOf<String, JsonObject>()
        var visits = 0
        var itemCalls = 0
        fun calls(value: JsonElement): Int = when (value) {
            is JsonObject -> (if ("call" in value) 1 else 0) + value.values.sumOf { calls(it) }
            is JsonArray -> value.sumOf { calls(it) }
            else -> 0
        }
        fun literal(value: JsonElement): JsonElement {
            when (value) {
                is JsonObject -> {
                    require(value.keys.none { it in setOf("path", "call", "item", "item_state", "event_value", "__proto__", "constructor", "prototype") }) {
                        "Item data cannot inject bindings or functions"
                    }
                    value.values.forEach(::literal)
                }
                is JsonArray -> value.forEach(::literal)
                else -> Unit
            }
            return value
        }
        fun substitute(value: JsonElement, item: JsonObject?, scope: String?): JsonElement = when (value) {
            is JsonObject -> if ("item" in value) {
                require(value.keys == setOf("item") && item != null) { "item is only available inside Repeat templates" }
                literal(field(item, value.getValue("item").jsonPrimitive.content))
            } else if ("item_state" in value) {
                require(scope != null) { "item_state is only available inside Repeat templates" }
                buildJsonObject { put("path", CardRowState.path(scope.substringBefore(':'), scope.substringAfter(':'),
                    value.getValue("item_state").jsonPrimitive.content)) }
            } else JsonObject(value.mapValues { substitute(it.value, item, scope) })
            is JsonArray -> JsonArray(value.map { substitute(it, item, scope) })
            else -> value
        }
        fun visit(id: String, scope: String?, item: JsonObject?, depth: Int, parentKeys: List<String>): String {
            require(depth <= 12) { "Cyclic or deeply nested components" }
            require(++visits <= 512) { "Too many rendered components" }
            val resultId = if (scope == null) id else "r_" + A2uiProtocol.fingerprint("$scope:$id").take(48)
            if (resultId in output) return resultId
            val definition = graph[id] ?: error("Missing template component")
            val result = if (definition["component"]?.jsonPrimitive?.content == "Repeat") {
                val resolved = JsonObject(definition + ("items" to substitute(definition.getValue("items"), item, scope)))
                // Repeat disappears during lowering, so count its per-instance work
                // before evaluation rather than losing it from the rendered graph budget.
                itemCalls += calls(resolved.getValue("items"))
                require(itemCalls <= 128) { "Local calculation count exceeds 128" }
                val rows = evaluate(resolved.getValue("items"), dataSource()) as? JsonArray ?: error("Repeat items must be an array")
                val max = definition["maxItems"]?.jsonPrimitive?.int ?: 20
                require(max in 1..32 && rows.size <= max) { "Repeat exceeds maxItems" }
                val keyField = definition.getValue("key").jsonPrimitive.content
                val keys = mutableSetOf<String>()
                val children = rows.map { row ->
                    val obj = row as? JsonObject ?: error("Repeat items must be objects")
                    val key = field(obj, keyField) as? JsonPrimitive ?: error("Repeat key must be a string")
                    require(key.isString && key.content.isNotBlank() && key.content.length <= 80 && keys.add(key.content)) {
                        "Repeat keys must be unique nonempty strings"
                    }
                    onRow(resultId, id, resolved, key.content, parentKeys)
                    JsonPrimitive(visit(definition.getValue("child").jsonPrimitive.content, "$resultId:${key.content}",
                        obj, depth + 1, parentKeys + key.content))
                }
                buildJsonObject { put("id", resultId); put("component", "Column"); put("children", JsonArray(children)) }
            } else JsonObject(definition.mapValues { (name, value) ->
                when (name) {
                    "id" -> JsonPrimitive(resultId)
                    "child" -> JsonPrimitive(visit(value.jsonPrimitive.content, scope, item, depth + 1, parentKeys))
                    "children" -> JsonArray(value.jsonArray.map { JsonPrimitive(visit(it.jsonPrimitive.content, scope, item, depth + 1, parentKeys)) })
                    else -> substitute(value, item, scope)
                }
            })
            require(output.size < 512) { "Too many rendered components" }
            output[resultId] = result
            return resultId
        }
        visit("root", null, null, 0, emptyList())
        require(itemCalls + output.values.sumOf { calls(it) } <= 128) { "Local calculation count exceeds 128" }
        require(JsonArray(output.values.toList()).toString().length <= A2uiProtocol.MAX_SOURCE) { "Expanded Repeat is too large" }
        return output
    }
}
