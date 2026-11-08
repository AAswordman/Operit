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
import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.engine.model.A2uiCoreSurfaceModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * Emits the UI for a successfully resolved A2UI component state into the Compose hierarchy.
 *
 * This composable acts as a dynamic router for recursive component rendering. It extracts the
 * [A2uiCatalog] from the component's underlying surface, looks up the registered Compose-specific
 * implementation for that component type, and delegates rendering to its [A2uiComponent.Content]
 * extension function.
 *
 * @param component The resolved component containing properties and context.
 * @param modifier The [Modifier] to be applied to the component's root layout.
 * @throws IllegalStateException If the underlying catalog does not implement [A2uiCatalog], or if
 *   the component type is not registered in the catalog.
 */
@Composable
public fun A2uiComponent(component: A2uiComponentModel, modifier: Modifier = Modifier) {
    val surface =
        component.surface as? A2uiCoreSurfaceModel
            ?: throw IllegalStateException("Surface must implement A2uiCoreSurfaceModel")
    val catalog =
        surface.catalog as? A2uiCatalog
            ?: throw IllegalStateException("Catalog must implement A2uiCatalog")
    val a2uiComponent =
        catalog.components[component.type]
            ?: throw IllegalStateException(
                "Component with type '${component.type}' is not registered"
            )

    with(a2uiComponent) {
        component.scope.Content(properties = component.properties, modifier = modifier)
    }
}

/**
 * Defines the schema and Jetpack Compose rendering implementation for a single A2UI component.
 *
 * Implementations of this interface bridge the gap between the A2UI protocol and Jetpack Compose
 * UI. They declare the component's structural requirements via [properties] (which are communicated
 * to the during capability negotiation) and map the incoming [A2uiComponentProperties] to actual
 * composable UI elements in [Content].
 */
@Stable
public interface A2uiComponent {

    /**
     * The unique string identifier for this component type (e.g., `"Text"`, `"Button"`).
     *
     * This matches the `"component"` field in the A2UI protocol's component payloads and is used by
     * the framework to route incoming payloads to this specific implementation.
     *
     * Note: This name must be unique within an [A2uiCatalog].
     */
    public val name: String

    /**
     * A semantic description of the component's behavior and purpose.
     *
     * This is included in the generated schema to help the agent understand when and how it should
     * output this component.
     */
    public val description: String

    /**
     * The list of typed properties expected by this component.
     *
     * The properties serve two purposes:
     * 1. To automatically generate the component's JSON schema for the agent.
     * 2. To serve as type-safe tokens for extracting payloads or resolving dynamic data bindings
     *    from [A2uiComponentProperties] at runtime.
     */
    public val properties: List<A2uiProperty<*>>

    /**
     * Determines whether this component is ready to be rendered.
     *
     * This is evaluated during component state resolution to determine if the component should
     * transition from the [androidx.a2ui.compose.runtime.A2uiComponentState.Loading] state to the
     * [androidx.a2ui.compose.runtime.A2uiComponentState.Success] state. The readiness is
     * automatically re-evaluated when any reactive state read in this composable function changes
     * (such as dynamic data bindings observed via `properties.bind(...)`).
     *
     * By default, components are considered instantly ready. Override this method to support
     * progressive rendering—for example, returning `false` until a required dynamic data binding
     * (like an image URL or text string) has been successfully resolved from the surface's data
     * model.
     *
     * @param properties The stable properties wrapper provided by the A2UI protocol.
     * @return `true` if the component is fully ready to be composed, `false` if it is still
     *   loading.
     */
    @Composable
    public fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = true

    /**
     * Emits the UI for this component.
     *
     * This function is invoked within the [A2uiComponentScope], which provides access to extensions
     * for evaluating reactive data bindings, rendering nested children, dispatching user actions,
     * and reporting runtime errors.
     *
     * @param properties The stable properties wrapper from which to extract static payloads or bind
     *   dynamic data paths.
     * @param modifier The [Modifier] applied to the root of this component's layout.
     */
    @Composable
    public fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier)
}
