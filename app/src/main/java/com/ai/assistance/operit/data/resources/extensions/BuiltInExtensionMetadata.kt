package com.ai.assistance.operit.data.resources.extensions

import android.content.Context
import com.ai.assistance.operit.core.tools.LocalizedText
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

/** Declarative metadata only: reading descriptions never loads or executes a plugin. */
internal object BuiltInExtensionMetadata {
    fun descriptions(context: Context): Map<String, LocalizedText> {
        val source = context.assets.open("offline-extension-metadata.json").bufferedReader().use { it.readText() }
        return decodeDescriptions(source)
    }

    internal fun decodeDescriptions(source: String): Map<String, LocalizedText> {
        val metadata = Json.decodeFromString<Map<String, String>>(source)
        return metadata.mapValues { (_, text) ->
            val description = requireNotNull(JsonValue.readHjson(text).asObject().get("description")) {
                "Built-in extension metadata must declare a description"
            }
            Json.decodeFromString<LocalizedText>(description.toString())
        }
    }
}
