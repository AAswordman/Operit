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

package androidx.a2ui.compose.runtime

import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiBooleanSchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiActionSchema
import androidx.a2ui.model.schema.commontypes.A2uiChildListSchema
import androidx.a2ui.model.schema.commontypes.A2uiComponentIdSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicBooleanSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicNumberSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringListSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDynamicValueSchema
import androidx.annotation.IntRange
import androidx.collection.DoubleList
import androidx.collection.FloatList
import androidx.collection.IntList
import androidx.collection.LongList
import androidx.compose.runtime.Immutable
import java.lang.StringBuilder

/**
 * A typed declaration of a property expected by an A2UI component.
 *
 * `A2uiProperty` serves two primary purposes:
 * 1. **Schema Generation**: It automatically constructs the underlying [A2uiSchema] used to
 *    communicate the component's structural requirements to the agent.
 * 2. **Typed Resolution**: It acts as a token utilized by [A2uiComponentProperties] and
 *    [A2uiComponentScope] to safely extract, type-cast, and optionally evaluate dynamic bindings
 *    from incoming protocol payloads at runtime.
 *
 * Properties are divided into specific subtypes based on their runtime evaluation behavior:
 * - [StaticA2uiProperty]: Read synchronously directly from the component properties.
 * - [DynamicA2uiProperty]: Evaluated reactively against the surface's data model.
 * - [ChildListA2uiProperty]: Used to resolve structural hierarchies of nested components.
 *
 * The property subtypes listed above are used in the APIs provided by [A2uiComponentScope] to
 * ensure that only the allowed property types are used with each of the relevant APIs.
 *
 * @param T The expected type of the resolved property value at runtime.
 */
@Immutable
public sealed class A2uiProperty<out T> {

    /** The key of the property as it appears in the A2UI protocol payload. */
    public abstract val key: String

    /** The underlying [A2uiSchema] definition describing this property to the agent. */
    public abstract val schema: A2uiSchema

    /** Indicates whether the agent is strictly required to provide this property. */
    public abstract val isRequired: Boolean

    public companion object {

        /**
         * Creates a static property representing a nested property object.
         *
         * @param key The key for this property.
         * @param properties A list of typed properties defining the nested object.
         * @param required Whether the agent must provide this property.
         * @param description An optional string explaining this property's purpose.
         * @param isAdditionalPropertiesAllowed Whether properties not explicitly listed in
         *   [properties] are allowed, defaults to true.
         * @param additionalPropertiesSchema Schema definition for additional dynamic properties,
         *   only applicable if [isAdditionalPropertiesAllowed] is true.
         * @return A [StaticA2uiProperty] resolving to [A2uiComponentProperties] at runtime.
         */
        public fun nested(
            key: String,
            properties: List<A2uiProperty<*>>,
            required: Boolean = false,
            description: String? = null,
            isAdditionalPropertiesAllowed: Boolean = true,
            additionalPropertiesSchema: A2uiSchema? = null,
        ): StaticA2uiProperty<A2uiComponentProperties> =
            NestedProperty(
                key,
                required,
                description,
                properties,
                isAdditionalPropertiesAllowed,
                additionalPropertiesSchema,
            )

        /**
         * Creates a static property representing an list of nested property objects.
         *
         * @param key The key for this property.
         * @param properties A list of typed properties defining the structure of the list items.
         * @param required Whether the agent must provide this property.
         * @param description An optional string explaining this property's purpose.
         * @param minItems The minimum number of items required in the list, defaults to 0.
         * @param maxItems The maximum number of items allowed in the list, defaults to
         *   [Int.MAX_VALUE] meaning no constraint.
         * @param isAdditionalPropertiesAllowed Whether properties not explicitly listed in
         *   [properties] are allowed, defaults to true.
         * @param additionalPropertiesSchema Schema definition for additional dynamic properties,
         *   only applicable if [isAdditionalPropertiesAllowed] is true.
         * @return A [StaticA2uiProperty] resolving to a List of [A2uiComponentProperties] at
         *   runtime.
         */
        public fun nestedList(
            key: String,
            properties: List<A2uiProperty<*>>,
            required: Boolean = false,
            description: String? = null,
            @IntRange(from = 0) minItems: Int = 0,
            @IntRange(from = 0) maxItems: Int = Int.MAX_VALUE,
            isAdditionalPropertiesAllowed: Boolean = true,
            additionalPropertiesSchema: A2uiSchema? = null,
        ): StaticA2uiProperty<List<A2uiComponentProperties>> =
            NestedListProperty(
                key,
                required,
                description,
                properties,
                minItems,
                maxItems,
                isAdditionalPropertiesAllowed,
                additionalPropertiesSchema,
            )

        /**
         * Creates a static property expecting a literal string value.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [String] at runtime.
         */
        public fun string(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<String> = StringProperty(key, required, description)

        /**
         * Creates a static property expecting a literal numeric value.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun number(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberProperty(
                key,
                isRequired = required,
                description = description,
                defaultValue = null,
            )

        /**
         * Creates an optional static property expecting a literal numeric value with an integer
         * default value.
         *
         * @param key The key for this property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun number(
            key: String,
            defaultValue: Int,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberProperty(
                key,
                isRequired = false,
                description = description,
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a literal numeric value with a float
         * default value.
         *
         * @param key The key for this property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun number(
            key: String,
            defaultValue: Float,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberProperty(
                key,
                isRequired = false,
                description = description,
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a literal numeric value with a double
         * default value.
         *
         * @param key The key for this property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun number(
            key: String,
            defaultValue: Double,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberProperty(
                key,
                isRequired = false,
                description = description,
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a literal numeric value with a long default
         * value.
         *
         * @param key The key for this property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun number(
            key: String,
            defaultValue: Long,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberProperty(
                key,
                isRequired = false,
                description = description,
                defaultValue = defaultValue,
            )

        /**
         * Creates a static property expecting a literal boolean value.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Boolean] at runtime.
         */
        public fun boolean(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<Boolean> =
            BooleanProperty(
                key,
                isRequired = required,
                description = description,
                defaultValue = null,
            )

        /**
         * Creates an optional static property expecting a literal boolean value with a default
         * value.
         *
         * @param key The key for this property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Boolean] at runtime.
         */
        public fun booleanWithDefault(
            key: String,
            defaultValue: Boolean,
            description: String? = null,
        ): StaticA2uiProperty<Boolean> =
            BooleanProperty(
                key,
                isRequired = false,
                description = description,
                defaultValue = defaultValue,
            )

        /**
         * Creates a static property accepting an arbitrary payload without strict type enforcement.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] resolving to [Any] (usually a Map, List, or primitive) at
         *   runtime.
         */
        public fun any(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<Any> = AnyProperty(key, required, description)

        /**
         * Creates a static property expecting a list of strings.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @param minItems The minimum number of items required in the list, defaults to 0.
         * @param maxItems The maximum number of items allowed in the list, defaults to
         *   [Int.MAX_VALUE] meaning no constraint.
         * @return A [StaticA2uiProperty] that resolves to a [List] of [String] at runtime.
         */
        public fun stringList(
            key: String,
            required: Boolean = false,
            description: String? = null,
            @IntRange(from = 0) minItems: Int = 0,
            @IntRange(from = 0) maxItems: Int = Int.MAX_VALUE,
        ): StaticA2uiProperty<List<String>> =
            StringListProperty(key, required, description, minItems, maxItems)

        /**
         * Creates a static property expecting a list of numbers.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @param minItems The minimum number of items required in the list, defaults to 0.
         * @param maxItems The maximum number of items allowed in the list, defaults to
         *   [Int.MAX_VALUE] meaning no constraint.
         * @return A [StaticA2uiProperty] that resolves to a [List] of [Number] at runtime.
         */
        public fun numberList(
            key: String,
            required: Boolean = false,
            description: String? = null,
            @IntRange(from = 0) minItems: Int = 0,
            @IntRange(from = 0) maxItems: Int = Int.MAX_VALUE,
        ): StaticA2uiProperty<List<Number>> =
            NumberListProperty(key, required, description, minItems, maxItems)

        /**
         * Creates a static property expecting a list of booleans.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @param minItems The minimum number of items required in the list, defaults to 0.
         * @param maxItems The maximum number of items allowed in the list, defaults to
         *   [Int.MAX_VALUE] meaning no constraint.
         * @return A [StaticA2uiProperty] that resolves to a [List] of [Boolean] at runtime.
         */
        public fun booleanList(
            key: String,
            required: Boolean = false,
            description: String? = null,
            @IntRange(from = 0) minItems: Int = 0,
            @IntRange(from = 0) maxItems: Int = Int.MAX_VALUE,
        ): StaticA2uiProperty<List<Boolean>> =
            BooleanListProperty(key, required, description, minItems, maxItems)

        /**
         * Creates a static property expecting a list of any values.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @param minItems The minimum number of items required in the list, defaults to 0.
         * @param maxItems The maximum number of items allowed in the list, defaults to
         *   [Int.MAX_VALUE] meaning no constraint.
         * @return A [StaticA2uiProperty] that resolves to a [List] of [Any] at runtime.
         */
        public fun anyList(
            key: String,
            required: Boolean = false,
            description: String? = null,
            @IntRange(from = 0) minItems: Int = 0,
            @IntRange(from = 0) maxItems: Int = Int.MAX_VALUE,
        ): StaticA2uiProperty<List<Any>> =
            AnyListProperty(key, required, description, minItems, maxItems)

        /**
         * Creates a static property expecting a string value strictly bounded to a predefined list
         * of options.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid string values the agent is allowed to supply for this
         *   property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [String] at runtime.
         */
        public fun stringEnum(
            key: String,
            enumValues: List<String>,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<String> =
            StringEnumProperty(
                key,
                isRequired = required,
                description = description,
                enumValues = enumValues,
                defaultValue = null,
            )

        /**
         * Creates an optional static property expecting a string value strictly bounded to a
         * predefined list of options with a default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid string values the agent is allowed to supply for this
         *   property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [String] at runtime.
         */
        public fun stringEnum(
            key: String,
            enumValues: List<String>,
            defaultValue: String,
            description: String? = null,
        ): StaticA2uiProperty<String> =
            StringEnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = enumValues,
                defaultValue = defaultValue,
            )

        /**
         * Creates a static property expecting a number value strictly bounded to a predefined list
         * of options.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid number values the agent is allowed to supply for this
         *   property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun numberEnum(
            key: String,
            enumValues: List<Number>,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberEnumProperty(
                key,
                isRequired = required,
                description = description,
                enumValues = enumValues,
                defaultValue = null,
            )

        /**
         * Creates an optional static property expecting an integer value strictly bounded to a
         * predefined list of options with a specified default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid integer values the agent is allowed to supply for this
         *   property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun numberEnum(
            key: String,
            enumValues: IntList,
            defaultValue: Int,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberEnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = List(enumValues.size) { enumValues[it] },
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a float value strictly bounded to a
         * predefined list of options with a specified default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid float values the agent is allowed to supply for this
         *   property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun numberEnum(
            key: String,
            enumValues: FloatList,
            defaultValue: Float,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberEnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = List(enumValues.size) { enumValues[it] },
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a double value strictly bounded to a
         * predefined list of options with a specified default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid double values the agent is allowed to supply for this
         *   property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun numberEnum(
            key: String,
            enumValues: DoubleList,
            defaultValue: Double,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberEnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = List(enumValues.size) { enumValues[it] },
                defaultValue = defaultValue,
            )

        /**
         * Creates an optional static property expecting a long value strictly bounded to a
         * predefined list of options with a specified default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid long values the agent is allowed to supply for this
         *   property.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to a [Number] at runtime.
         */
        public fun numberEnum(
            key: String,
            enumValues: LongList,
            defaultValue: Long,
            description: String? = null,
        ): StaticA2uiProperty<Number> =
            NumberEnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = List(enumValues.size) { enumValues[it] },
                defaultValue = defaultValue,
            )

        /**
         * Creates a static property expecting an enum value strictly bounded to a predefined list
         * of enum options with the ability to map values to custom types to support statically
         * typed enums.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid enum values the agent is allowed to supply for this
         *   property.
         * @param mapToString A mapping function to serialize an enum entry into its string schema
         *   value.
         * @param convertFromString A parsing function to convert a raw string value into an enum
         *   entry.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to [T] at runtime.
         */
        public fun <T : Enum<T>> enum(
            key: String,
            enumValues: List<T>,
            mapToString: (T) -> String,
            convertFromString: (String) -> T?,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<T> =
            EnumProperty(
                key,
                isRequired = required,
                description = description,
                enumValues = enumValues,
                mapToString = mapToString,
                convertFromString = convertFromString,
                defaultValue = null,
            )

        /**
         * Creates an optional static property expecting an enum value strictly bounded to a
         * predefined list of enum options with a default value.
         *
         * @param key The key for this property.
         * @param enumValues A list of valid enum values the agent is allowed to supply for this
         *   property.
         * @param mapToString A mapping function to serialize an enum entry into its string schema
         *   value.
         * @param convertFromString A parsing function to convert a raw string value into an enum
         *   entry.
         * @param defaultValue The default value for this property.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] that resolves to [T] at runtime.
         */
        public fun <T : Enum<T>> enum(
            key: String,
            enumValues: List<T>,
            mapToString: (T) -> String,
            convertFromString: (String) -> T?,
            defaultValue: T,
            description: String? = null,
        ): StaticA2uiProperty<T> =
            EnumProperty(
                key,
                isRequired = false,
                description = description,
                enumValues = enumValues,
                mapToString = mapToString,
                convertFromString = convertFromString,
                defaultValue = defaultValue,
            )

        /**
         * Creates a static property with a custom schema and type-casting logic.
         *
         * This allows defining a property that is constrained by a custom JSON schema (e.g.,
         * `oneOf` with specific literal schemas).
         *
         * @param key The key for this property.
         * @param schema The custom [A2uiSchema] defining this property's structural requirements.
         * @param safeCast A function to safely cast the raw payload into the expected type [T].
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @return A [StaticA2uiProperty] that resolves to [T] at runtime.
         */
        public fun <T> custom(
            key: String,
            schema: A2uiSchema,
            safeCast: (Any) -> T?,
            required: Boolean = false,
        ): StaticA2uiProperty<T> = CustomProperty(key, required, schema, safeCast)

        /**
         * Creates a dynamic property representing a reactive data binding mapped to a [String].
         *
         * The resolved runtime value will automatically be safely coerced or stringified if the
         * bound path points to non-string primitives or complex objects.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun dynamicString(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): DynamicA2uiProperty<String> = DynamicStringProperty(key, required, description)

        /**
         * Creates a dynamic property representing a reactive data binding mapped to a [Number].
         *
         * The resolved runtime value will safely attempt to coerce compliant [String]s (e.g.
         * `"42"`) or [Boolean]s (`true` -> `1`) into a [Number].
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun dynamicNumber(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): DynamicA2uiProperty<Number> = DynamicNumberProperty(key, required, description)

        /**
         * Creates a dynamic property representing a reactive data binding mapped to a [Boolean].
         *
         * The resolved runtime value will safely attempt to coerce compliant [String]s (e.g.
         * `"true"`) or [Number]s (`1` -> `true`, `0` -> `false`) into a [Boolean].
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun dynamicBoolean(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): DynamicA2uiProperty<Boolean> = DynamicBooleanProperty(key, required, description)

        /**
         * Creates a dynamic property representing a reactive data binding that can resolve to any
         * type.
         *
         * The payload provided by the agent can be a literal value, a data-model JSON pointer
         * (e.g., `{"path": "/user"}`), or a function call execution tree.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun dynamicValue(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): DynamicA2uiProperty<Any> = DynamicValueProperty(key, required, description)

        /**
         * Creates a dynamic property representing a reactive data binding mapped to a [List] of
         * [String]s.
         *
         * The resolved runtime value must be a list. Its children will automatically be coerced or
         * stringified into [String] values.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun dynamicStringList(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): DynamicA2uiProperty<List<String>> = DynamicStringListProperty(key, required, description)

        /**
         * Creates a dynamic property with a custom schema and type-casting logic.
         *
         * This allows defining a property that is dynamically evaluated against the data model, but
         * constrained by a custom JSON schema (e.g., `oneOf` with specific literal schemas).
         *
         * @param key The key for this property.
         * @param schema The custom [A2uiSchema] defining this property's structural requirements.
         * @param safeCast A function to safely cast the evaluated payload into the expected type
         *   [T].
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @return A [DynamicA2uiProperty] evaluated dynamically against the surface's data model at
         *   runtime.
         */
        public fun <T> dynamicCustom(
            key: String,
            schema: A2uiSchema,
            safeCast: (Any) -> T?,
            required: Boolean = false,
        ): DynamicA2uiProperty<T> = DynamicCustomProperty(key, required, schema, safeCast)

        /**
         * Creates a static property storing the string ID of another component within the same
         * surface.
         *
         * Used to link container components to singular children (e.g. a `Button`'s child).
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] resolving to the target component's ID as a [String].
         */
        public fun componentId(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<String> = ComponentIdProperty(key, required, description)

        /**
         * Creates a property representing a structural list of child component references.
         *
         * This signals to the agent that it can provide either:
         * 1. A static list of `ComponentId` string references.
         * 2. An object describing a dynamic data-bound template (e.g., `{"path": "/users",
         *    "componentId": "user_card"}`).
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [ChildListA2uiProperty] to be handled via
         *   `A2uiComponentProperties.bindChildReferences()`.
         */
        public fun childList(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): ChildListA2uiProperty = ChildListProperty(key, required, description)

        /**
         * Creates a static property describing an action to be executed when interacting with the
         * component.
         *
         * The payload dictates whether the action invokes a local UI function (e.g. `openUrl`) or
         * queues an interaction event over the network to the server.
         *
         * @param key The key for this property.
         * @param required Whether the agent must provide this property to successfully render the
         *   component.
         * @param description An optional string explaining this property's purpose to the agent.
         * @return A [StaticA2uiProperty] resolving to the action definition Map at runtime.
         */
        public fun action(
            key: String,
            required: Boolean = false,
            description: String? = null,
        ): StaticA2uiProperty<Map<String, Any?>> = ActionProperty(key, required, description)
    }
}

/**
 * A static configuration or structural property that should be read synchronously.
 *
 * Unlike dynamic bindings, the payloads mapped to `StaticA2uiProperty` are extracted natively from
 * the component payload without invoking the surface's dynamic evaluator.
 */
@Immutable
public sealed class StaticA2uiProperty<T> : A2uiProperty<T>() {
    internal abstract fun safeCast(value: Any): T?
}

/**
 * A property that supports dynamic data-binding and requires reactive evaluation.
 *
 * Values targeted by this property type are processed by the surface's evaluator, meaning they can
 * seamlessly resolve hardcoded literals, data-model JSON pointer bindings (`path`), or local
 * client-side functional expressions (`call`).
 */
@Immutable
public sealed class DynamicA2uiProperty<T> : A2uiProperty<T>() {
    internal abstract fun safeCast(value: Any): T?
}

/**
 * A property representing a structural list of child components.
 *
 * This unique property type delegates its resolution to the component rendering scope, transforming
 * either static lists or dynamic data-model list templates into an actionable list of
 * [A2uiComponentReference] items linked to individual data contexts.
 */
@Immutable public sealed class ChildListA2uiProperty : A2uiProperty<List<A2uiComponentReference>>()

@Immutable
internal class NestedProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    nestedProperties: List<A2uiProperty<*>>,
    isAdditionalPropertiesAllowed: Boolean,
    additionalPropertiesSchema: A2uiSchema?,
) : StaticA2uiProperty<A2uiComponentProperties>() {
    override val schema: A2uiSchema = run {
        val propertiesMap = mutableMapOf<String, A2uiSchema>()
        val requiredSet = mutableSetOf<String>()
        for (i in nestedProperties.indices) {
            val prop = nestedProperties[i]
            propertiesMap[prop.key] = prop.schema
            if (prop.isRequired) {
                requiredSet.add(prop.key)
            }
        }
        A2uiObjectSchema(
            properties = propertiesMap,
            required = requiredSet,
            description = description,
            isAdditionalPropertiesAllowed = isAdditionalPropertiesAllowed,
            additionalPropertiesSchema = additionalPropertiesSchema,
        )
    }

    override fun safeCast(value: Any): A2uiComponentProperties? {
        @Suppress("UNCHECKED_CAST") val map = value as? Map<String, Any?> ?: return null
        return A2uiComponentProperties(map)
    }
}

@Immutable
internal class NestedListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    nestedProperties: List<A2uiProperty<*>>,
    @IntRange(from = 0) minItems: Int,
    @IntRange(from = 0) maxItems: Int,
    isAdditionalPropertiesAllowed: Boolean,
    additionalPropertiesSchema: A2uiSchema?,
) : StaticA2uiProperty<List<A2uiComponentProperties>>() {
    override val schema: A2uiSchema = run {
        val propertiesMap = mutableMapOf<String, A2uiSchema>()
        val requiredSet = mutableSetOf<String>()
        for (i in nestedProperties.indices) {
            val prop = nestedProperties[i]
            propertiesMap[prop.key] = prop.schema
            if (prop.isRequired) {
                requiredSet.add(prop.key)
            }
        }
        A2uiArraySchema(
            items =
                A2uiObjectSchema(
                    properties = propertiesMap,
                    required = requiredSet,
                    isAdditionalPropertiesAllowed = isAdditionalPropertiesAllowed,
                    additionalPropertiesSchema = additionalPropertiesSchema,
                ),
            description = description,
            minItems = minItems,
            maxItems = if (maxItems == Int.MAX_VALUE) -1 else maxItems,
        )
    }

    override fun safeCast(value: Any): List<A2uiComponentProperties>? {
        val list = value as? List<*> ?: return null
        val result = ArrayList<A2uiComponentProperties>(list.size)
        for (i in list.indices) {
            val item = list[i]
            @Suppress("UNCHECKED_CAST") val map = item as? Map<String, Any?>
            if (map != null) {
                result.add(A2uiComponentProperties(map))
            }
        }
        return result
    }
}

@Immutable
internal class StringProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : StaticA2uiProperty<String>() {
    override val schema: A2uiSchema = A2uiStringSchema(description = description)

    override fun safeCast(value: Any): String? = value as? String
}

@Immutable
internal class NumberProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    defaultValue: Number?,
) : StaticA2uiProperty<Number>() {
    override val schema: A2uiSchema =
        A2uiNumberSchema(
            description = description,
            keywords = listOfNotNull(defaultValue?.let { A2uiSchemaKeyword.Default(it) }),
        )

    override fun safeCast(value: Any): Number? = value as? Number
}

@Immutable
internal class BooleanProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    defaultValue: Boolean?,
) : StaticA2uiProperty<Boolean>() {
    override val schema: A2uiSchema =
        A2uiBooleanSchema(
            description = description,
            keywords = listOfNotNull(defaultValue?.let { A2uiSchemaKeyword.Default(it) }),
        )

    override fun safeCast(value: Any): Boolean? = value as? Boolean
}

@Immutable
internal class AnyProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : StaticA2uiProperty<Any>() {
    override val schema: A2uiSchema = A2uiAnySchema(description)

    override fun safeCast(value: Any): Any = value
}

@Immutable
internal class StringListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    @IntRange(from = 0) minItems: Int,
    @IntRange(from = 0) maxItems: Int,
) : StaticA2uiProperty<List<String>>() {
    override val schema: A2uiSchema =
        A2uiArraySchema(
            items = A2uiStringSchema.INSTANCE,
            description = description,
            minItems = minItems,
            maxItems = if (maxItems == Int.MAX_VALUE) -1 else maxItems,
        )

    @Suppress("UNCHECKED_CAST")
    override fun safeCast(value: Any): List<String>? = value as? List<String>
}

@Immutable
internal class NumberListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    @IntRange(from = 0) minItems: Int,
    @IntRange(from = 0) maxItems: Int,
) : StaticA2uiProperty<List<Number>>() {
    override val schema: A2uiSchema =
        A2uiArraySchema(
            items = A2uiNumberSchema.INSTANCE,
            description = description,
            minItems = minItems,
            maxItems = if (maxItems == Int.MAX_VALUE) -1 else maxItems,
        )

    @Suppress("UNCHECKED_CAST")
    override fun safeCast(value: Any): List<Number>? = value as? List<Number>
}

@Immutable
internal class BooleanListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    @IntRange(from = 0) minItems: Int,
    @IntRange(from = 0) maxItems: Int,
) : StaticA2uiProperty<List<Boolean>>() {
    override val schema: A2uiSchema =
        A2uiArraySchema(
            items = A2uiBooleanSchema.INSTANCE,
            description = description,
            minItems = minItems,
            maxItems = if (maxItems == Int.MAX_VALUE) -1 else maxItems,
        )

    @Suppress("UNCHECKED_CAST")
    override fun safeCast(value: Any): List<Boolean>? = value as? List<Boolean>
}

@Immutable
internal class AnyListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    @IntRange(from = 0) minItems: Int,
    @IntRange(from = 0) maxItems: Int,
) : StaticA2uiProperty<List<Any>>() {
    override val schema: A2uiSchema =
        A2uiArraySchema(
            items = A2uiAnySchema.INSTANCE,
            description = description,
            minItems = minItems,
            maxItems = if (maxItems == Int.MAX_VALUE) -1 else maxItems,
        )

    @Suppress("UNCHECKED_CAST") override fun safeCast(value: Any): List<Any>? = value as? List<Any>
}

@Immutable
internal class StringEnumProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    enumValues: List<String>,
    defaultValue: String?,
) : StaticA2uiProperty<String>() {
    init {
        require(defaultValue == null || defaultValue in enumValues) {
            "The default value ($defaultValue) must be one of the specified enumValues: $enumValues"
        }
    }

    override val schema: A2uiSchema =
        A2uiStringSchema(
            description = description,
            keywords =
                listOfNotNull(
                    A2uiSchemaKeyword.Enum(enumValues),
                    defaultValue?.let { A2uiSchemaKeyword.Default(it) },
                ),
        )

    override fun safeCast(value: Any): String? = value as? String
}

@Immutable
internal class NumberEnumProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    enumValues: List<Number>,
    defaultValue: Number?,
) : StaticA2uiProperty<Number>() {
    init {
        require(defaultValue == null || defaultValue in enumValues) {
            "The default value ($defaultValue) must be one of the specified enumValues: $enumValues"
        }
    }

    override val schema: A2uiSchema =
        A2uiNumberSchema(
            description = description,
            keywords =
                listOfNotNull(
                    A2uiSchemaKeyword.Enum(enumValues),
                    defaultValue?.let { A2uiSchemaKeyword.Default(it) },
                ),
        )

    override fun safeCast(value: Any): Number? = value as? Number
}

@Immutable
internal class EnumProperty<T>(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
    private val enumValues: List<T>,
    mapToString: (T) -> String,
    private val convertFromString: (String) -> T?,
    defaultValue: T?,
) : StaticA2uiProperty<T>() {
    init {
        require(defaultValue == null || defaultValue in enumValues) {
            "The default value ($defaultValue) must be one of the specified enumValues: $enumValues"
        }
    }

    override val schema: A2uiSchema = run {
        val mappedEnumValues = ArrayList<String>(enumValues.size)
        for (i in enumValues.indices) {
            mappedEnumValues.add(mapToString(enumValues[i]))
        }
        val keywords =
            listOfNotNull(
                A2uiSchemaKeyword.Enum(mappedEnumValues),
                defaultValue?.let { A2uiSchemaKeyword.Default(mapToString(it)) },
            )
        A2uiStringSchema(description = description, keywords = keywords)
    }

    override fun safeCast(value: Any): T? =
        when (value) {
            is String -> convertFromString(value)
            is Enum<*> ->
                if ((enumValues as List<*>).contains(value))
                    @Suppress("UNCHECKED_CAST") (value as T)
                else null
            else -> null
        }
}

@Immutable
internal class CustomProperty<T>(
    override val key: String,
    override val isRequired: Boolean,
    override val schema: A2uiSchema,
    private val cast: (Any) -> T?,
) : StaticA2uiProperty<T>() {
    override fun safeCast(value: Any): T? = cast(value)
}

@Immutable
internal class DynamicStringProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : DynamicA2uiProperty<String>() {
    override val schema: A2uiSchema = A2uiDynamicStringSchema(description)

    override fun safeCast(value: Any): String? = TypeConversion.toString(value)
}

@Immutable
internal class DynamicNumberProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : DynamicA2uiProperty<Number>() {
    override val schema: A2uiSchema = A2uiDynamicNumberSchema(description)

    override fun safeCast(value: Any): Number? = TypeConversion.toNumber(value)
}

@Immutable
internal class DynamicBooleanProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : DynamicA2uiProperty<Boolean>() {
    override val schema: A2uiSchema = A2uiDynamicBooleanSchema(description)

    override fun safeCast(value: Any): Boolean? = TypeConversion.toBoolean(value)
}

@Immutable
internal class DynamicValueProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : DynamicA2uiProperty<Any>() {
    override val schema: A2uiSchema = A2uiDynamicValueSchema(description)

    override fun safeCast(value: Any): Any = value
}

@Immutable
internal class DynamicStringListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : DynamicA2uiProperty<List<String>>() {
    override val schema: A2uiSchema = A2uiDynamicStringListSchema(description)

    override fun safeCast(value: Any): List<String>? =
        when (value) {
            is List<*> -> {
                val result = ArrayList<String>(value.size)
                for (i in value.indices) {
                    result.add(TypeConversion.toString(value[i]) ?: "")
                }
                result
            }
            else -> null
        }
}

@Immutable
internal class DynamicCustomProperty<T>(
    override val key: String,
    override val isRequired: Boolean,
    override val schema: A2uiSchema,
    private val cast: (Any) -> T?,
) : DynamicA2uiProperty<T>() {
    override fun safeCast(value: Any): T? = cast(value)
}

@Immutable
internal class ComponentIdProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : StaticA2uiProperty<String>() {
    override val schema: A2uiSchema = A2uiComponentIdSchema(description)

    override fun safeCast(value: Any): String? = value as? String
}

@Immutable
internal class ChildListProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : ChildListA2uiProperty() {
    override val schema: A2uiSchema = A2uiChildListSchema(description)
}

@Immutable
internal class ActionProperty(
    override val key: String,
    override val isRequired: Boolean,
    description: String?,
) : StaticA2uiProperty<Map<String, Any?>>() {
    override val schema: A2uiSchema = A2uiActionSchema(description)

    @Suppress("UNCHECKED_CAST")
    override fun safeCast(value: Any): Map<String, Any?>? = value as? Map<String, Any?>
}

internal object TypeConversion {

    /** Limits recursion to prevent the stack overflow on cyclic references. */
    private const val MAX_DEPTH = 32

    fun toNumber(value: Any?): Number? {
        return when (value) {
            is Number -> value
            is CharSequence -> value.toString().toDoubleOrNull()
            is Boolean -> if (value) 1 else 0
            else -> null
        }
    }

    fun toBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is CharSequence -> value.toString().trim().toBoolean()
            is Number -> value.toDouble().let { it != 0.0 && !it.isNaN() }
            else -> null
        }
    }

    fun toString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Number,
            is Boolean -> value.toString()
            else -> {
                val iterable = toIterable(value)
                if (iterable != null) {
                    val stringBuilder = StringBuilder()
                    appendIterable(iterable, 0, stringBuilder)
                    stringBuilder.toString()
                } else if (value is Map<*, *>) {
                    val stringBuilder = StringBuilder()
                    appendMap(value, 0, stringBuilder)
                    stringBuilder.toString()
                } else {
                    value.toString()
                }
            }
        }
    }

    private fun appendMap(map: Map<*, *>, depth: Int, stringBuilder: StringBuilder) {
        if (depth >= MAX_DEPTH) {
            stringBuilder.append("null")
            return
        }
        stringBuilder.append('{')
        var first = true
        val nextDepth = depth + 1
        for ((k, v) in map) {
            if (!first) stringBuilder.append(',')
            first = false
            stringBuilder.append('"')
            appendEscapedString(k.toString(), stringBuilder)
            stringBuilder.append("\":")
            appendNode(v, nextDepth, stringBuilder)
        }
        stringBuilder.append('}')
    }

    private fun appendIterable(iterable: Iterable<*>, depth: Int, stringBuilder: StringBuilder) {
        if (depth >= MAX_DEPTH) {
            stringBuilder.append("null")
            return
        }
        stringBuilder.append('[')
        var first = true
        val nextDepth = depth + 1
        for (item in iterable) {
            if (!first) stringBuilder.append(',')
            first = false
            appendNode(item, nextDepth, stringBuilder)
        }
        stringBuilder.append(']')
    }

    private fun appendEscapedString(str: String, stringBuilder: StringBuilder) {
        for (i in str.indices) {
            when (val c = str[i]) {
                '"' -> stringBuilder.append("\\\"")
                '\\' -> stringBuilder.append("\\\\")
                '\b' -> stringBuilder.append("\\b")
                '\u000C' -> stringBuilder.append("\\f")
                '\n' -> stringBuilder.append("\\n")
                '\r' -> stringBuilder.append("\\r")
                '\t' -> stringBuilder.append("\\t")
                else -> {
                    if (c < ' ') {
                        stringBuilder.append("\\u")
                        val code = c.code
                        // Zero-allocation hex string conversion
                        stringBuilder.append(Character.forDigit((code shr 12) and 0xF, 16))
                        stringBuilder.append(Character.forDigit((code shr 8) and 0xF, 16))
                        stringBuilder.append(Character.forDigit((code shr 4) and 0xF, 16))
                        stringBuilder.append(Character.forDigit(code and 0xF, 16))
                    } else {
                        stringBuilder.append(c)
                    }
                }
            }
        }
    }

    private fun appendNode(node: Any?, depth: Int, sb: StringBuilder) {
        when (node) {
            null -> sb.append("null")
            is String -> {
                sb.append('"')
                appendEscapedString(node, sb)
                sb.append('"')
            }
            is Boolean -> sb.append(node)
            is Number -> {
                if (node is Double && (node.isNaN() || node.isInfinite())) {
                    sb.append("null")
                } else if (node is Float && (node.isNaN() || node.isInfinite())) {
                    sb.append("null")
                } else {
                    sb.append(node)
                }
            }
            is Map<*, *> -> appendMap(node, depth, sb)
            else -> {
                val iterable = toIterable(node)
                if (iterable != null) {
                    appendIterable(iterable, depth, sb)
                } else {
                    sb.append('"')
                    appendEscapedString(node.toString(), sb)
                    sb.append('"')
                }
            }
        }
    }

    /**
     * Unboxes standard Kotlin collections and primitive arrays into a unified Iterable so they
     * serialize cleanly as JSON arrays rather than JVM string references.
     */
    private fun toIterable(obj: Any): Iterable<*>? =
        when (obj) {
            is Iterable<*> -> obj
            is Array<*> -> obj.asIterable()
            is IntArray -> obj.asIterable()
            is LongArray -> obj.asIterable()
            is FloatArray -> obj.asIterable()
            is DoubleArray -> obj.asIterable()
            is BooleanArray -> obj.asIterable()
            is ShortArray -> obj.asIterable()
            is ByteArray -> obj.asIterable()
            is CharArray -> obj.asIterable()
            else -> null
        }
}
