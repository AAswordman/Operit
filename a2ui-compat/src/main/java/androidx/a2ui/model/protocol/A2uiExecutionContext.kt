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

package androidx.a2ui.model.protocol

import androidx.a2ui.model.catalog.A2uiFunctionDefinition

/**
 * Provides environment access, dynamic payload evaluation, and function execution capabilities
 * within the scope of a specific component.
 */
public interface A2uiExecutionContext {
    /** The base data path used to resolve relative paths in this context. */
    public val dataPath: A2uiDataPath

    /**
     * Evaluates a dynamic payload.
     *
     * @param payload payload to evaluate
     * @return evaluated result, or null if evaluation fails
     */
    public fun evaluatePayload(payload: Any?): Any?

    /**
     * Executes a catalog function by name.
     *
     * @param name name of the function to execute
     * @param args arguments to pass to the function
     * @return result of the function execution, or null if execution fails
     */
    public fun executeFunction(name: String, args: Map<String, Any>): Any?

    /**
     * Returns the definition of a catalog function by name, or null if not found.
     *
     * @param name name of the function
     * @return function definition, or null if the function is not found in the catalog
     */
    public fun getFunctionDefinition(name: String): A2uiFunctionDefinition? = null

    /**
     * Resolves a value from the data model at the given path.
     *
     * @param path data path to resolve
     * @return resolved value, or null if value resolution fails
     */
    public fun resolveValue(path: A2uiDataPath): Any?

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
     * @param functionDefinition definition identifying the cache
     * @param factory factory to construct the cache if missing
     * @return cache instance
     */
    public fun <T : Any> getOrCreateFunctionScopedCache(
        functionDefinition: A2uiFunctionDefinition,
        factory: () -> T,
    ): T
}
