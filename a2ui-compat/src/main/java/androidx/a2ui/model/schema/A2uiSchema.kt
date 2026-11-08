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

package androidx.a2ui.model.schema

import androidx.annotation.RestrictTo
import kotlinx.serialization.json.JsonElement

/** Base node for all schema of A2UI based on JSON Schema. */
public sealed class A2uiSchema {
    /** Semantic description of the schema. */
    public abstract val description: String?

    /** List of JSON Schema keywords applied to this schema node. */
    public open val keywords: List<A2uiSchemaKeyword<Any>>
        get() = emptyList()

    /**
     * Serializes this schema to a JSON Schema string representation.
     *
     * @return the serialized JSON Schema string
     */
    public fun toJsonSchema(): String = toJsonElement().toString()

    /**
     * Returns the [JsonElement] hierarchy representing this schema.
     *
     * @return the [JsonElement] hierarchy representing this schema
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public abstract fun toJsonElement(): JsonElement

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as A2uiSchema
        return description == other.description && keywords == other.keywords
    }

    override fun hashCode(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + keywords.hashCode()
        return result
    }
}
