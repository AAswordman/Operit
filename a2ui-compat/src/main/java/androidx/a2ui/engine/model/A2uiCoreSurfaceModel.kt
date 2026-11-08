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

// ConcurrentHashMap is required for atomic compute() operations and is safe for our minSdk 24.
@file:Suppress("BanConcurrentHashMap")

package androidx.a2ui.engine.model

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.engine.processor.fromPayload
import androidx.a2ui.engine.schema.A2uiCoreSchemaValidator
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiUserAction
import java.util.concurrent.ConcurrentHashMap

/**
 * The root domain model for a single active surface.
 *
 * It acts as the owner of that surface's [A2uiCoreDataModel] and [A2uiCoreComponentRegistry],
 * managing updates to these registries and propagating user actions and validation/runtime errors.
 *
 * @param id The unique identifier of this surface.
 * @param catalog The catalog used in this surface.
 * @param dataModel The storage model for this surface's data tree.
 * @param componentRegistry The component registry backing this surface.
 * @param onDispatchAction Callback to handle user actions dispatched from this surface.
 * @param onDispatchError Callback to handle errors dispatched from this surface.
 * @param theme An optional map of theme-specific overrides for this surface.
 * @param shouldSendDataModel If true, indicates the data model of this surface should be appended
 *   as metadata to outgoing messages to the server.
 * @param timeProvider Provider that returns the current epoch time in milliseconds.
 */
public class A2uiCoreSurfaceModel(
    override val id: String,
    public val catalog: A2uiCoreCatalog,
    public val dataModel: A2uiCoreDataModel,
    public val componentRegistry: A2uiCoreComponentRegistry,
    private val onDispatchAction: (A2uiUserAction) -> Unit,
    private val onDispatchError: (A2uiClientErrorMessage) -> Unit,
    public val theme: Map<String, Any?> = emptyMap(),
    @get:JvmName("shouldSendDataModel") public val shouldSendDataModel: Boolean = false,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : A2uiSurfaceModel, A2uiCoreCacheProvider {

    private val dynamicEvaluator = A2uiCoreDynamicEvaluatorImpl
    private val schemaValidator = A2uiCoreSchemaValidator(catalog)

    private val caches = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

    /**
     * Gets or creates a component-scoped cache for [functionDefinition].
     *
     * Each component and [functionDefinition] pair gets a separate cache that persists across
     * function invocations and data model updates. Ideal for storing the results of heavy
     * operations that do *not* rely on data model values (e.g., static metadata, parsed templates,
     * compiled regexes, etc.).
     *
     * Warning: The cache does not refresh upon data model changes. Caching values that are based on
     * the data model will result in a stale cache.
     *
     * @param componentId unique identifier of the component
     * @param functionDefinition definition identifying the cache
     * @param factory factory function to create the cache if missing
     * @return cache instance
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrCreateFunctionScopedCache(
        componentId: String,
        functionDefinition: A2uiFunctionDefinition,
        factory: () -> T,
    ): T {
        val componentCaches = caches.computeIfAbsent(componentId) { ConcurrentHashMap() }
        val existing = componentCaches[functionDefinition.name]
        if (existing != null) {
            return existing as T
        }

        return componentCaches.computeIfAbsent(functionDefinition.name) { factory() } as T
    }

    /**
     * Applies a data model update to this surface.
     *
     * @param path The absolute JSON pointer path to update.
     * @param value The new value to store at the path.
     */
    internal fun updateDataModel(path: A2uiDataPath, value: Any?) {
        dataModel.update(path, value)
    }

    /**
     * Dispatches a user action from this surface to the registered action handler callback.
     *
     * @param componentId The unique identifier of the component that was interacted with.
     * @param actionDefinition A map containing details about the action (e.g., name, context).
     */
    public fun dispatchAction(componentId: String, actionDefinition: Map<String, Any?>) {
        val action = A2uiUserAction.fromPayload(id, componentId, timeProvider(), actionDefinition)
        onDispatchAction(action)
    }

    /**
     * Reports a rendering or evaluation failure to the component registry and dispatches the error
     * to the registered error handler callback.
     *
     * **Note:** The [componentId] is exclusively used to mark the error on the local component
     * registry for local UI error boundary rendering. It is **not** appended or injected into the
     * exception context or path sent to the server. Callers must ensure the [exception] itself is
     * initialized with all the relevant information, including the component id if relevant.
     *
     * @param exception The exception describing the failure.
     * @param componentId The unique identifier of the component that encountered the error. Null if
     *   the error is surface-level.
     */
    @JvmOverloads
    public fun dispatchError(exception: A2uiException, componentId: String? = null) {
        if (componentId != null) {
            componentRegistry.reportError(componentId, exception)
        }
        val error =
            A2uiClientErrorMessage(
                code = exception.code,
                surfaceId = id,
                message = exception.message ?: "",
                context = exception.context,
            )
        onDispatchError(error)
    }

    /**
     * Evaluates a dynamic payload for a specific component.
     *
     * @param componentId unique identifier of the UI component
     * @param valueResolver resolver to retrieve values from the data model
     * @param dataPath base path used to resolve relative paths
     * @param payload payload to evaluate
     * @return evaluated result, or null if evaluation fails
     */
    public fun evaluatePayload(
        componentId: String,
        valueResolver: A2uiCoreValueResolver,
        dataPath: A2uiDataPath,
        payload: Any?,
    ): Any? {
        val executionContext =
            A2uiCoreExecutionContext(
                dataPath = dataPath,
                componentId = componentId,
                catalog = catalog,
                dispatchError = ::dispatchError,
                valueResolver = valueResolver,
                dynamicEvaluator = dynamicEvaluator,
                cacheProvider = this,
            )
        return executionContext.evaluatePayload(payload)
    }

    /**
     * Applies a data model update to this surface.
     *
     * @param path A string representing the absolute JSON pointer path to update.
     * @param value The new value to store at the path.
     */
    internal fun updateDataModel(path: String, value: Any?) {
        try {
            validateDataPath(path)
            dataModel.update(A2uiDataPath(path), value)
        } catch (e: A2uiException.A2uiValidationException) {
            dispatchError(exception = e)
        }
    }

    /**
     * Updates the registry with a batch of components.
     *
     * @param payloads The list of components to apply.
     */
    internal fun updateComponents(payloads: List<A2uiComponentPayload>) {
        val validPayloads = mutableListOf<A2uiComponentPayload>()
        for (payload in payloads) {
            try {
                validateComponent(payload)
                caches.remove(payload.id)
                validPayloads.add(payload)
            } catch (e: A2uiException) {
                dispatchError(exception = e, componentId = payload.id)
            }
        }
        componentRegistry.update(validPayloads)
    }

    /** Cleans up resources and active memory. */
    internal fun dispose() {
        dataModel.close()
        componentRegistry.close()
        caches.clear()
    }

    private fun validateDataPath(path: String) {
        if (!path.startsWith("/")) {
            throw A2uiException.A2uiValidationException(
                "Data model update path must be absolute",
                path,
            )
        }

        for (i in path.indices) {
            if (path[i] == '~') {
                if (i == path.lastIndex || (path[i + 1] != '0' && path[i + 1] != '1')) {
                    throw A2uiException.A2uiValidationException(
                        "Invalid escape sequence in path",
                        path,
                    )
                }
            }
        }
    }

    private fun validateComponent(payload: A2uiComponentPayload) {
        val basePath = "/components/${payload.id}"
        val componentDef =
            catalog.componentDefinitions[payload.type]
                ?: throw A2uiException.A2uiValidationException(
                    "Component type '${payload.type}' not found in catalog",
                    basePath,
                )
        schemaValidator.validateSchema(
            payload.properties,
            componentDef.propertySchema,
            basePath = basePath,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiCoreSurfaceModel) return false
        return (id == other.id) &&
            (catalog == other.catalog) &&
            (dataModel == other.dataModel) &&
            (componentRegistry == other.componentRegistry) &&
            (theme == other.theme) &&
            (shouldSendDataModel == other.shouldSendDataModel)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = (31 * result) + catalog.hashCode()
        result = (31 * result) + dataModel.hashCode()
        result = (31 * result) + componentRegistry.hashCode()
        result = (31 * result) + theme.hashCode()
        result = (31 * result) + shouldSendDataModel.hashCode()
        return result
    }

    override fun toString(): String {
        return "A2uiSurfaceModel(id='$id', catalog=$catalog, theme=$theme, shouldSendDataModel=$shouldSendDataModel)"
    }
}
