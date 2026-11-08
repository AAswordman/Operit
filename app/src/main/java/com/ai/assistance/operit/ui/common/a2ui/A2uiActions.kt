package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.a2ui.model.protocol.A2uiDataPath
import kotlinx.serialization.json.*

/**
 * Operit events carry structured form data and local state replacements. The engine
 * already evaluates nested objects and arrays, so the button schema must accept those
 * same values instead of rejecting them before dispatch. Event names, bounded values
 * and paths are still validated by A2uiProtocol.
 */
internal val cardEventActionProperty = A2uiProperty.nested(
    key = "action",
    required = true,
    isAdditionalPropertiesAllowed = false,
    properties = listOf(
        A2uiProperty.nested(
            key = "event",
            required = true,
            isAdditionalPropertiesAllowed = false,
            properties = listOf(
                A2uiProperty.stringEnum("name", A2uiProtocol.actions.toList(), required = true),
                A2uiProperty.nested(
                    key = "context",
                    properties = emptyList(),
                    description = "Structured event data with nested literals, bindings and calculations.",
                ),
                A2uiProperty.boolean("wantResponse"),
                A2uiProperty.string("responsePath"),
            ),
        ),
    ),
)

/**
 * Local state transitions must read current state at consumption, not the earlier actor snapshot.
 * Caller applies this result immediately, without suspending. External submissions keep their
 * original click snapshot so confirmation and sent data stay identical.
 */
internal fun resolveLocalCardContext(
    surface: A2uiCoreSurfaceModel,
    componentId: String,
    component: JsonObject?,
    fallback: Map<String, Any?>,
    change: Boolean = false,
): Map<String, Any?> {
    val event = (component?.get(if (change) "onChange" else "action") as? JsonObject)?.get("event") as? JsonObject
    if ((event?.get("name") as? JsonPrimitive)?.content != "local_action") return fallback
    val raw = event["context"] ?: return fallback
    val definition = if (change) cardEventValues(raw, fallback) else raw
    val evaluated = surface.evaluatePayload(componentId, { surface.dataModel[it] }, A2uiDataPath("/"), A2uiProtocol.toValue(definition))
        as? Map<*, *> ?: error("Invalid local action")
    return evaluated.entries.associate { (key, value) -> (key as? String ?: error("Invalid local action key")) to value }
        .also { A2uiProtocol.toJson(it) }
}
