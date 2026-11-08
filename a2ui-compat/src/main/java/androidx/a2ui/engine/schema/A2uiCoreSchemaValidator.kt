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

package androidx.a2ui.engine.schema

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.model.catalog.toSchema
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiBooleanSchema
import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiRefSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.annotation.RestrictTo

/**
 * A stateless utility for validating raw JSON against A2UI Schema definitions. This class ensures
 * that incoming JSON payloads from the agent structurally conform to the expected UI component
 * schemas *before* they are allowed into the reactive data models.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class A2uiCoreSchemaValidator(private val catalog: A2uiCoreCatalog? = null) {

    private val functionSchemas: Map<String, A2uiSchema> =
        catalog?.functions?.associate { func -> func.definition.name to func.definition.toSchema() }
            ?: emptyMap()

    private val anyFunctionSchema: A2uiSchema =
        A2uiAnySchema(
            keywords = listOf(A2uiSchemaKeyword.OneOf(schemas = functionSchemas.values.toList()))
        )

    /**
     * Validates that the given payload conforms to the provided schema.
     *
     * @param payload The raw payload to validate (e.g., a component properties).
     * @param schema The schema defining the expected structure.
     * @param basePath The base path to use for error reporting (defaults to "/").
     * @throws A2uiException.A2uiValidationException if the payload violates the schema rules or
     *   JSON is malformed.
     */
    public fun validateSchema(payload: Any?, schema: A2uiSchema, basePath: String = "/") {
        validateSchemaInternal(payload, schema, basePath)
    }

    /**
     * Validates that the given payload conforms to the provided schema. Use [path] to track the
     * location of the subschema within the root schema for accurate errors.
     */
    private fun validateSchemaInternal(payload: Any?, schema: A2uiSchema, path: String = "/") {
        for (keyword in schema.keywords) {
            when (keyword) {
                is A2uiSchemaKeyword.OneOf -> validateOneOf(payload, keyword.schemas, path)
                is A2uiSchemaKeyword.AllOf -> validateAllOf(payload, keyword.schemas, path)
                is A2uiSchemaKeyword.AnyOf -> validateAnyOf(payload, keyword.schemas, path)
                is A2uiSchemaKeyword.Not -> validateNot(payload, keyword.schema, path)
                is A2uiSchemaKeyword.Enum -> validateEnum(payload, keyword.values, path)
                is A2uiSchemaKeyword.Const -> validateConst(payload, keyword.value, path)
                is A2uiSchemaKeyword.Format -> validateFormat(payload, keyword.format, path)
                is A2uiSchemaKeyword.IfThen ->
                    validateIfThen(
                        payload = payload,
                        ifSchema = keyword.ifSchema,
                        thenSchema = keyword.thenSchema,
                        elseSchema = keyword.elseSchema,
                        path = path,
                    )
                is A2uiSchemaKeyword.Default -> {
                    /* default is an annotation keyword */
                }
            }
        }
        when (schema) {
            is A2uiObjectSchema -> validateObject(payload, schema, path)
            is A2uiStringSchema -> validateString(payload, path)
            is A2uiNumberSchema -> validateNumber(payload, path)
            is A2uiBooleanSchema -> validateBoolean(payload, path)
            is A2uiArraySchema -> validateArray(payload, schema, path)
            is A2uiAnySchema -> validateAny()
            is A2uiRefSchema -> validateRef(payload, schema, path)
            is A2uiCompositeSchema -> validateSchemaInternal(payload, schema.getDefinition(), path)
        }
    }

    private fun validateObject(payload: Any?, schema: A2uiObjectSchema, path: String) {
        if (payload !is Map<*, *>) {
            throw A2uiException.A2uiValidationException(
                "Expected an object, but instead got: $payload",
                path,
            )
        }
        for (required in schema.required) {
            if (required !in payload) {
                throw A2uiException.A2uiValidationException(
                    "Missing required property '$required'",
                    path,
                )
            }
        }
        for ((key, value) in payload) {
            val propertySchema = schema.properties[key]
            if (propertySchema != null) {
                validateSchemaInternal(value, propertySchema, concatPath(path, key.toString()))
            } else if (!schema.isAdditionalPropertiesAllowed) {
                throw A2uiException.A2uiValidationException(
                    "Additional property '$key' not allowed",
                    path,
                )
            } else {
                val additionalPropertiesSchema = schema.additionalPropertiesSchema
                if (additionalPropertiesSchema != null) {
                    validateSchemaInternal(
                        value,
                        additionalPropertiesSchema,
                        concatPath(path, key.toString()),
                    )
                }
            }
        }
    }

    private fun validateString(payload: Any?, path: String) {
        if (payload !is String) {
            throw A2uiException.A2uiValidationException(
                "Expected a string, but instead got: $payload",
                path,
            )
        }
    }

    private fun validateFormat(payload: Any?, format: String, path: String) {
        if (payload !is String) {
            return
        }
        val isValid =
            when (format) {
                "date-time" -> DATE_TIME_REGEX.matches(payload)
                "date" -> DATE_REGEX.matches(payload)
                "time" -> TIME_REGEX.matches(payload)
                else ->
                    throw A2uiException.A2uiValidationException(
                        "Unsupported format '$format'",
                        path,
                    )
            }
        if (!isValid) {
            throw A2uiException.A2uiValidationException(
                "String value '$payload' does not match format '$format'",
                path,
            )
        }
    }

    private fun validateIfThen(
        payload: Any?,
        ifSchema: A2uiSchema,
        thenSchema: A2uiSchema,
        elseSchema: A2uiSchema?,
        path: String,
    ) {
        var ifMatched = false
        try {
            validateSchemaInternal(payload, ifSchema, path)
            ifMatched = true
        } catch (_: A2uiException.A2uiValidationException) {
            // Failing the 'if' condition is valid logic flow during conditional evaluation.
            // An 'if' mismatch is not a schema failure, but determines whether execution branches
            // to 'then' or 'else'.
        }

        if (ifMatched) {
            validateSchemaInternal(payload, thenSchema, path)
        } else if (elseSchema != null) {
            validateSchemaInternal(payload, elseSchema, path)
        }
    }

    private fun validateNumber(payload: Any?, path: String) {
        if (payload !is Number) {
            throw A2uiException.A2uiValidationException(
                "Expected a number, but instead got: $payload",
                path,
            )
        }
    }

    private fun validateBoolean(payload: Any?, path: String) {
        if (payload !is Boolean) {
            throw A2uiException.A2uiValidationException(
                "Expected a boolean, but instead got: $payload",
                path,
            )
        }
    }

    private fun validateArray(payload: Any?, schema: A2uiArraySchema, path: String) {
        val itemsList = toList(payload)
        if (itemsList == null) {
            throw A2uiException.A2uiValidationException(
                "Expected an array, but instead got: $payload",
                path,
            )
        }
        if (schema.minItems >= 0 && itemsList.size < schema.minItems) {
            throw A2uiException.A2uiValidationException(
                "Array has ${itemsList.size} items, but minimum required is ${schema.minItems}",
                path,
            )
        }
        if (schema.maxItems >= 0 && itemsList.size > schema.maxItems) {
            throw A2uiException.A2uiValidationException(
                "Array has ${itemsList.size} items, but maximum allowed is ${schema.maxItems}",
                path,
            )
        }
        val itemsSchema = schema.items
        if (itemsSchema != null) {
            itemsList.forEachIndexed { index, item ->
                validateSchemaInternal(item, itemsSchema, concatPath(path, index.toString()))
            }
        }
    }

    private fun validateEnum(payload: Any?, enumValues: List<Any>, path: String) {
        if (!enumValues.any { areEqual(payload, it) }) {
            throw A2uiException.A2uiValidationException(
                "Value '${payload}' is not a valid enum value",
                path,
            )
        }
    }

    private fun concatPath(path: String, segment: String): String =
        if (path.endsWith("/")) "$path$segment" else "$path/$segment"

    private fun validateAny() {
        // Any valid JSON element is allowed.
    }

    private fun validateOneOf(payload: Any?, subSchemas: List<A2uiSchema>, path: String) {
        var matchCount = 0
        for (subSchema in subSchemas) {
            try {
                validateSchemaInternal(payload, subSchema, path)
                matchCount++
            } catch (_: A2uiException.A2uiValidationException) {
                // Ignore validation failure for subschemas
            }
        }
        if (matchCount != 1) {
            throw A2uiException.A2uiValidationException(
                "Payload must match exactly one subschema, but instead matched $matchCount",
                path,
            )
        }
    }

    private fun validateAllOf(payload: Any?, subSchemas: List<A2uiSchema>, path: String) {
        for (subSchema in subSchemas) {
            validateSchemaInternal(payload, subSchema, path)
        }
    }

    private fun validateAnyOf(payload: Any?, subSchemas: List<A2uiSchema>, path: String) {
        var matched = false
        for (subSchema in subSchemas) {
            try {
                validateSchemaInternal(payload, subSchema, path)
                matched = true
                break
            } catch (_: A2uiException.A2uiValidationException) {
                // Ignore validation failure for subschemas
            }
        }
        if (!matched) {
            throw A2uiException.A2uiValidationException(
                "Payload must match at least one subschema",
                path,
            )
        }
    }

    private fun validateNot(payload: Any?, subSchema: A2uiSchema, path: String) {
        var matched = false
        try {
            validateSchemaInternal(payload, subSchema, path)
            matched = true
        } catch (_: A2uiException.A2uiValidationException) {
            // Failure means NOT constraint satisfied
        }
        if (matched) {
            throw A2uiException.A2uiValidationException(
                "Payload must NOT match subschema, but it matched",
                path,
            )
        }
    }

    private fun validateConst(payload: Any?, expectedValue: Any?, path: String) {
        if (!areEqual(payload, expectedValue)) {
            throw A2uiException.A2uiValidationException(
                "Expected a const value '${expectedValue}', but instead got: $payload",
                path,
            )
        }
    }

    private fun isValidCatalogRef(ref: String): Boolean {
        val beforeFragment = ref.substringBefore('#', "")
        return beforeFragment.isEmpty() || beforeFragment == CATALOG_JSON
    }

    private fun validateRef(payload: Any?, schema: A2uiRefSchema, path: String) {
        val ref = schema.ref
        if (!isValidCatalogRef(ref)) {
            throw A2uiException.A2uiValidationException(
                "Unsupported reference '$ref'. References must belong to the $CATALOG_JSON namespace or be relative within it.",
                path,
            )
        }
        val fragment = ref.substringAfter('#').removePrefix("/")
        when {
            fragment == ANY_FUNCTION_FRAGMENT || fragment.endsWith("/$ANY_FUNCTION_FRAGMENT") -> {
                validateAnyFunctionRef(payload, path)
            }
            fragment.startsWith(COMPONENTS_PREFIX) -> {
                validateComponentRef(payload, ref, fragment, path)
            }
            fragment.startsWith(FUNCTIONS_PREFIX) -> {
                validateFunctionRef(payload, ref, fragment, path)
            }
            else -> {
                throw A2uiException.A2uiValidationException(
                    "Unsupported reference fragment '$fragment' in reference '$ref'",
                    path,
                )
            }
        }
    }

    private fun validateAnyFunctionRef(payload: Any?, path: String) {
        validateSchemaInternal(payload, anyFunctionSchema, path)
    }

    private fun validateComponentRef(payload: Any?, ref: String, fragment: String, path: String) {
        val compName = fragment.substringAfter(COMPONENTS_PREFIX)
        val compDef = catalog?.componentDefinitions?.get(compName)
        if (compDef != null) {
            validateSchemaInternal(payload, compDef.propertySchema, path)
        } else {
            throw A2uiException.A2uiValidationException(
                "Component '$compName' not found in catalog for reference '$ref'",
                path,
            )
        }
    }

    private fun validateFunctionRef(payload: Any?, ref: String, fragment: String, path: String) {
        val funcName = fragment.substringAfter(FUNCTIONS_PREFIX)
        val funcSchema = functionSchemas[funcName]
        if (funcSchema != null) {
            validateSchemaInternal(payload, funcSchema, path)
        } else {
            throw A2uiException.A2uiValidationException(
                "Function '$funcName' not found in catalog for reference '$ref'",
                path,
            )
        }
    }

    /**
     * Determines deep/structural equality between two values.
     *
     * Standard JVM equality (`==` or `.contains()`) falls short for JSON schema validation on
     * complex/nested payloads due to several issues:
     * 1. **Arrays**: JVM arrays (e.g., `Array`, `IntArray`, `DoubleArray`) check reference/identity
     *    equality instead of structural content equality.
     * 2. **Collection Types**: An `Array` and a `List` of the same elements are not considered
     *    equal by default `==` even if they represent the same JSON array structure.
     * 3. **Numeric Types**: Differing numeric types (e.g., `1` as an `Int` vs. `1L` as a `Long`)
     *    fail JVM equality even though they are mathematically equivalent numbers in a JSON
     *    context.
     *
     * This function recursively resolves these differences to perform an accurate deep structural
     * comparison for maps, arrays/collections, numbers, and primitives.
     *
     * @param a The first value.
     * @param b The second value.
     * @return True if [a] and [b] are structurally equal, false otherwise.
     */
    private fun areEqual(a: Any?, b: Any?): Boolean {
        if (a === b) return true
        if (a == null || b == null) return false

        if (a is Map<*, *> && b is Map<*, *>) {
            return areMapsEqual(a, b)
        }

        val aList = toList(a)
        val bList = toList(b)

        if (aList != null && bList != null) {
            return areCollectionsEqual(aList, bList)
        }

        if (a is Number && b is Number) {
            return areNumbersEqual(a, b)
        }

        return a == b
    }

    private fun areMapsEqual(a: Map<*, *>, b: Map<*, *>): Boolean {
        if (a.size != b.size) return false
        for ((k, v) in a) {
            if (!b.containsKey(k)) return false
            if (!areEqual(v, b[k])) return false
        }
        return true
    }

    private fun areCollectionsEqual(a: List<*>, b: List<*>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!areEqual(a[i], b[i])) return false
        }
        return true
    }

    private fun areNumbersEqual(a: Number, b: Number): Boolean {
        if (a is Double || a is Float || b is Double || b is Float) {
            return a.toDouble() == b.toDouble()
        }
        return a.toLong() == b.toLong()
    }

    private fun toList(obj: Any?): List<*>? {
        return when (obj) {
            is List<*> -> obj
            is Array<*> -> obj.toList()
            is BooleanArray -> obj.toList()
            is ByteArray -> obj.toList()
            is CharArray -> obj.toList()
            is DoubleArray -> obj.toList()
            is FloatArray -> obj.toList()
            is IntArray -> obj.toList()
            is LongArray -> obj.toList()
            is ShortArray -> obj.toList()
            is Iterable<*> -> obj.toList()
            else -> null
        }
    }

    private companion object {
        private const val CATALOG_JSON = "catalog.json"
        private const val ANY_FUNCTION_FRAGMENT = "\$defs/anyFunction"
        private const val COMPONENTS_PREFIX = "components/"
        private const val FUNCTIONS_PREFIX = "functions/"

        private val DATE_REGEX = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")
        private val TIME_REGEX =
            Regex("""^\d{2}:\d{2}(:\d{2}([.,]\d+)?)?([zZ]|[+-]\d{2}(?::\d{2})?)?$""")
        private val DATE_TIME_REGEX =
            Regex(
                """^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])[tT ]\d{2}:\d{2}(:\d{2}([.,]\d+)?)?([zZ]|[+-]\d{2}(?::\d{2})?)?$"""
            )
    }
}
