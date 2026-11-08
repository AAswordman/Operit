package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.theme.script.*
import kotlinx.serialization.json.*

/** Parameter codes carry appearance values, while resource identities stay with the receiver. */
internal object ThemeAppearanceParameters {
    private const val IMAGE = "sharedParameterImage"
    private val json = Json(ThemeScriptContract.json) { encodeDefaults = false }

    fun export(appearance: ThemeScriptAppearance): JsonObject =
        withoutBindings(json.encodeToJsonElement(ThemeScriptAppearance.serializer(), appearance)).jsonObject

    fun validate(parameters: JsonObject) {
        require(withoutBindings(parameters) == parameters) { "Appearance parameters cannot bind resources" }
        val appearance = decode(parameters)
        ThemeScriptContract.validate(
            appearance, ThemeScriptManifest(1, "assets/main.js", mapOf(IMAGE to "assets/parameter.png")),
        )
    }

    fun merge(current: ThemeScriptAppearance, parameters: JsonObject): ThemeScriptAppearance {
        validate(parameters)
        val incoming = decode(parameters)
        return current.copy(
            surfaces = current.surfaces + incoming.surfaces.mapValues { (id, surface) ->
                mergeSurface(current.surfaces[id] ?: ThemeScriptSurface(), surface)
            },
            icons = icons(current.icons, incoming.icons),
            palette = incoming.palette,
            typography = text(current.typography, incoming.typography),
            modes = current.modes + incoming.modes.mapValues { (mode, variant) ->
                val before = current.forMode(mode)
                val overrides = current.modes[mode] ?: ThemeAppearanceMode()
                overrides.copy(
                    surfaces = overrides.surfaces + variant.surfaces.mapValues { (id, surface) ->
                        mergeSurface(before.surfaces[id] ?: ThemeScriptSurface(), surface)
                    },
                    icons = overrides.icons + icons(before.icons, variant.icons).filterKeys { it in variant.icons },
                    typography = variant.typography?.let { text(before.typography, it) } ?: overrides.typography,
                )
            },
        )
    }

    private fun text(current: ThemeAppearanceText, incoming: ThemeAppearanceText): ThemeAppearanceText =
        incoming.copy(family = if (current.family?.startsWith("asset:") == true) current.family else incoming.family)

    private fun icons(
        current: Map<String, ThemeAppearanceIcon>,
        incoming: Map<String, ThemeAppearanceIcon>,
    ): Map<String, ThemeAppearanceIcon> = current + incoming.mapNotNull { (id, icon) ->
        current[id]?.let { id to icon.copy(asset = it.asset) }
    }.toMap()

    private fun mergeSurface(current: ThemeScriptSurface, incoming: ThemeScriptSurface): ThemeScriptSurface {
        val bound = mutableSetOf<Int>()
        val layers = incoming.layers.mapIndexedNotNull { index, layer ->
            if (layer.asset == null) {
                // Keep resource layer IDs stable when an unrelated paint layer uses the same name.
                if (layer.id != null && current.layers.any { it.asset != null && it.id == layer.id }) layer.copy(id = null)
                else layer
            } else {
                // Named layers match by ID; unnamed layers match by their position in the surface.
                val target = if (layer.id != null) current.layers.indexOfFirst { it.id == layer.id }
                    else index.takeIf { it in current.layers.indices } ?: -1
                val asset = current.layers.getOrNull(target)?.asset
                if (asset == null || !bound.add(target)) null else layer.copy(asset = asset, id = current.layers[target].id)
            }
        } + current.layers.filterIndexed { index, layer -> layer.asset != null && index !in bound }
        return incoming.copy(layers = layers, textStyle = text(current.textStyle, incoming.textStyle))
    }

    private fun withoutBindings(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterNot { (key, value) ->
            key == "asset" || key == "family" && (value as? JsonPrimitive)?.contentOrNull?.startsWith("asset:") == true
        }.mapValues { withoutBindings(it.value) })
        is JsonArray -> JsonArray(element.map(::withoutBindings))
        else -> element
    }

    /** Synthetic IDs validate every field even when the receiver has no matching image or icon. */
    private fun decode(parameters: JsonObject): ThemeScriptAppearance =
        json.decodeFromJsonElement(ThemeScriptAppearance.serializer(), validationBindings(parameters).jsonObject)

    private fun validationBindings(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            val fields = element.mapValues { (key, value) ->
                if (key == "icons") JsonObject(value.jsonObject.mapValues { (_, icon) ->
                    JsonObject(icon.jsonObject + ("asset" to JsonPrimitive(IMAGE)))
                }) else validationBindings(value)
            }.toMutableMap()
            if ((fields["type"] as? JsonPrimitive)?.contentOrNull in setOf("image", "nineSlice")) {
                fields["asset"] = JsonPrimitive(IMAGE)
            }
            JsonObject(fields)
        }
        is JsonArray -> JsonArray(element.map(::validationBindings))
        else -> element
    }
}
