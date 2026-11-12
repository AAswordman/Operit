package com.ai.assistance.operit.core.agent.collaboration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Preserve hook metadata types across durable forks rather than flattening everything to text. */
@Serializable
data class CollaborationMetadata(
    val type: String,
    val scalar: String = "",
    val items: List<CollaborationMetadata> = emptyList(),
    val entries: Map<String, CollaborationMetadata> = emptyMap(),
) {
    fun value(): Any? = when (type) {
        "null" -> null
        "string" -> scalar
        "boolean" -> scalar.toBooleanStrict()
        "int" -> scalar.toInt()
        "long" -> scalar.toLong()
        "float" -> scalar.toFloat()
        "double" -> scalar.toDouble()
        "json" -> Json.parseToJsonElement(scalar)
        "list" -> items.map { it.value() }
        "map" -> entries.mapValues { it.value.value() }
        else -> error("Unsupported fork metadata type: $type")
    }

    companion object {
        fun from(value: Any?): CollaborationMetadata = when (value) {
            null -> CollaborationMetadata("null")
            is String -> CollaborationMetadata("string", value)
            is Boolean -> CollaborationMetadata("boolean", value.toString())
            is Int -> CollaborationMetadata("int", value.toString())
            is Long -> CollaborationMetadata("long", value.toString())
            is Float -> CollaborationMetadata("float", value.toString())
            is Double -> CollaborationMetadata("double", value.toString())
            is JsonElement -> CollaborationMetadata("json", value.toString())
            is List<*> -> CollaborationMetadata("list", items = value.map(::from))
            is Map<*, *> -> CollaborationMetadata("map", entries = value.entries.associate {
                require(it.key is String) { "Fork metadata keys must be strings" }
                it.key as String to from(it.value)
            })
            else -> error("Unsupported fork metadata value: ${value.javaClass.simpleName}")
        }
    }
}
