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

import androidx.a2ui.compose.runtime.A2uiRuntimeCatalog
import androidx.a2ui.compose.runtime.a2uiRuntimeMessageProcessor
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.A2uiMessageProcessor

/**
 * Creates a [A2uiMessageProcessor] configured for Jetpack Compose UI.
 *
 * This provisions the underlying core processor with a Compose-backed component registry and
 * reactive data model that leverage the Compose Snapshot state.
 *
 * @param catalogs The list of component catalogs supported by the client.
 * @param interceptors An optional list of core [A2uiActionInterceptor]s to process actions.
 * @return A fully initialized [A2uiMessageProcessor].
 * @throws IllegalArgumentException If any catalog in the [catalogs] list does not implement the
 *   [androidx.a2ui.compose.runtime.A2uiRuntimeCatalog] interface.
 */
public fun A2uiMessageProcessor(
    catalogs: List<A2uiCatalog>,
    interceptors: List<A2uiActionInterceptor> = emptyList(),
): A2uiMessageProcessor {
    val runtimeCatalogs = ArrayList<A2uiRuntimeCatalog>(catalogs.size)
    for (i in catalogs.indices) {
        val catalog = catalogs[i]
        runtimeCatalogs.add(
            catalog as? A2uiRuntimeCatalog
                ?: throw IllegalArgumentException(
                    "Each A2uiCatalog must implement A2uiRuntimeCatalog"
                )
        )
    }
    return a2uiRuntimeMessageProcessor(catalogs = runtimeCatalogs, interceptors = interceptors)
}
