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
 * Represents a single UI component's payload.
 *
 * @property id The unique identifier of the component.
 * @property type The type or name of the component (e.g., "button", "text").
 * @property properties A map containing the properties and attributes of this component.
 */
public class A2uiComponentPayload(
    public val id: String,
    public val type: String,
    public val properties: Map<String, Any?>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiComponentPayload) return false
        return (id == other.id) && (type == other.type) && (properties == other.properties)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = (31 * result) + type.hashCode()
        result = (31 * result) + properties.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiComponentPayload(id=$id, type=$type, properties=$properties)"
    }
}
