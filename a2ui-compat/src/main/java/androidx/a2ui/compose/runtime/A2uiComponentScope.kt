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

import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * The receiver scope provided to an A2UI component for rendering.
 *
 * This scope provides a component-specific context, allowing the component to evaluate reactive
 * data bindings, render nested children, dispatch user actions, and report runtime errors.
 */
@Stable
public sealed interface A2uiComponentScope {

    /**
     * Resolves the current [A2uiComponentState] of a component by its unique ID and subscribes for
     * future updates.
     *
     * @param id The unique ID of the component to resolve.
     * @param baseDataPath An optional relative or absolute data path to override the component's
     *   data context.
     * @return The [A2uiComponentState] of the requested component.
     */
    @Composable
    public fun observeA2uiComponentState(
        id: String,
        baseDataPath: String? = null,
    ): A2uiComponentState

    /**
     * Dispatches an A2UI action (such as a server event or local function call) as defined in the
     * component's properties.
     *
     * @param actionPayload The action definition to dispatch, typically extracted from the
     *   component's properties.
     */
    public fun dispatchAction(actionPayload: Map<String, Any?>)

    /**
     * Reports a runtime error that occurred during the evaluation or rendering of this component.
     *
     * @param exception The exception detailing the error.
     */
    public fun reportError(exception: A2uiException)

    /**
     * Evaluates a dynamic property against the surface's reactive data model and subscribes the
     * component to future updates.
     *
     * This method resolves various payload types sent by the agent:
     * - Literal values.
     * - Data model bindings via JSON pointer paths (e.g., `{"path": "/user/name"}`).
     * - Local client-side function executions (e.g., `{"call": "formatString", ...}`).
     *
     * If the agent provides an invalid payload or a type mismatch occurs, this method returns
     * `null` to prevent crashes and automatically dispatches a runtime error back to the agent,
     * facilitating an explicit feedback loop for self-correction. During progressive rendering
     * (when the required data has not yet arrived to the data model), this will also evaluate to
     * `null`.
     *
     * @param property The [DynamicA2uiProperty] definition to evaluate and bind.
     * @return The fully evaluated value cast to [T], or `null` if the property is missing, the data
     *   is pending, or an evaluation/type error occurred.
     */
    @Composable
    public fun <T : Any> A2uiComponentProperties.bind(property: DynamicA2uiProperty<T>): T?

    /**
     * Resolves a structural list of child component references based on the provided property and
     * subscribes the component to future updates.
     *
     * This method supports both static and dynamic component hierarchies as defined by the
     * protocol:
     * - Static Lists: Direct arrays of component IDs.
     * - Dynamic Templates: An object defining a `componentId` and a data `path` (e.g., `{"path":
     *   "/items", "componentId": "item_template"}`). The list will reactively expand or contract
     *   based on the underlying data model array, injecting the correct relative base data paths
     *   into the resulting component references.
     *
     * Note: This method only resolves the references. It does not observe the state of the child
     * components themselves. The returned component references can be used to call
     * [observeA2uiComponentState] to observe and render the children. This separation allows for
     * lazy observation of child states in lazy layouts like `LazyColumn`.
     *
     * If the agent hallucinates a malformed structure or points a template to a non-list data node,
     * an error is automatically dispatched to the agent for self-correction and `null` is returned.
     *
     * @param property The [ChildListA2uiProperty] definition to evaluate and bind.
     * @return A list of resolved [A2uiComponentReference]s ready to be rendered, or `null` if the
     *   property is missing or malformed.
     */
    @Suppress("NullableCollection") // Need to distinguish empty lists and null
    @Composable
    public fun A2uiComponentProperties.bindChildReferences(
        property: ChildListA2uiProperty
    ): List<A2uiComponentReference>?

    /**
     * Establishes a two-way data binding by providing a stable callback that updates the underlying
     * data model for the given dynamic property.
     *
     * This method is useful for interactive components (like text fields or checkboxes) that must
     * write local user input back to the surface's reactive data model. The component may pass
     * `null` to the returned lambda to erase the data in the data model for the specified property.
     *
     * If the agent binds the property to a writable JSON pointer path (e.g., `{"path":
     * "/form/name"}`), this returns a lambda that mutates the model at that path. If the agent
     * instead provides a read-only payload (such as a literal string or a function call), this
     * method returns `null`. Components should utilize a `null` result to degrade into a read-only
     * or disabled state, preventing user input that cannot be synchronized.
     *
     * @param property The [DynamicA2uiProperty] to create a two-way binding updater for.
     * @return A stable lambda that writes updates back to the data model, or `null` if the property
     *   is not bound to a writable data path.
     */
    @Composable
    public fun <T : Any> A2uiComponentProperties.bindUpdater(
        property: DynamicA2uiProperty<T>
    ): ((T?) -> Unit)?
}

/**
 * Resolves the current [A2uiComponentState] of a component by its [A2uiComponentReference] and
 * subscribes for future updates.
 *
 * @param reference The unique [A2uiComponentReference] of the component to resolve.
 * @return The [A2uiComponentState] of the requested component.
 */
@Composable
public fun A2uiComponentScope.observeA2uiComponentState(
    reference: A2uiComponentReference
): A2uiComponentState = observeA2uiComponentState(reference.id, reference.baseDataPath)

/**
 * Intercepts actions dispatched by descendant components within [content].
 *
 * Useful for container components (such as modals) that handle actions from child triggers (like
 * buttons or cards) to update local UI state instead of dispatching to the surface.
 *
 * ### Interceptor Chaining
 * Interceptors chain from the innermost (deepest child) to the outermost (ancestor) in the
 * composition tree:
 * - If [onIntercept] returns `true`, the action is consumed immediately; outer interceptors and the
 *   underlying surface will not receive the action.
 * - If [onIntercept] returns `false`, the action falls back to the parent interceptor.
 * - If all chained interceptors return `false`, the action dispatches to the surface.
 *
 * @param onIntercept The callback invoked when a descendant dispatches an action. Return `true` to
 *   consume the action, or `false` to pass it to parent interceptors or the surface.
 * @param content The composable content to apply the interceptor to
 */
@Composable
public fun A2uiComponentScope.ProvideActionInterceptor(
    onIntercept: (actionPayload: Map<String, Any?>) -> Boolean,
    content: @Composable () -> Unit,
) {
    val parentInterceptor = LocalA2uiActionInterceptor.current

    val chainedInterceptor: A2uiLocalActionInterceptor =
        remember(parentInterceptor, onIntercept) {
            if (parentInterceptor == null) {
                onIntercept
            } else {
                // Chain interceptors: try the new interceptor first, then fallback to the parent
                { payload -> onIntercept(payload) || parentInterceptor(payload) }
            }
        }

    CompositionLocalProvider(
        LocalA2uiActionInterceptor provides chainedInterceptor,
        content = content,
    )
}

/**
 * Intercepts an action dispatched within an A2UI component tree.
 *
 * Returns `true` if the action was consumed locally, or `false` to propagate it further.
 */
internal typealias A2uiLocalActionInterceptor = (actionPayload: Map<String, Any?>) -> Boolean

/**
 * CompositionLocal containing the active [A2uiLocalActionInterceptor] for the current hierarchy.
 */
internal val LocalA2uiActionInterceptor = compositionLocalOf<A2uiLocalActionInterceptor?> { null }
