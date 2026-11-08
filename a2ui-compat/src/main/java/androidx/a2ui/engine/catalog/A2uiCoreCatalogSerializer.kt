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

package androidx.a2ui.engine.catalog

import androidx.a2ui.model.catalog.toSchema
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Serializes an [A2uiCoreCatalog] into JSON Schema representations.
 *
 * Traversal and JSON formatting are computed lazily and cached per serializer instance.
 *
 * @param catalog the catalog to serialize
 */
public class A2uiCoreCatalogSerializer(public val catalog: A2uiCoreCatalog) {
    internal val jsonObject: JsonObject by
        lazy(LazyThreadSafetyMode.PUBLICATION) { catalog.toJsonObject() }

    /** The serialized JSON Schema string representation of [catalog]. */
    public val jsonSchemaString: String by
        lazy(LazyThreadSafetyMode.PUBLICATION) { jsonObject.toString() }

    /** The serialized JSON Schema Map representation of [catalog]. */
    public val jsonSchemaMap: Map<String, Any?> by
        lazy(LazyThreadSafetyMode.PUBLICATION) {
            @Suppress("UNCHECKED_CAST")
            jsonObject.toMapValue() as Map<String, Any?>
        }
}

internal fun JsonElement.toMapValue(): Any? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }
        is JsonArray -> map { it.toMapValue() }
        is JsonObject -> mapValues { it.value.toMapValue() }
    }

internal fun A2uiCoreCatalog.toJsonObject(): JsonObject = buildJsonObject {
    put("\$schema", "https://json-schema.org/draft/2020-12/schema")
    put("\$id", id)
    title?.let { put("title", it) }
    description?.let { put("description", it) }
    put("catalogId", id)

    put(
        "components",
        buildJsonObject {
            for (component in componentDefinitions) {
                put(component.name, component.toSchema().toJsonElement())
            }
        },
    )

    put(
        "functions",
        buildJsonObject {
            for (function in functions) {
                put(function.definition.name, function.definition.toSchema().toJsonElement())
            }
        },
    )

    put(
        "\$defs",
        buildJsonObject {
            for ((defName, defSchema) in collectLocalDefinitions()) {
                put(defName, defSchema.toJsonElement())
            }

            themeSchema?.let { put("theme", it.toJsonElement()) }

            put(
                "anyComponent",
                buildJsonObject {
                    put(
                        "oneOf",
                        JsonArray(
                            componentDefinitions.map { comp ->
                                buildJsonObject { put("\$ref", "#/components/${comp.name}") }
                            }
                        ),
                    )
                    put("discriminator", buildJsonObject { put("propertyName", "component") })
                },
            )

            put(
                "anyFunction",
                buildJsonObject {
                    put(
                        "oneOf",
                        JsonArray(
                            functions.map { func ->
                                buildJsonObject {
                                    put("\$ref", "#/functions/${func.definition.name}")
                                }
                            }
                        ),
                    )
                },
            )
        },
    )
}

/**
 * Traverses the catalog to collect all local schema definitions.
 *
 * A local definition is a reusable subschema declared inside the document's top-level `$defs` map.
 * We extract the definitions of `A2uiCompositeSchema` that have no schemaId (and by that are
 * considered local), and collect them into this section of the schema.
 */
private fun A2uiCoreCatalog.collectLocalDefinitions(): Map<String, A2uiSchema> {
    val localDefs = mutableMapOf<String, A2uiSchema>()
    val visited = mutableSetOf<A2uiSchema>()
    for (component in componentDefinitions) {
        collectLocalDefinitionsFromSchema(component.propertySchema, localDefs, visited)
    }
    for (function in functions) {
        collectLocalDefinitionsFromSchema(function.definition.argumentSchema, localDefs, visited)
    }
    themeSchema?.let { collectLocalDefinitionsFromSchema(it, localDefs, visited) }
    return localDefs
}

private fun collectLocalDefinitionsFromSchema(
    schema: A2uiSchema,
    result: MutableMap<String, A2uiSchema>,
    visited: MutableSet<A2uiSchema>,
): Map<String, A2uiSchema> {
    if (!visited.add(schema)) return result

    when (schema) {
        is A2uiCompositeSchema -> {
            if (schema.schemaId == null) {
                val defName = schema.definitionName
                if (defName != null) {
                    result.putIfAbsent(defName, schema.getDefinition())
                }
                collectLocalDefinitionsFromSchema(schema.getDefinition(), result, visited)
            }
        }
        is A2uiObjectSchema -> {
            for (propSchema in schema.properties.values) {
                collectLocalDefinitionsFromSchema(propSchema, result, visited)
            }
            schema.additionalPropertiesSchema?.let {
                collectLocalDefinitionsFromSchema(it, result, visited)
            }
        }
        is A2uiArraySchema -> {
            schema.items?.let { collectLocalDefinitionsFromSchema(it, result, visited) }
        }
        is A2uiStringSchema,
        is A2uiNumberSchema,
        is A2uiBooleanSchema,
        is A2uiAnySchema,
        is A2uiRefSchema -> {}
    }

    for (keyword in schema.keywords) {
        when (keyword) {
            is A2uiSchemaKeyword.OneOf -> {
                for (subSchema in keyword.schemas) {
                    collectLocalDefinitionsFromSchema(subSchema, result, visited)
                }
            }
            is A2uiSchemaKeyword.AllOf -> {
                for (subSchema in keyword.schemas) {
                    collectLocalDefinitionsFromSchema(subSchema, result, visited)
                }
            }
            is A2uiSchemaKeyword.AnyOf -> {
                for (subSchema in keyword.schemas) {
                    collectLocalDefinitionsFromSchema(subSchema, result, visited)
                }
            }
            is A2uiSchemaKeyword.Not -> {
                collectLocalDefinitionsFromSchema(keyword.schema, result, visited)
            }
            is A2uiSchemaKeyword.Default<*>,
            is A2uiSchemaKeyword.Enum<*>,
            is A2uiSchemaKeyword.Format,
            is A2uiSchemaKeyword.Const<*> -> {}
            is A2uiSchemaKeyword.IfThen -> {
                collectLocalDefinitionsFromSchema(keyword.ifSchema, result, visited)
                collectLocalDefinitionsFromSchema(keyword.thenSchema, result, visited)
                keyword.elseSchema?.let { collectLocalDefinitionsFromSchema(it, result, visited) }
            }
        }
    }

    return result
}
