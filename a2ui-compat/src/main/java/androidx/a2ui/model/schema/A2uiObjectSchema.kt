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

import androidx.a2ui.model.schema.internal.putCommonKeywords
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Schema representing an object with nested properties. Use this to define the top-level property
 * structure of a component or a nested object argument.
 *
 * @property properties A map of property names to their respective A2uiBaseSchema definitions.
 * @property required A set of property names that must be present in any object implementing this
 *   schema.
 * @property isAdditionalPropertiesAllowed true if properties not defined in [properties] are
 *   allowed
 * @property additionalPropertiesSchema schema definition for additional dynamic properties. Only
 *   applicable if [isAdditionalPropertiesAllowed] is true.
 * @property description semantic description of the schema
 * @property keywords JSON Schema keywords applied to this schema node
 */
@OptIn(ExperimentalSerializationApi::class)
public class A2uiObjectSchema
@JvmOverloads
public constructor(
    public val properties: Map<String, A2uiSchema> = emptyMap(),
    public val required: Set<String> = emptySet(),
    public val isAdditionalPropertiesAllowed: Boolean = true,
    public val additionalPropertiesSchema: A2uiSchema? = null,
    public override val description: String? = null,
    public override val keywords: List<A2uiSchemaKeyword<Map<String, Any>>> = emptyList(),
) : A2uiSchema() {
    init {
        val missingKeys = required.filter { !properties.containsKey(it) }
        require(missingKeys.isEmpty()) {
            "All required keys must be present in properties. Missing keys: $missingKeys"
        }
    }

    override fun toJsonElement(): JsonElement = buildJsonObject {
        put(KEY_TYPE, TYPE_OBJECT)
        put(KEY_PROPERTIES, JsonObject(properties.mapValues { it.value.toJsonElement() }))
        if (required.isNotEmpty()) {
            put(KEY_REQUIRED, JsonArray(required.map { JsonPrimitive(it) }))
        }
        if (!isAdditionalPropertiesAllowed) {
            put(KEY_ADDITIONAL_PROPERTIES, false)
        } else if (additionalPropertiesSchema != null) {
            put(KEY_ADDITIONAL_PROPERTIES, additionalPropertiesSchema.toJsonElement())
        }
        putCommonKeywords(this@A2uiObjectSchema)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false
        other as A2uiObjectSchema
        return properties == other.properties &&
            required == other.required &&
            isAdditionalPropertiesAllowed == other.isAdditionalPropertiesAllowed &&
            additionalPropertiesSchema == other.additionalPropertiesSchema
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + isAdditionalPropertiesAllowed.hashCode()
        result = 31 * result + (additionalPropertiesSchema?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Object(description=$description)"
    }

    public companion object {
        @JvmField public val INSTANCE: A2uiObjectSchema = A2uiObjectSchema()

        internal const val KEY_PROPERTIES = "properties"
        internal const val KEY_REQUIRED = "required"
        internal const val KEY_ADDITIONAL_PROPERTIES = "additionalProperties"
        internal const val TYPE_OBJECT = "object"
    }
}
