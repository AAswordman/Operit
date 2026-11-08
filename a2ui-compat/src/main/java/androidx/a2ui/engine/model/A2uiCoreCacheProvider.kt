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

import androidx.a2ui.model.catalog.A2uiFunctionDefinition

/** Provider to retrieve or create a component-scoped cache. */
internal interface A2uiCoreCacheProvider {
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
     * @param factory factory function to construct the cache if missing
     * @return cache instance
     */
    fun <T : Any> getOrCreateFunctionScopedCache(
        componentId: String,
        functionDefinition: A2uiFunctionDefinition,
        factory: () -> T,
    ): T
}
