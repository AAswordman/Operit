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

package androidx.a2ui.engine.model

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.model.catalog.A2uiFunctionDefinition
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.protocol.A2uiExecutionContext

/**
 * Represents the execution environment of a specific component.
 *
 * @param dataPath base path used to resolve relative paths
 * @param componentId unique identifier of the component associated with this context
 * @param catalog catalog containing registered components and functions
 * @param dispatchError callback run when an error is dispatched, receiving the `componentId`
 *   (String) and the `exception` ([A2uiException])
 * @param valueResolver resolver to retrieve state synchronously from the data model
 * @param dynamicEvaluator evaluator for resolving dynamic bindings and client functions
 * @param cacheProvider provider to retrieve or create a component-scoped cache
 */
internal class A2uiCoreExecutionContext(
    override val dataPath: A2uiDataPath,
    private val componentId: String,
    private val catalog: A2uiCoreCatalog,
    private val dispatchError: (A2uiException, String?) -> Unit,
    private val valueResolver: A2uiCoreValueResolver,
    private val dynamicEvaluator: A2uiCoreDynamicEvaluator,
    private val cacheProvider: A2uiCoreCacheProvider,
) : A2uiExecutionContext {
    override fun evaluatePayload(payload: Any?): Any? =
        dynamicEvaluator.evaluate(dataPath, payload, this)

    override fun executeFunction(name: String, args: Map<String, Any>): Any? {
        val catalogFunction = catalog.functions[name]
        if (catalogFunction == null) {
            val exception =
                A2uiException.A2uiRuntimeException("Function '$name' not found in catalog.")
            dispatchError(exception, componentId)
            return null
        }
        return try {
            catalogFunction.execute(args, this)
        } catch (e: A2uiException) {
            dispatchError(e, componentId)
            null
        } catch (e: Exception) {
            val exception =
                A2uiException.A2uiRuntimeException(
                    message = "Function '$name' execution failed",
                    context = mapOf("originalErrorMessage" to e.message),
                )
            dispatchError(exception, componentId)
            null
        }
    }

    override fun getFunctionDefinition(name: String): A2uiFunctionDefinition? =
        catalog.functions[name]?.definition

    override fun resolveValue(path: A2uiDataPath): Any? = valueResolver.resolve(path)

    override fun <T : Any> getOrCreateFunctionScopedCache(
        functionDefinition: A2uiFunctionDefinition,
        factory: () -> T,
    ): T {
        return cacheProvider.getOrCreateFunctionScopedCache(
            componentId,
            functionDefinition,
            factory,
        )
    }
}
