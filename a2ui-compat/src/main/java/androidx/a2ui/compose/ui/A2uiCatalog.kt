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

package androidx.a2ui.compose.ui

import androidx.a2ui.compose.runtime.A2uiComponentModel
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.A2uiReadinessEvaluator
import androidx.a2ui.compose.runtime.A2uiRuntimeCatalog
import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.catalog.A2uiCoreCatalogSerializer
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinition
import androidx.a2ui.engine.catalog.A2uiCoreComponentDefinitionCollection
import androidx.a2ui.engine.catalog.obtainSerializer
import androidx.a2ui.model.catalog.A2uiFunction
import androidx.a2ui.model.catalog.A2uiFunctionCollection
import androidx.a2ui.model.protocol.A2uiInlineCatalog
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * A Jetpack Compose implementation of an A2UI component catalog, which defines which
 * [A2uiComponent]s and [A2uiFunction]s are available to the agent for a particular A2UI surface.
 *
 * To instantiate a new catalog, use the `A2uiCatalog` factory function.
 */
@Stable
public sealed interface A2uiCatalog {

    /**
     * The unique identifier for this catalog.
     *
     * This ID is announced to the agent during the capability negotiation phase. It is recommended
     * to use a URI format including a version number (e.g.,
     * `"https://example.com/a2ui/catalog/v1"`) to ensure global uniqueness and facilitate protocol
     * versioning.
     */
    public val id: String

    /** The collection of [A2uiComponent] implementations registered in this catalog. */
    public val components: A2uiComponentCollection

    /** The collection of [A2uiFunction] implementations registered in this catalog. */
    public val functions: A2uiFunctionCollection

    /**
     * The optional JSON schema defining the dynamic theme overrides supported by this catalog.
     *
     * If provided, the agent can send theme properties conforming to this schema (e.g.,
     * `"primaryColor"`) within the `createSurface` message to customize the catalog's visual
     * appearance.
     */
    public val themeSchema: A2uiSchema?

    /**
     * Indicates whether this catalog's full JSON Schema should be serialized inline as part of the
     * capabilities advertisement sent to the agent.
     */
    public val isInline: Boolean
        get() = false
}

/**
 * Creates a new, immutable [A2uiCatalog] mapping a list of [A2uiComponent]s and [A2uiFunction]s to
 * their respective protocol definitions.
 *
 * @param catalogId The unique identifier for this catalog.
 * @param components The list of [A2uiComponent] implementations supported by this catalog.
 * @param functions The optional list of [A2uiFunction]s supported by this catalog. Defaults to an
 *   empty list.
 * @param themeSchema An optional [A2uiSchema] defining the dynamic theme overrides. Defaults to
 *   null.
 * @param isInline Indicates whether this catalog's full JSON Schema should be serialized inline.
 *   Defaults to false.
 * @return A fully initialized and validated [A2uiCatalog].
 * @throws IllegalArgumentException If duplicate component names or duplicate function names are
 *   detected.
 */
public fun A2uiCatalog(
    catalogId: String,
    components: List<A2uiComponent>,
    functions: List<A2uiFunction> = emptyList(),
    themeSchema: A2uiSchema? = null,
    isInline: Boolean = false,
): A2uiCatalog {
    val componentCollection = A2uiComponentCollection(components)
    val functionCollection = A2uiFunctionCollection(functions)

    val componentDefinitions =
        ArrayList<A2uiCoreComponentDefinition>(components.size).apply {
            for (i in components.indices) {
                val component = components[i]
                add(
                    A2uiCoreComponentDefinitionImpl(
                        name = component.name,
                        description = component.description,
                        properties = component.properties,
                    )
                )
            }
        }
    val componentDefinitionCollection = A2uiCoreComponentDefinitionCollection(componentDefinitions)

    return A2uiCatalogImpl(
        catalogId,
        themeSchema,
        componentDefinitionCollection,
        componentCollection,
        functionCollection,
        isInline,
    )
}

/**
 * Creates an [A2uiReadinessEvaluator] that resolves readiness states using the components
 * registered in this catalog.
 *
 * This evaluator delegates to each specific [A2uiComponent.isReady] implementation to determine if
 * a component is ready to transition from a loading state to a success state (e.g., has loaded all
 * its required dynamic data).
 *
 * @return An [A2uiReadinessEvaluator] backed by this catalog.
 */
public fun A2uiCatalog.asReadinessEvaluator(): A2uiReadinessEvaluator {
    return object : A2uiReadinessEvaluator {
        @Composable
        override fun isReady(componentModel: A2uiComponentModel): Boolean {
            val component =
                components[componentModel.type]
                    ?: throw IllegalStateException(
                        "Component with type '${componentModel.type}' is not registered"
                    )
            return with(component) { componentModel.scope.isReady(componentModel.properties) }
        }
    }
}

/**
 * Serializes this catalog to its JSON Schema string representation.
 *
 * @return the serialized catalog JSON Schema string
 * @throws IllegalArgumentException If this catalog does not support serialization.
 */
public fun A2uiCatalog.toJsonSchemaString(): String {
    val impl =
        this as? A2uiCatalogImpl
            ?: throw IllegalArgumentException("The catalog does not support serialization")
    return impl.serializer.jsonSchemaString
}

/**
 * Serializes this catalog to its JSON Schema Map representation.
 *
 * @return the serialized catalog JSON Schema Map
 * @throws IllegalArgumentException If this catalog does not support serialization.
 */
public fun A2uiCatalog.toJsonSchemaMap(): Map<String, Any?> {
    val impl =
        this as? A2uiCatalogImpl
            ?: throw IllegalArgumentException("The catalog does not support serialization")
    return impl.serializer.jsonSchemaMap
}

internal class A2uiCatalogImpl(
    override val id: String,
    override val themeSchema: A2uiSchema?,
    override val componentDefinitions: A2uiCoreComponentDefinitionCollection,
    override val components: A2uiComponentCollection,
    override val functions: A2uiFunctionCollection,
    override val isInline: Boolean = false,
) : A2uiCatalog, A2uiRuntimeCatalog, A2uiCoreCatalog, A2uiInlineCatalog {
    val serializer: A2uiCoreCatalogSerializer by
        lazy(LazyThreadSafetyMode.PUBLICATION) { obtainSerializer() }

    override fun toJsonSchemaMap(): Map<String, Any?> = serializer.jsonSchemaMap

    override fun toJsonSchemaString(): String = serializer.jsonSchemaString
}

private class A2uiCoreComponentDefinitionImpl(
    override val name: String,
    override val description: String,
    properties: List<A2uiProperty<*>>,
) : A2uiCoreComponentDefinition {

    override val propertySchema: A2uiSchema

    init {
        val schemaProperties = HashMap<String, A2uiSchema>(properties.size)
        val requiredProperties = HashSet<String>()
        for (i in properties.indices) {
            val prop = properties[i]
            schemaProperties[prop.key] = prop.schema
            if (prop.isRequired) {
                requiredProperties.add(prop.key)
            }
        }
        propertySchema =
            A2uiObjectSchema(properties = schemaProperties, required = requiredProperties)
    }
}
