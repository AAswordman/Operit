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

package androidx.a2ui.model.protocol

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The unified interface for all messages sent from the A2UI client to the A2A server. */
public sealed interface A2uiClientToServerMessage

/**
 * Represents a client event message to be sent to the server.
 *
 * @property type The type of the action performed (e.g., "click", "swipe").
 * @property surfaceId The unique identifier of the surface where the action occurred.
 * @property componentId The unique identifier of the component that was interacted with.
 * @property timestamp The timestamp of when the action occurred, represented as milliseconds since
 *   the epoch.
 * @property context A map containing custom properties and metadata associated with this action.
 * @property clientDataModel An optional snapshot of active client data models to be synchronized
 *   with the server alongside this event.
 */
public class A2uiClientEventMessage(
    public val type: String,
    public val surfaceId: String,
    public val componentId: String,
    public val timestamp: Long,
    public val context: Map<String, Any?> = emptyMap(),
    public val clientDataModel: A2uiClientDataModel? = null,
) : A2uiClientToServerMessage {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiClientEventMessage) return false
        return (type == other.type) &&
            (surfaceId == other.surfaceId) &&
            (componentId == other.componentId) &&
            (timestamp == other.timestamp) &&
            (context == other.context) &&
            (clientDataModel == other.clientDataModel)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = (31 * result) + surfaceId.hashCode()
        result = (31 * result) + componentId.hashCode()
        result = (31 * result) + timestamp.hashCode()
        result = (31 * result) + context.hashCode()
        result = (31 * result) + (clientDataModel?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val timestampStr = dateFormat.format(Date(timestamp))
        return "A2uiClientEventMessage(type=$type, surfaceId=$surfaceId, " +
            "componentId=$componentId, timestamp=$timestampStr, context=$context, " +
            "clientDataModel=$clientDataModel)"
    }

    private companion object {
        private val dateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}

/**
 * Represents a client-side execution or validation error reported to the server.
 *
 * @property code An error category identifier (such as "VALIDATION_FAILED" or "RUNTIME_ERROR").
 * @property surfaceId The unique ID of the surface where the error occurred.
 * @property message A human-readable description of the error.
 * @property context A map containing custom properties and error context (such as the target path).
 *   For validation errors ("VALIDATION_FAILED"), this map must contain exactly the "path" key
 *   (whose value must be a non-null String) and absolutely nothing else. For other errors, it is
 *   open-ended.
 */
public class A2uiClientErrorMessage(
    public val code: String,
    public val surfaceId: String,
    public val message: String,
    public val context: Map<String, Any?> = emptyMap(),
) : A2uiClientToServerMessage {
    init {
        if (code == "VALIDATION_FAILED") {
            require(context.keys == setOf("path")) {
                "Validation errors must contain exactly the 'path' key in their context, and nothing else."
            }
            require(context["path"] is String) {
                "The 'path' key in validation error context must be a non-null String."
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiClientErrorMessage) return false
        return (code == other.code) &&
            (surfaceId == other.surfaceId) &&
            (message == other.message) &&
            (context == other.context)
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = (31 * result) + surfaceId.hashCode()
        result = (31 * result) + message.hashCode()
        result = (31 * result) + context.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiClientErrorMessage(code=$code, surfaceId=$surfaceId, message=$message, context=$context)"
    }
}
