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

import androidx.a2ui.model.schema.internal.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents a JSON Schema (Draft 2020-12) keyword applied to a schema node of type [T].
 *
 * Keywords include subschema applicators (`oneOf`, `allOf`, `anyOf`, `not`, conditional
 * `if-then-else`), annotations (`default`), and validation constraints (`enum`, `const`, `format`).
 */
public sealed class A2uiSchemaKeyword<out T> {
    internal abstract fun addToJsonObject(builder: JsonObjectBuilder)

    /**
     * Subschemas where the instance must match exactly one (`oneOf`).
     *
     * Uses [Nothing] as the type parameter so that covariance (`out T`) allows this keyword to be
     * used in any typed schema's keyword list.
     *
     * @property schemas list of subschemas to validate against
     */
    public class OneOf(public val schemas: List<A2uiSchema>) : A2uiSchemaKeyword<Nothing>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, JsonArray(schemas.map { it.toJsonElement() }))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is OneOf && schemas == other.schemas
        }

        override fun hashCode(): Int = schemas.hashCode()

        override fun toString(): String = "OneOf($schemas)"

        public companion object {
            private const val KEY = "oneOf"
        }
    }

    /**
     * Subschemas where the instance must match all (`allOf`).
     *
     * Uses [Nothing] as the type parameter so that covariance (`out T`) allows this keyword to be
     * used in any typed schema's keyword list.
     *
     * @property schemas list of subschemas to validate against
     */
    public class AllOf(public val schemas: List<A2uiSchema>) : A2uiSchemaKeyword<Nothing>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, JsonArray(schemas.map { it.toJsonElement() }))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is AllOf && schemas == other.schemas
        }

        override fun hashCode(): Int = schemas.hashCode()

        override fun toString(): String = "AllOf($schemas)"

        public companion object {
            private const val KEY = "allOf"
        }
    }

    /**
     * Subschemas where the instance must match at least one (`anyOf`).
     *
     * Uses [Nothing] as the type parameter so that covariance (`out T`) allows this keyword to be
     * used in any typed schema's keyword list.
     *
     * @property schemas list of subschemas to validate against
     */
    public class AnyOf(public val schemas: List<A2uiSchema>) : A2uiSchemaKeyword<Nothing>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, JsonArray(schemas.map { it.toJsonElement() }))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is AnyOf && schemas == other.schemas
        }

        override fun hashCode(): Int = schemas.hashCode()

        override fun toString(): String = "AnyOf($schemas)"

        public companion object {
            private const val KEY = "anyOf"
        }
    }

    /**
     * Subschema where the instance must NOT match (`not`).
     *
     * Uses [Nothing] as the type parameter so that covariance (`out T`) allows this keyword to be
     * used in any typed schema's keyword list.
     *
     * @property schema subschema that the instance must not match
     */
    public class Not(public val schema: A2uiSchema) : A2uiSchemaKeyword<Nothing>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, schema.toJsonElement())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Not && schema == other.schema
        }

        override fun hashCode(): Int = schema.hashCode()

        override fun toString(): String = "Not($schema)"

        public companion object {
            private const val KEY = "not"
        }
    }

    /**
     * Default value for the schema property (`default`).
     *
     * @property value default value for the property
     */
    public class Default<out T>(public val value: T) : A2uiSchemaKeyword<T>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, value.toJsonElement())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Default<*> && value == other.value
        }

        override fun hashCode(): Int = value?.hashCode() ?: 0

        override fun toString(): String = "Default($value)"

        public companion object {
            private const val KEY = "default"
        }
    }

    /**
     * Allowed enumeration values for the schema property (`enum`).
     *
     * @property values list of allowed enumeration values
     */
    public class Enum<out T>(public val values: List<T>) : A2uiSchemaKeyword<T>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, JsonArray(values.map { it.toJsonElement() }))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Enum<*> && values == other.values
        }

        override fun hashCode(): Int = values.hashCode()

        override fun toString(): String = "Enum($values)"

        public companion object {
            private const val KEY = "enum"
        }
    }

    /**
     * Constant value constraint (`const`).
     *
     * @property value constant value required for the property
     */
    public class Const<out T>(public val value: T) : A2uiSchemaKeyword<T>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, value.toJsonElement())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Const<*> && value == other.value
        }

        override fun hashCode(): Int = value?.hashCode() ?: 0

        override fun toString(): String = "Const($value)"

        public companion object {
            private const val KEY = "const"
        }
    }

    /**
     * String format constraint (`format`).
     *
     * Supported formats: `"date"`, `"time"`, and `"date-time"`.
     *
     * @property format format constraint name
     */
    public class Format(public val format: String) : A2uiSchemaKeyword<String>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY, JsonPrimitive(format))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Format && format == other.format
        }

        override fun hashCode(): Int = format.hashCode()

        override fun toString(): String = "Format($format)"

        public companion object {
            private const val KEY = "format"
        }
    }

    /**
     * Conditional subschema applicator (`if`-`then`-`else`).
     *
     * Note: Standard JSON Schema Draft 2020-12 permits only one `if`-`then`-`else` block per schema
     * node. To apply multiple conditional rules to a single node, wrap multiple [IfThen] keywords
     * inside an [AllOf] list.
     *
     * @property ifSchema condition subschema
     * @property thenSchema subschema evaluated if [ifSchema] matches
     * @property elseSchema optional subschema evaluated if [ifSchema] fails
     */
    public class IfThen(
        public val ifSchema: A2uiSchema,
        public val thenSchema: A2uiSchema,
        public val elseSchema: A2uiSchema? = null,
    ) : A2uiSchemaKeyword<Nothing>() {
        override fun addToJsonObject(builder: JsonObjectBuilder) {
            builder.put(KEY_IF, ifSchema.toJsonElement())
            builder.put(KEY_THEN, thenSchema.toJsonElement())
            if (elseSchema != null) {
                builder.put(KEY_ELSE, elseSchema.toJsonElement())
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is IfThen &&
                ifSchema == other.ifSchema &&
                thenSchema == other.thenSchema &&
                elseSchema == other.elseSchema
        }

        override fun hashCode(): Int {
            var result = ifSchema.hashCode()
            result = 31 * result + thenSchema.hashCode()
            result = 31 * result + (elseSchema?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String = "IfThen(if=$ifSchema, then=$thenSchema, else=$elseSchema)"

        public companion object {
            private const val KEY_IF = "if"
            private const val KEY_THEN = "then"
            private const val KEY_ELSE = "else"
        }
    }
}
