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

import androidx.a2ui.model.protocol.A2uiDataPath

/**
 * A callback interface provided by the framework layer during evaluation. Allows the core to read
 * from the data model synchronously while enabling the framework to track data accesses for
 * reactive updates.
 */
public fun interface A2uiCoreValueResolver {
    /**
     * Resolves a value from the data model at the given path.
     *
     * @param path The data path to resolve.
     * @return The resolved value or null if the given path does not exist in the data model.
     */
    public fun resolve(path: A2uiDataPath): Any?
}
