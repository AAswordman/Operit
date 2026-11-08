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

@file:JvmName("A2uiCoreComponentDefinitionSerializerKt")

package androidx.a2ui.engine.catalog

import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiCompositeSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiRefSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema

/**
 * Converts a component definition into an [A2uiSchema].
 *
 * @param componentDefinition the component definition to serialize
 * @return the [A2uiSchema] representation of the component definition with discriminator
 */
internal fun serializeComponentDefinitionToSchema(
    componentDefinition: A2uiCoreComponentDefinition
): A2uiSchema {
    val rawSchema = componentDefinition.propertySchema
    val componentName = componentDefinition.name
    val description = componentDefinition.description

    // As we can't resolve $ref schemas, we'll assume they are referencing an
    // object schema for our purposes.
    require(validateIsObjectSchema(rawSchema) != ObjectSchemaValidationResult.NOT_OBJECT) {
        "Unexpected schema type '${rawSchema::class.simpleName}'. " +
            "Top-level component schema must be A2uiObjectSchema, A2uiCompositeSchema, A2uiRefSchema, or A2uiAnySchema schema with AllOf, OneOf, or AnyOf keyword."
    }

    val isObjectWithTopLevelProperties =
        rawSchema is A2uiObjectSchema && rawSchema.properties.isNotEmpty()
    var allOfKeyword: A2uiSchemaKeyword.AllOf? = null
    var hasOneOfOrAnyOf = false
    for (keyword in rawSchema.keywords) {
        when (keyword) {
            is A2uiSchemaKeyword.AllOf -> {
                allOfKeyword = keyword
            }
            is A2uiSchemaKeyword.OneOf,
            is A2uiSchemaKeyword.AnyOf -> hasOneOfOrAnyOf = true
            else -> {}
        }
        if (allOfKeyword != null && hasOneOfOrAnyOf) break
    }

    return when {
        // References are wrapped in allOf to preserve their $ref link
        rawSchema is A2uiCompositeSchema || rawSchema is A2uiRefSchema ->
            wrapWithAllOfDiscriminator(rawSchema, componentName, description)
        // Direct object schemas with properties receive the discriminator directly
        isObjectWithTopLevelProperties ->
            injectComponentMetadataIntoProperties(
                rawSchema as A2uiObjectSchema,
                componentName,
                description,
            )
        // Union schemas without properties must be intersected via allOf
        hasOneOfOrAnyOf -> wrapWithAllOfDiscriminator(rawSchema, componentName, description)
        // AllOf compositions without properties merge the discriminator into the allOf list
        allOfKeyword != null ->
            injectComponentMetadataIntoAllOf(rawSchema, allOfKeyword, componentName, description)
        // Empty object schemas receive the discriminator directly
        rawSchema is A2uiObjectSchema ->
            injectComponentMetadataIntoProperties(rawSchema, componentName, description)
        else ->
            throw IllegalArgumentException(
                "Unexpected schema type '${rawSchema::class.java.simpleName}'. " +
                    "Top-level component schema must be A2uiObjectSchema, A2uiCompositeSchema, or a schema with AllOf, OneOf, or AnyOf keyword."
            )
    }
}

private fun wrapWithAllOfDiscriminator(
    schema: A2uiSchema,
    componentName: String,
    componentDescription: String,
): A2uiObjectSchema {
    val targetDescription = resolveDescription(schema.description, componentDescription)
    val discriminatorObjectSchema =
        A2uiObjectSchema(
            properties =
                mapOf(
                    "component" to
                        A2uiStringSchema(keywords = listOf(A2uiSchemaKeyword.Const(componentName)))
                ),
            required = setOf("component"),
        )
    val subSchema =
        when (schema) {
            is A2uiCompositeSchema,
            is A2uiRefSchema -> schema
            is A2uiObjectSchema -> schema.copy(description = null)
            is A2uiAnySchema -> schema.copy(description = null)
            else ->
                throw IllegalArgumentException(
                    "Unexpected schema type '${schema::class.java.simpleName}' in wrapWithAllOfDiscriminator."
                )
        }
    return A2uiObjectSchema(
        description = targetDescription,
        keywords = listOf(A2uiSchemaKeyword.AllOf(listOf(subSchema, discriminatorObjectSchema))),
    )
}

private enum class ObjectSchemaValidationResult {
    IS_OBJECT,
    NOT_OBJECT,
    MAYBE_OBJECT,
}

private fun validateIsObjectSchema(schema: A2uiSchema): ObjectSchemaValidationResult =
    when {
        schema is A2uiObjectSchema -> ObjectSchemaValidationResult.IS_OBJECT
        schema is A2uiCompositeSchema -> validateIsObjectSchema(schema.getDefinition())
        schema is A2uiRefSchema -> ObjectSchemaValidationResult.MAYBE_OBJECT
        schema is A2uiAnySchema -> validateAnySchemaIsObjectSchema(schema)
        else -> ObjectSchemaValidationResult.NOT_OBJECT
    }

private fun validateAnySchemaIsObjectSchema(schema: A2uiAnySchema): ObjectSchemaValidationResult {
    val compositionKeywords =
        schema.keywords.filter {
            it is A2uiSchemaKeyword.AllOf ||
                it is A2uiSchemaKeyword.OneOf ||
                it is A2uiSchemaKeyword.AnyOf
        }
    if (compositionKeywords.isEmpty()) {
        return ObjectSchemaValidationResult.NOT_OBJECT
    }
    var maybeObject = false
    for (keyword in compositionKeywords) {
        when (validateCompositionKeywordIsObjectSchema(keyword)) {
            ObjectSchemaValidationResult.NOT_OBJECT ->
                return ObjectSchemaValidationResult.NOT_OBJECT
            ObjectSchemaValidationResult.MAYBE_OBJECT -> maybeObject = true
            ObjectSchemaValidationResult.IS_OBJECT -> {}
        }
    }
    return if (maybeObject) {
        ObjectSchemaValidationResult.MAYBE_OBJECT
    } else {
        ObjectSchemaValidationResult.IS_OBJECT
    }
}

private fun validateCompositionKeywordIsObjectSchema(
    keyword: A2uiSchemaKeyword<Any>
): ObjectSchemaValidationResult =
    when (keyword) {
        is A2uiSchemaKeyword.AllOf -> {
            var result = ObjectSchemaValidationResult.MAYBE_OBJECT
            for (subSchema in keyword.schemas) {
                when (validateIsObjectSchema(subSchema)) {
                    ObjectSchemaValidationResult.NOT_OBJECT -> {
                        result = ObjectSchemaValidationResult.NOT_OBJECT
                        break
                    }
                    ObjectSchemaValidationResult.IS_OBJECT ->
                        result = ObjectSchemaValidationResult.IS_OBJECT
                    ObjectSchemaValidationResult.MAYBE_OBJECT -> {}
                }
            }
            result
        }
        is A2uiSchemaKeyword.OneOf,
        is A2uiSchemaKeyword.AnyOf -> {
            val subSchemas =
                (keyword as? A2uiSchemaKeyword.OneOf)?.schemas
                    ?: (keyword as A2uiSchemaKeyword.AnyOf).schemas
            val subSchemasResults = subSchemas.map { validateIsObjectSchema(it) }
            when {
                subSchemasResults.any { it == ObjectSchemaValidationResult.IS_OBJECT } ->
                    ObjectSchemaValidationResult.IS_OBJECT
                subSchemasResults.any { it == ObjectSchemaValidationResult.MAYBE_OBJECT } ->
                    ObjectSchemaValidationResult.MAYBE_OBJECT
                else -> ObjectSchemaValidationResult.NOT_OBJECT
            }
        }
        else -> ObjectSchemaValidationResult.NOT_OBJECT
    }

private fun injectComponentMetadataIntoProperties(
    schema: A2uiObjectSchema,
    componentName: String,
    componentDescription: String,
): A2uiObjectSchema {
    val targetDescription = resolveDescription(schema.description, componentDescription)
    return injectObjectDiscriminator(schema, componentName, targetDescription)
}

private fun injectObjectDiscriminator(
    schema: A2uiObjectSchema,
    componentName: String,
    description: String? = schema.description,
): A2uiObjectSchema {
    val existingComponentProperty = schema.properties["component"]
    if (existingComponentProperty != null) {
        val constKeyword =
            existingComponentProperty.keywords
                .filterIsInstance<A2uiSchemaKeyword.Const<*>>()
                .firstOrNull()
        require(constKeyword != null && constKeyword.value == componentName) {
            "Existing 'component' property const '${constKeyword?.value}' " +
                "does not match expected component name '$componentName'"
        }
        if (schema.description == description && schema.required.contains("component")) {
            return schema
        }
        return schema.copy(
            description = description,
            required =
                if (schema.required.contains("component")) schema.required
                else setOf("component") + schema.required,
        )
    }
    val updatedProperties =
        mapOf(
            "component" to
                A2uiStringSchema(keywords = listOf(A2uiSchemaKeyword.Const(componentName)))
        ) + schema.properties
    val updatedRequired = setOf("component") + schema.required
    return schema.copy(
        description = description,
        properties = updatedProperties,
        required = updatedRequired,
    )
}

private fun injectComponentMetadataIntoAllOf(
    schema: A2uiSchema,
    allOfKeyword: A2uiSchemaKeyword.AllOf,
    componentName: String,
    componentDescription: String,
): A2uiSchema {
    val targetDescription = resolveDescription(schema.description, componentDescription)
    val objectSchemasCount = allOfKeyword.schemas.count { it is A2uiObjectSchema }
    val updatedSubSchemas =
        if (objectSchemasCount == 1) {
            allOfKeyword.schemas.map { subSchema ->
                if (subSchema is A2uiObjectSchema) {
                    injectObjectDiscriminator(subSchema, componentName)
                } else {
                    subSchema
                }
            }
        } else {
            val discriminatorObjectSchema =
                A2uiObjectSchema(
                    properties =
                        mapOf(
                            "component" to
                                A2uiStringSchema(
                                    keywords = listOf(A2uiSchemaKeyword.Const(componentName))
                                )
                        ),
                    required = setOf("component"),
                )
            allOfKeyword.schemas + discriminatorObjectSchema
        }
    val updatedKeywords =
        schema.keywords.map { keyword ->
            if (keyword === allOfKeyword) A2uiSchemaKeyword.AllOf(updatedSubSchemas) else keyword
        }
    return when (schema) {
        is A2uiObjectSchema -> {
            @Suppress("UNCHECKED_CAST")
            val objectKeywords = updatedKeywords as List<A2uiSchemaKeyword<Map<String, Any>>>
            schema.copy(description = targetDescription, keywords = objectKeywords)
        }
        is A2uiAnySchema -> schema.copy(description = targetDescription, keywords = updatedKeywords)
        else ->
            throw IllegalArgumentException(
                "Unexpected schema type '${schema::class.java.simpleName}' in injectComponentMetadataIntoAllOf."
            )
    }
}

private fun resolveDescription(
    existingDescription: String?,
    componentDescription: String,
): String? {
    if (existingDescription != null) {
        require(existingDescription == componentDescription) {
            "Existing description '$existingDescription' does not match expected component description '$componentDescription'"
        }
        return existingDescription
    }
    return componentDescription.ifBlank { null }
}

private fun A2uiObjectSchema.copy(
    properties: Map<String, A2uiSchema> = this.properties,
    required: Set<String> = this.required,
    isAdditionalPropertiesAllowed: Boolean = this.isAdditionalPropertiesAllowed,
    additionalPropertiesSchema: A2uiSchema? = this.additionalPropertiesSchema,
    description: String? = this.description,
    keywords: List<A2uiSchemaKeyword<Map<String, Any>>> = this.keywords,
): A2uiObjectSchema =
    A2uiObjectSchema(
        properties = properties,
        required = required,
        isAdditionalPropertiesAllowed = isAdditionalPropertiesAllowed,
        additionalPropertiesSchema = additionalPropertiesSchema,
        description = description,
        keywords = keywords,
    )

private fun A2uiAnySchema.copy(
    description: String? = this.description,
    keywords: List<A2uiSchemaKeyword<Any>> = this.keywords,
): A2uiAnySchema = A2uiAnySchema(description = description, keywords = keywords)
