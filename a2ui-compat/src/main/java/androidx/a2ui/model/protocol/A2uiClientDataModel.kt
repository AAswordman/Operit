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

/**
 * Represents the client data model synchronization payload sent to the agent.
 *
 * This payload captures the current state of the data tree for active surfaces with `sendDataModel
 * = true` and is automatically attached to outbound client event messages.
 *
 * @property surfaces A map where keys are surface IDs and values are the complete, current data
 *   model root for that surface.
 */
public class A2uiClientDataModel(public val surfaces: Map<String, Any?>) {
    /** Converts this data model object to the A2UI JSON-compatible Map structure. */
    public fun toPayloadMap(): Map<String, Map<String, Any?>> {
        val versionPayload = buildMap<String, Any?> { put("surfaces", surfaces) }
        return mapOf(
            "a2uiClientDataModel" to mapOf(A2uiProtocolConstants.PROTOCOL_VERSION to versionPayload)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiClientDataModel) return false
        return surfaces == other.surfaces
    }

    override fun hashCode(): Int {
        return surfaces.hashCode()
    }

    override fun toString(): String {
        return "A2uiClientDataModel(surfaces=$surfaces)"
    }
}
