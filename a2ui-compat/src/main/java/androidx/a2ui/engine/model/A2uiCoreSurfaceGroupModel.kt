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

import androidx.a2ui.model.protocol.A2uiClientDataModel
import androidx.a2ui.model.protocol.A2uiDataPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The central manager for all active surfaces on the client.
 *
 * It exposes an observable list of active [A2uiCoreSurfaceModel]s and acts as a registry to resolve
 * active surfaces by their unique ID.
 *
 * Concurrency design: This class is thread-safe for concurrent operations across different surface
 * IDs (e.g., adding or deleting different surfaces in parallel). It relies on external
 * sequentialization (such as sequential actors or single-threaded queues) to ensure that operations
 * targeting the same surface ID are executed sequentially.
 */
public class A2uiCoreSurfaceGroupModel internal constructor() {
    private val _activeSurfaces = MutableStateFlow<List<A2uiCoreSurfaceModel>>(emptyList())

    /** Exposes the currently active surfaces to the host UI framework. */
    public val activeSurfaces: StateFlow<List<A2uiCoreSurfaceModel>> = _activeSurfaces.asStateFlow()

    /**
     * Resolves a specific surface model by its ID.
     *
     * @param id The unique identifier of the surface.
     * @return The active [A2uiCoreSurfaceModel], or `null` if not found.
     */
    internal fun getSurface(id: String): A2uiCoreSurfaceModel? {
        return _activeSurfaces.value.find { it.id == id }
    }

    /**
     * Adds a surface model. If a surface with the same ID already exists, an exception will be
     * thrown.
     *
     * To prevent resource race conditions, this method MUST be called from a context that
     * guarantees sequential execution for any given surface ID.
     *
     * @param surface The [A2uiCoreSurfaceModel] to add.
     */
    internal fun add(surface: A2uiCoreSurfaceModel) {
        require(getSurface(surface.id) == null) { "Surface '${surface.id}' already exists." }
        _activeSurfaces.update { current -> current + surface }
    }

    /**
     * Deletes and disposes of a surface model by its ID.
     *
     * To prevent resource race conditions, this method MUST be called from a context that
     * guarantees sequential execution for any given surface ID.
     *
     * @param id The unique identifier of the surface to delete.
     */
    internal fun delete(id: String) {
        var removedSurface: A2uiCoreSurfaceModel? = null
        _activeSurfaces.update { current ->
            removedSurface = null
            val newList =
                buildList(current.size) {
                    for (item in current) {
                        if (item.id == id) {
                            removedSurface = item
                        } else {
                            add(item)
                        }
                    }
                }
            if (removedSurface != null) newList else current
        }
        removedSurface?.dispose()
    }

    /**
     * Clears all surfaces from the group and returns them so they can be disposed.
     *
     * While this method safely updates the active surface list concurrently, the caller is
     * responsible for ensuring that the returned surfaces are disposed of in a context that
     * respects the sequential execution requirement for each surface ID.
     */
    internal fun clear(): List<A2uiCoreSurfaceModel> {
        var removedSurfaces: List<A2uiCoreSurfaceModel> = emptyList()
        _activeSurfaces.update { current ->
            removedSurfaces = current
            emptyList()
        }
        return removedSurfaces
    }

    /**
     * Aggregates the data model for all active surfaces that have `sendDataModel` enabled.
     *
     * @return The populated [A2uiClientDataModel], or `null` if no surfaces currently require data
     *   synchronization.
     */
    internal fun getClientDataModel(): A2uiClientDataModel? {
        val activeSurfaces = _activeSurfaces.value
        val surfacesToSend = mutableMapOf<String, Any?>()

        for (surface in activeSurfaces) {
            if (surface.shouldSendDataModel) {
                val rootData = surface.dataModel[A2uiDataPath("/")]
                surfacesToSend[surface.id] = rootData
            }
        }

        if (surfacesToSend.isEmpty()) {
            return null
        }

        return A2uiClientDataModel(surfacesToSend)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiCoreSurfaceGroupModel) return false
        return _activeSurfaces.value == other._activeSurfaces.value
    }

    override fun hashCode(): Int {
        return _activeSurfaces.value.hashCode()
    }

    override fun toString(): String {
        return "A2uiSurfaceGroupModel(activeSurfaces=${_activeSurfaces.value})"
    }
}
