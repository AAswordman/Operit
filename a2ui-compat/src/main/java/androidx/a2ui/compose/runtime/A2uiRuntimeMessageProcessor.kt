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

import androidx.a2ui.engine.catalog.A2uiCoreCatalog
import androidx.a2ui.engine.processor.A2uiCoreMessageProcessor
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.A2uiMessageProcessor

/**
 * Creates a [A2uiMessageProcessor] tailored for Jetpack Compose.
 *
 * This provisions the underlying core processor with a Compose-backed component registry and
 * reactive data model that leverage the Compose Snapshot state.
 *
 * @param catalogs The list of [A2uiRuntimeCatalog]s defining the component schemas, local
 *   functions, and theme definitions supported by the client app. Each catalog provided must also
 *   implement the underlying [androidx.a2ui.engine.catalog.A2uiCoreCatalog] interface so that it
 *   can be ingested by the core data layer.
 * @param interceptors An optional list of [A2uiActionInterceptor] instances.
 * @return A fully initialized [A2uiCoreMessageProcessor] ready to process incoming messages.
 * @throws IllegalArgumentException If any catalog in the [catalogs] list does not implement the
 *   [androidx.a2ui.engine.catalog.A2uiCoreCatalog] interface.
 */
public fun a2uiRuntimeMessageProcessor(
    catalogs: List<A2uiRuntimeCatalog>,
    interceptors: List<A2uiActionInterceptor> = emptyList(),
): A2uiMessageProcessor {
    val coreCatalogs = ArrayList<A2uiCoreCatalog>(catalogs.size)
    for (i in catalogs.indices) {
        val catalog = catalogs[i]
        coreCatalogs.add(
            catalog as? A2uiCoreCatalog
                ?: throw IllegalArgumentException(
                    "A2uiRuntimeCatalog must be a valid core A2uiCoreCatalog"
                )
        )
    }
    return A2uiCoreMessageProcessor(
        catalogs = coreCatalogs,
        dataModelFactory = { A2uiDataModel() },
        componentRegistryFactory = { A2uiComponentRegistry() },
        actionInterceptors = interceptors,
    )
}
