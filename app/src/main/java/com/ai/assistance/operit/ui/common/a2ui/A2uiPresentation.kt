package com.ai.assistance.operit.ui.common.a2ui

import kotlinx.serialization.json.*

/** Labels come from the same bindings as the outgoing payload, not a second copy of form state. */
internal fun cardSubmissionRows(
    payload: JsonElement,
    definition: JsonElement?,
    components: Collection<JsonObject>,
): List<Pair<String, JsonElement>> {
    val labels = buildMap {
        components.forEach { c ->
            val label = (c["label"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
            listOf("value", "checked", "number").forEach { key ->
                val path = ((c[key] as? JsonObject)?.get("path") as? JsonPrimitive)?.content
                if (path != null) put(path, label)
            }
        }
    }
    val result = mutableListOf<Pair<String, JsonElement>>()
    fun walk(value: JsonElement, schema: JsonElement?, key: String, boundPath: String?) {
        val path = ((schema as? JsonObject)?.get("path") as? JsonPrimitive)?.content ?: boundPath
        when (value) {
            is JsonObject -> if (value.isEmpty()) result += (labels[path] ?: key) to value else value.forEach { (name, child) ->
                walk(child, (schema as? JsonObject)?.get(name), if (key.isEmpty()) name else "$key · $name",
                    path?.trimEnd('/')?.plus("/$name"))
            }
            is JsonArray -> if (value.isEmpty()) result += (labels[path] ?: key) to value else value.forEachIndexed { i, child ->
                walk(child, null, "$key · ${i + 1}", null)
            }
            else -> result += (labels[path] ?: key) to value
        }
    }
    walk(payload, definition, "", null)
    return result
}
