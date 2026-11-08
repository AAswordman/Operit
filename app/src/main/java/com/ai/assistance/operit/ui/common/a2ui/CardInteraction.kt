package com.ai.assistance.operit.ui.common.a2ui

import androidx.a2ui.compose.runtime.*
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*

internal const val CARD_CHANGE_EVENT = "_operit_change"
internal val LocalCardMutationGuard = compositionLocalOf<(() -> Unit) -> Boolean> { { it(); true } }
internal val LocalCardValueResolver = compositionLocalOf<(String) -> JsonElement> { { JsonNull } }

/** Invoked only by actual input callbacks, never by model observers or replay. */
@Composable
internal fun A2uiComponentScope.cardChangeHandler(
    properties: A2uiComponentProperties, field: String,
): (Any?, Any?, Int, () -> Unit) -> Unit {
    val guard = LocalCardMutationGuard.current
    val resolve = LocalCardValueResolver.current
    val raw = properties[A2uiProperty.any(field)] as? Map<*, *>
    val path = raw?.get("path") as? String
    val disabled = properties[A2uiProperty.any("disabled")]
    return { previous, next, index, update ->
        val blocked = disabled != null && runCatching {
            CardRepeat.evaluate(A2uiProtocol.toJson(disabled), resolve) != JsonPrimitive(false)
        }.getOrDefault(true)
        if (!blocked) applyCardInput(
            current = { if (path != null) A2uiProtocol.toValue(resolve(path)) else previous },
            next = next, guard = guard, update = update,
        ) { actual ->
            dispatchAction(mapOf("event" to mapOf("name" to CARD_CHANGE_EVENT,
                "context" to mapOf("previous" to actual, "value" to next, "index" to index))))
        }
    }
}
internal fun applyCardInput(current: () -> Any?, next: Any?, guard: (() -> Unit) -> Boolean,
    update: () -> Unit, changed: (Any?) -> Unit) {
    val actual = current()
    if (!sameCardInput(actual, next) && guard(update)) changed(actual)
}
internal fun sameCardInput(a: Any?, b: Any?): Boolean =
    if (a is Number && b is Number) a.toDouble() == b.toDouble()
    else A2uiProtocol.toJson(a) == A2uiProtocol.toJson(b)

internal fun cardEventValues(value: JsonElement, context: Map<String, Any?>): JsonElement = when (value) {
    is JsonObject -> if ("event_value" in value) {
        require(value.keys == setOf("event_value"))
        val field = value.getValue("event_value").jsonPrimitive.content
        require(field in setOf("value", "previous", "index"))
        val result = A2uiProtocol.toJson(context[field])
        require(result is JsonPrimitive) { "Event values must be scalar" }
        result
    } else JsonObject(value.mapValues { cardEventValues(it.value, context) })
    is JsonArray -> JsonArray(value.map { cardEventValues(it, context) })
    else -> value
}

internal object CardPressable : A2uiComponent {
    override val name = "Pressable"
    override val description = "Accessible clickable node. Its content contains display components only."
    private val label = A2uiProperty.dynamicString("label")
    private val child = A2uiProperty.dynamicString("child")
    // Operit validates event actions itself, including structured local state values.
    // Read the definition unchanged; its bindings are evaluated only when dispatched.
    private val action = A2uiProperty.any("action")
    private val disabled = A2uiProperty.dynamicBoolean("disabled")
    private val selected = A2uiProperty.dynamicBoolean("selected")
    override val properties = listOf(label, child, action, disabled, selected)
    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val title = properties.bind(label).orEmpty()
        val childId = properties.bind(child) ?: return
        val isSelected = properties.bind(selected) == true
        @Suppress("UNCHECKED_CAST")
        val event = properties[action] as? Map<String, Any?> ?: return
        val local = (event["event"] as? Map<*, *>)?.get("name") == "local_action"
        val enabled = LocalCardBrowse.current && properties.bind(disabled) != true &&
            (!local || !LocalCardReadOnly.current)
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = title) { dispatchAction(event) }
                .semantics(mergeDescendants = true) { contentDescription = title; this.selected = isSelected }
                .padding(12.dp)) {
                CardChild(childId)
            }
        }
    }
}
