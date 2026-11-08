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

import androidx.a2ui.model.protocol.A2uiDataPath

/** A storage interface for the JSON data tree. */
public interface A2uiCoreDataModel : AutoCloseable {
    /**
     * Replaces the value at the given absolute path.
     *
     * @param path The absolute JSON pointer path to the data point to update.
     * @param value The new value to store at the given path. The type must correspond to a standard
     *   JSON data type: [String], [Number], [Boolean], `null`, `Map<String, Any?>` (for JSON
     *   objects), or `List<Any?>` (for JSON arrays).
     */
    public fun update(path: A2uiDataPath, value: Any?)

    /**
     * Retrieves the value at the given absolute path.
     *
     * @param path The absolute JSON pointer path to the data point to retrieve.
     * @return The value stored at the given path, or `null` if the path does not exist or has no
     *   value. The returned type will correspond to a standard JSON data type: [String], [Number],
     *   [Boolean], `null`, `Map<String, Any?>` (for JSON objects), or `List<Any?>` (for JSON
     *   arrays).
     */
    public operator fun get(path: A2uiDataPath): Any?

    /** Cleans up resources (e.g., clearing registries to prevent memory leaks). */
    public override fun close()
}
