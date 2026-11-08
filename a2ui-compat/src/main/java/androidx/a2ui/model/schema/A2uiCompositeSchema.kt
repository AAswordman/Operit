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

import kotlinx.serialization.json.JsonElement

/**
 * Schema node that is defined by using other schema nodes.
 *
 * Subclasses of this abstract class define a schema using [A2uiSchema] components returned by
 * [getDefinition]. Supports reusable definitions in the `$defs` section of the generated JSON
 * Schema when [definitionName] is specified.
 */
public abstract class A2uiCompositeSchema : A2uiSchema() {
    /**
     * Name of this schema within the `$defs` section of the generated JSON Schema.
     *
     * If non-null, nested references to this schema will generate a `$ref` pointing to this
     * definition name instead of inlining the full schema. Use this to avoid duplicating complex
     * schemas in components.
     */
    public open val definitionName: String? = null

    /**
     * Root schema ID used to reference this sub-schema.
     *
     * Non-null values prepend this ID to the `$ref` path when serializing this schema.
     * * Format when non-null: `"$schemaId#/$defs/$definitionName"`
     * * Format when null: `"#/$defs/$definitionName"`
     *
     * Note: This has no effect if [definitionName] is null.
     */
    public open val schemaId: String? = null

    /**
     * Returns the underlying schema definition.
     *
     * Concrete subclasses must implement this to return the actual schema details that this schema
     * wraps.
     *
     * @return the underlying schema definition
     */
    public abstract fun getDefinition(): A2uiSchema

    override val keywords: List<A2uiSchemaKeyword<Any>> = emptyList()

    /**
     * Returns the serialized JSON Schema of the underlying definition without referencing.
     *
     * This ignores [definitionName] and [schemaId], serializing the full unwrapped schema directly.
     *
     * @return the serialized JSON Schema string of the definition
     */
    internal fun getDefinitionJsonSchema(): String = getDefinitionJsonElement().toString()

    internal fun getDefinitionJsonElement(): JsonElement = getDefinition().toJsonElement()

    /**
     * Serializes this schema to a JSON Schema string representation.
     *
     * If [definitionName] is non-null, this generates a `$ref` schema pointing to the definition
     * instead of the full schema. Otherwise, it delegates to [getDefinitionJsonSchema].
     *
     * @return the serialized JSON Schema string
     */
    override fun toJsonElement(): JsonElement {
        return if (definitionName != null) {
            val ref =
                if (schemaId != null) {
                    "$schemaId#/\$defs/$definitionName"
                } else {
                    "#/\$defs/$definitionName"
                }
            A2uiRefSchema(ref, description).toJsonElement()
        } else {
            getDefinitionJsonElement()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false

        other as A2uiCompositeSchema
        return definitionName == other.definitionName && schemaId == other.schemaId
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (definitionName?.hashCode() ?: 0)
        result = 31 * result + (schemaId?.hashCode() ?: 0)
        return result
    }
}
