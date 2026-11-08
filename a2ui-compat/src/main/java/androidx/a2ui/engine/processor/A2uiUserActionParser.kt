/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.a2ui.engine.processor

import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.a2ui.model.protocol.A2uiFunctionCallAction
import androidx.a2ui.model.protocol.A2uiUserAction

/**
 * Creates an [A2uiUserAction] from a payload map.
 *
 * Assumes that the payload schema was validated prior to entering the renderer system.
 */
internal fun A2uiUserAction.Companion.fromPayload(
    surfaceId: String,
    componentId: String,
    timestamp: Long,
    payload: Map<String, Any?>,
): A2uiUserAction {
    if (payload.containsKey("event")) {
        @Suppress("UNCHECKED_CAST")
        val eventMap =
            checkNotNull(payload["event"] as? Map<String, Any?>) {
                "Action payload contains 'event' key, but value is not a valid Map."
            }
        val eventName =
            checkNotNull(eventMap["name"] as? String) {
                "Event payload is missing a valid String 'name'."
            }
        @Suppress("UNCHECKED_CAST")
        val context = (eventMap["context"] as? Map<String, Any?>) ?: emptyMap()
        return A2uiEventAction(
            surfaceId = surfaceId,
            componentId = componentId,
            timestamp = timestamp,
            eventName = eventName,
            context = context,
        )
    } else if (payload.containsKey("functionCall")) {
        @Suppress("UNCHECKED_CAST")
        val functionCallMap =
            checkNotNull(payload["functionCall"] as? Map<String, Any?>) {
                "Action payload contains 'functionCall' key, but value is not a valid Map."
            }
        val functionName =
            checkNotNull(functionCallMap["call"] as? String) {
                "FunctionCall payload is missing a valid String 'call'."
            }
        @Suppress("UNCHECKED_CAST")
        val args = (functionCallMap["args"] as? Map<String, Any?>) ?: emptyMap()

        return A2uiFunctionCallAction(
            surfaceId = surfaceId,
            componentId = componentId,
            timestamp = timestamp,
            functionName = functionName,
            args = args,
        )
    } else {
        error("Action payload failed to match either 'event' or 'functionCall' after validation.")
    }
}
