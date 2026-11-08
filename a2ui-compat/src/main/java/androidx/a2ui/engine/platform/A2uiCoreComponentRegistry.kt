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

package androidx.a2ui.engine.platform

import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiException

/**
 * A registry for storing UI components. Host frameworks back this with native reactive states
 * (e.g., SnapshotStateMap in Compose).
 */
public interface A2uiCoreComponentRegistry : AutoCloseable {
    /**
     * Inserts or replaces UI components in the registry.
     *
     * @param components The list of UI components to insert or update.
     */
    public fun update(components: List<A2uiComponentPayload>)

    /**
     * Reports an error associated with a specific UI component.
     *
     * @param id The unique identifier of the component that encountered the error.
     * @param exception The exception describing the component error.
     */
    public fun reportError(id: String, exception: A2uiException)

    /** Instantly clears all components from the registry for deterministic teardown. */
    public override fun close()
}
