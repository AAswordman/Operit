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

import androidx.a2ui.engine.platform.A2uiCoreComponentRegistry
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiException
import androidx.annotation.MainThread
import androidx.collection.MutableScatterMap
import androidx.collection.MutableScatterSet
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An implementation of [A2uiCoreComponentRegistry] for the Jetpack Compose A2UI renderer.
 *
 * This registry manages A2UI components backed by Compose [MutableState]s. It enforces
 * single-snapshot atomic batch updates, and utilizes structural equality checks on the caller's
 * thread prior to applying updates to enable strict referential equality for component properties
 * on the UI thread.
 */
@Stable
internal class A2uiComponentRegistry : A2uiCoreComponentRegistry {

    /** Holds the raw component records, updated by a data layer thread. */
    private val records = MutableScatterMap<String, A2uiComponentRecord>()

    /** Holds the Compose snapshot states for component records created by the UI thread. */
    private val states = MutableScatterMap<String, MutableState<A2uiComponentRecord?>>()

    /**
     * Synchronizes concurrent updates to ensure thread-safety and prevent snapshot conflicts.
     *
     * The lock is configured as fair to ensure updates are applied in the order they are received.
     */
    private val updateLock = ReentrantLock(true)

    /**
     * Synchronizes concurrent [states] and [records] operations.
     *
     * The lock is configured as fair to ensure updates are applied in the order they are received.
     */
    private val registryLock = ReentrantLock(true)

    override fun update(components: List<A2uiComponentPayload>) {
        if (components.isEmpty()) return

        updateLock.withLock {
            val statesToApply = ArrayList<MutableState<A2uiComponentRecord?>>(components.size)
            val recordsToApply = ArrayList<A2uiComponentRecord.Valid>(components.size)
            val seenIds = MutableScatterSet<String>(components.size)

            registryLock.withLock {
                // Iterate backwards so that if a batch contains duplicate IDs, the last payload
                // provided naturally wins.
                for (i in components.indices.reversed()) {
                    val payload = components[i]
                    if (!seenIds.add(payload.id)) {
                        continue // Skip earlier payloads for an ID that was already processed
                    }

                    val existingRecord = records[payload.id] as? A2uiComponentRecord.Valid

                    if (
                        existingRecord == null ||
                            existingRecord.type != payload.type ||
                            existingRecord.properties.raw != payload.properties
                    ) {
                        val newRecord =
                            A2uiComponentRecord.Valid(
                                type = payload.type,
                                properties = A2uiComponentProperties(payload.properties),
                            )

                        records[payload.id] = newRecord

                        // If the UI thread is already observing this component, queue the update
                        val state = states[payload.id]
                        if (state != null) {
                            statesToApply.add(state)
                            recordsToApply.add(newRecord)
                        }
                    }
                }
            }

            if (statesToApply.isNotEmpty()) {
                Snapshot.withMutableSnapshot {
                    for (i in statesToApply.indices) {
                        statesToApply[i].value = recordsToApply[i]
                    }
                }
            }
        }

        Snapshot.sendApplyNotifications()
    }

    override fun reportError(id: String, exception: A2uiException) {
        updateLock.withLock {
            val record = A2uiComponentRecord.Error(exception)

            val stateToUpdate = registryLock.withLock {
                records[id] = record
                states[id]
            }

            stateToUpdate?.let { state ->
                Snapshot.withMutableSnapshot { state.value = record }
            }
        }

        Snapshot.sendApplyNotifications()
    }

    override fun close() {
        updateLock.withLock {
            registryLock.withLock {
                Snapshot.withMutableSnapshot { states.forEachValue { it.value = null } }
                states.clear()
                records.clear()
            }
        }

        Snapshot.sendApplyNotifications()
    }

    /**
     * Retrieves the component record for the given [id], establishing a reactive observation.
     *
     * **Thread Safety Warning:** To prevent Compose snapshot isolation crashes, this method must
     * only be called from the composition thread (typically the main UI thread).
     *
     * @param id The unique identifier of the component to retrieve.
     * @return The reactive component record, or `null` if it has not been loaded yet.
     */
    @MainThread
    internal fun get(id: String): A2uiComponentRecord? {
        val state = registryLock.withLock {
            states.getOrPut(id) {
                // The MutableState is created on the UI thread to ensure its snapshot ID is <= the
                // current active read-only snapshot.
                mutableStateOf(records[id])
            }
        }

        // Reading .value here provides fine-grained reactivity matching exactly to this individual
        // component ID, rather than the entire map structure.
        return state.value
    }
}
