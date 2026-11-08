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

/** Defines an A2UI component user interaction. */
public sealed interface A2uiUserAction {
    /** unique identifier of the surface where the action occurred */
    public val surfaceId: String
    /** unique identifier of the component that was interacted with */
    public val componentId: String
    /** timestamp of when the action occurred, represented as milliseconds since the epoch */
    public val timestamp: Long

    public companion object
}

/**
 * Defines an event action triggered by a user interaction to be sent to the server.
 *
 * @property surfaceId unique identifier of the surface where the action occurred
 * @property componentId unique identifier of the component that was interacted with
 * @property timestamp timestamp of when the action occurred, represented as milliseconds since the
 *   epoch
 * @property eventName name of the event
 * @property context map containing custom properties and metadata associated with this action
 */
public class A2uiEventAction(
    override val surfaceId: String,
    override val componentId: String,
    override val timestamp: Long,
    public val eventName: String,
    public val context: Map<String, Any?>,
) : A2uiUserAction {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiEventAction) return false
        return (surfaceId == other.surfaceId) &&
            (componentId == other.componentId) &&
            (timestamp == other.timestamp) &&
            (eventName == other.eventName) &&
            (context == other.context)
    }

    override fun hashCode(): Int {
        var result = surfaceId.hashCode()
        result = (31 * result) + componentId.hashCode()
        result = (31 * result) + timestamp.hashCode()
        result = (31 * result) + eventName.hashCode()
        result = (31 * result) + context.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiEventAction(surfaceId=$surfaceId, componentId=$componentId, " +
            "timestamp=$timestamp, eventName=$eventName, context=$context)"
    }
}

/**
 * Defines a local component function execution action triggered by a user interaction.
 *
 * @property surfaceId unique identifier of the surface where the action occurred
 * @property componentId unique identifier of the component that was interacted with
 * @property timestamp timestamp of when the action occurred, represented as milliseconds since the
 *   epoch
 * @property functionName name of the function to execute
 * @property args map containing arguments to pass to the function
 */
public class A2uiFunctionCallAction(
    override val surfaceId: String,
    override val componentId: String,
    override val timestamp: Long,
    public val functionName: String,
    public val args: Map<String, Any?>,
) : A2uiUserAction {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiFunctionCallAction) return false
        return (surfaceId == other.surfaceId) &&
            (componentId == other.componentId) &&
            (timestamp == other.timestamp) &&
            (functionName == other.functionName) &&
            (args == other.args)
    }

    override fun hashCode(): Int {
        var result = surfaceId.hashCode()
        result = (31 * result) + componentId.hashCode()
        result = (31 * result) + timestamp.hashCode()
        result = (31 * result) + functionName.hashCode()
        result = (31 * result) + args.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiFunctionCallAction(surfaceId=$surfaceId, componentId=$componentId, " +
            "timestamp=$timestamp, functionName=$functionName, args=$args)"
    }
}
