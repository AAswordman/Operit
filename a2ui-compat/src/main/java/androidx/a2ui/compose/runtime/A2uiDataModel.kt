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

import androidx.a2ui.engine.platform.A2uiCoreDataModel
import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiException.A2uiRuntimeException
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An A2UI data model implementation for the Jetpack Compose A2UI renderer.
 *
 * This class maps standard Kotlin collections and primitives into Jetpack Compose Snapshot-backed
 * state containers ([SnapshotStateMap], [SnapshotStateSparseList]) to enable highly performant,
 * fine-grained reactivity. It automatically handles nested state hydration and protects against
 * agent hallucinations (e.g., massive array indices leading to OOMs or structural mismatches).
 */
@Stable
internal class A2uiDataModel : A2uiCoreDataModel {

    /** The observable root of the hierarchical data tree. */
    private val rootState = mutableStateOf<Any?>(null)

    /**
     * Synchronizes concurrent updates to ensure thread-safety and prevent snapshot conflicts.
     *
     * The lock is configured as fair to ensure updates are applied in the order they are received.
     */
    private val updateLock = ReentrantLock(true)

    /**
     * Updates or deletes a value at the specified [A2uiDataPath] using strict upsert semantics.
     * - If the path exists, the value is updated.
     * - If the path does not exist, the intermediate containers are hydrated.
     * - If [value] is `null`, the key is removed (or set to `null` in arrays to preserve length).
     *
     * @param path The JSON Pointer path to update.
     * @param value The reactive value to insert, or `null` to delete the key/index.
     * @throws A2uiRuntimeException If a structural hallucination occurs (e.g., using a string key
     *   on an array).
     */
    override fun update(path: A2uiDataPath, value: Any?) {
        // Ensure all incoming standard Maps/Lists become Snapshots
        val snapshotValue = toSnapshotState(value)

        updateLock.withLock {
            Snapshot.withMutableSnapshot {
                val segments = path.segments

                // Root replacement
                if (segments.isEmpty()) {
                    // Allow Maps or `null` (to clear the root), but reject Lists or primitives
                    if (snapshotValue != null && snapshotValue !is SnapshotStateMap<*, *>) {
                        throw A2uiRuntimeException(
                            "Root data model update must be a Map/Object or null.",
                            context = mapOf("path" to "/"),
                        )
                    }
                    rootState.value = snapshotValue
                    return@withMutableSnapshot
                }

                // If the root is null, hydrate it into a SnapshotStateMap
                if (rootState.value !is SnapshotStateMap<*, *>) {
                    // If we're performing a deletion (value == null) but the root doesn't exist,
                    // there is nothing to delete. Abort early.
                    if (snapshotValue == null) return@withMutableSnapshot

                    rootState.value = mutableStateMapOf<String, Any?>()
                }

                var current: Any? = rootState.value

                // Add the new data to the tree with the necessary nested hydration
                for (i in segments.indices) {
                    val isLast = i == segments.size - 1
                    val segment = segments[i]

                    if (isLast) {
                        setChild(current, segment, snapshotValue, segments, i)
                    } else {
                        val nextSegment = segments[i + 1]
                        var next = getChild(current, segment)

                        if (!isSuitableContainer(next, nextSegment, segments, i + 1)) {
                            // If we're performing a deletion (value == null) but the path doesn't
                            // exist or isn't a valid container, there's nothing to delete.
                            if (snapshotValue == null) return@withMutableSnapshot

                            next = createContainerForSegment(nextSegment)
                            setChild(current, segment, next, segments, i)
                        }

                        current = next
                    }
                }
            }
        }

        Snapshot.sendApplyNotifications()
    }

    /**
     * Retrieves the reactive data model value at the specified [A2uiDataPath].
     *
     * @param path The JSON Pointer path to resolve.
     * @return The value at the path, or `null` if the path does not exist.
     */
    override fun get(path: A2uiDataPath): Any? {
        var current = rootState.value
        val segments = path.segments
        for (i in segments.indices) {
            current = getChild(current, segments[i]) ?: return null
        }
        return current
    }

    override fun close() {
        updateLock.withLock { rootState.value = null }
        Snapshot.sendApplyNotifications()
    }

    /**
     * Determines if a JSON pointer segment implies an array structure. Valid array segments are "-"
     * (append) or positive integers.
     *
     * @param segment The path segment to inspect.
     * @return `true` if the segment represents an array index or append operation.
     */
    private fun isArraySegment(segment: String): Boolean {
        if (segment == "-") return true
        val index = segment.toIntOrNull()
        return index != null && index >= 0
    }

    /**
     * Validates whether the current [node] is a structurally compatible container for the
     * [nextSegment]. Maps accept any segment, but arrays enforce strict numeric or append segments.
     *
     * @param node The current state node in the traversal.
     * @param nextSegment The upcoming path segment.
     * @param segments The full list of parsed path segments.
     * @param nextSegmentIndex The index of the upcoming segment (used for error reporting).
     * @return `true` if the node is compatible, `false` if it must be dropped and replaced.
     * @throws A2uiRuntimeException If strict compliance fails (e.g., string key used on a list).
     */
    private fun isSuitableContainer(
        node: Any?,
        nextSegment: String,
        segments: List<String>,
        nextSegmentIndex: Int,
    ): Boolean {
        return when (node) {
            is SnapshotStateMap<*, *> -> true
            is SnapshotStateSparseList -> {
                if (!isArraySegment(nextSegment)) {
                    throw A2uiRuntimeException(
                        message =
                            "Invalid index ('$nextSegment') used on a path segment that refers to an array.",
                        context = mapOf("path" to buildErrorPath(segments, nextSegmentIndex)),
                    )
                }
                true
            }
            else -> false // Nulls or primitives must be overwritten
        }
    }

    /**
     * Instantiates the appropriate Snapshot container based on the implied structure of the
     * [nextSegment].
     *
     * @param nextSegment The upcoming path segment dictating the required container type.
     * @return A new [SnapshotStateSparseList] or [SnapshotStateMap].
     */
    private fun createContainerForSegment(nextSegment: String): Any {
        return if (isArraySegment(nextSegment)) {
            SnapshotStateSparseList()
        } else {
            mutableStateMapOf<String, Any?>()
        }
    }

    /**
     * Safely extracts a child node from the current container using the given [segment].
     *
     * @param node The container to read from.
     * @param segment The key or index to retrieve.
     * @return The child node, or `null` if the container is invalid or the key does not exist.
     */
    private fun getChild(node: Any?, segment: String): Any? =
        when (node) {
            is SnapshotStateMap<*, *> -> node[segment]
            is SnapshotStateSparseList -> {
                segment.toIntOrNull()?.let { if (it in node.indices) node[it] else null }
            }
            else -> null
        }

    /**
     * Assigns or removes a [value] within the target [node] at the specified [segment].
     *
     * @param node The container to mutate.
     * @param segment The key or index to target.
     * @param value The value to insert, or `null` to delete the key / clear the list index.
     * @param segments The full list of parsed path segments.
     * @param segmentIndex The index of the target segment (used for error reporting).
     * @throws A2uiRuntimeException If the index segment is malformed for an array.
     */
    @Suppress("UNCHECKED_CAST")
    private fun setChild(
        node: Any?,
        segment: String,
        value: Any?,
        segments: List<String>,
        segmentIndex: Int,
    ) {
        when (node) {
            is SnapshotStateMap<*, *> -> {
                val map = node as SnapshotStateMap<String, Any?>

                if (value == null) {
                    map.remove(segment)
                    return
                }

                map[segment] = value
            }
            is SnapshotStateSparseList -> {
                // Handle the append index
                if (segment == "-") {
                    // Deleting at the non-existent append index is a no-op
                    if (value != null) {
                        if (node.size >= MAX_ARRAY_SIZE) {
                            throw A2uiRuntimeException(
                                message =
                                    "Cannot append to array. Size exceeds the maximum allowed limit of $MAX_ARRAY_SIZE.",
                                context = mapOf("path" to buildErrorPath(segments, segmentIndex)),
                            )
                        }
                        node.add(value)
                    }
                    return
                }

                val index = segment.toIntOrNull()
                if (index == null || index < 0) {
                    throw A2uiRuntimeException(
                        message =
                            "Invalid index ('$segment') used on a path segment that refers to an array.",
                        context = mapOf("path" to buildErrorPath(segments, segmentIndex)),
                    )
                }

                if (index >= MAX_ARRAY_SIZE) {
                    throw A2uiRuntimeException(
                        message =
                            "Array index ('$index') exceeds the maximum allowed limit of $MAX_ARRAY_SIZE.",
                        context = mapOf("path" to buildErrorPath(segments, segmentIndex)),
                    )
                }

                if (index >= node.size && value == null) {
                    // Attempting to delete an element at an out-of-bounds index is no-op
                    return
                }

                node.setOrExpand(index, value)
            }
        }
    }

    /**
     * Recursively transforms standard Kotlin Maps and Lists into Compose Snapshot states.
     *
     * Includes a fast-path optimization that avoids deep copies if the collection contains only
     * primitives.
     *
     * @param value The data payload received from the protocol parser or from local writes.
     * @return The fully Snapshot-backed equivalent representation.
     */
    private fun toSnapshotState(value: Any?): Any? {
        return when (value) {
            is SnapshotStateMap<*, *>,
            is SnapshotStateSparseList -> value // Fast path: already Snapshots

            is Map<*, *> -> {
                // Check if we need a deep transform or key stringification
                var needsDeepCopy = false
                for ((k, v) in value) {
                    if (k !is String || v is Map<*, *> || v is List<*>) {
                        needsDeepCopy = true
                        break
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val mapToInsert =
                    if (needsDeepCopy) {
                        // Defensive copy and recursive transform.
                        val capacity = (value.size / 0.75f).toInt() + 1
                        val transformedMap = HashMap<String, Any?>(capacity)
                        for ((k, v) in value) {
                            transformedMap[k.toString()] = toSnapshotState(v)
                        }
                        transformedMap
                    } else {
                        // Fast path
                        value as Map<String, Any?>
                    }

                val snapshotMap = mutableStateMapOf<String, Any?>()
                snapshotMap.putAll(mapToInsert)
                snapshotMap
            }

            is List<*> -> {
                // Check if any children need transformation
                var needsDeepCopy = false
                for (i in value.indices) {
                    val it = value[i]
                    if (it is Map<*, *> || it is List<*>) {
                        needsDeepCopy = true
                        break
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val listToInsert =
                    if (needsDeepCopy) {
                        // Defensive copy and recursive transform
                        val transformedList = ArrayList<Any?>(value.size)
                        for (i in value.indices) {
                            transformedList.add(toSnapshotState(value[i]))
                        }
                        transformedList
                    } else {
                        // Fast path
                        value as Collection<Any?>
                    }

                val snapshotList = SnapshotStateSparseList()
                snapshotList.addAll(listToInsert)
                snapshotList
            }

            else -> value // Primitives
        }
    }

    /** Reconstructs the escaped JSON Pointer path up to the specified index for error reporting. */
    private fun buildErrorPath(segments: List<String>, upToIndex: Int): String {
        val builder = StringBuilder()
        for (i in 0..upToIndex) {
            builder.append("/")
            builder.append(segments[i].escapePathSegment())
        }
        return builder.toString()
    }

    /** Escapes the path segment to create a valid JSON Pointer path for errors. */
    private fun String.escapePathSegment() = this.replace("~", "~0").replace("/", "~1")
}

/**
 * An adaptive Snapshot list collection that automatically transitions between an efficient
 * `SnapshotStateList` and a gap-safe [SnapshotStateMap] representation depending on structural
 * updates requested by an agent.
 *
 * This protects the host process from Out-Of-Memory (OOM) crashes if an agent attempts to write to
 * an out-of-bounds array index, since gaps natively resolve to `null` without allocating backing
 * memory.
 */
@Stable
@Suppress("PrimitiveInCollection")
internal class SnapshotStateSparseList : AbstractList<Any?>() {
    private var isSparse by mutableStateOf(false)
    private val denseList = mutableStateListOf<Any?>()
    private var sparseMap: SnapshotStateMap<Int, Any?>? = null
    private var _sparseSize by mutableIntStateOf(0)

    /** The size of the list calculated based on the current mode. */
    override val size: Int
        get() = if (isSparse) _sparseSize else denseList.size

    /** Retrieves the element at [index]. Gaps in a sparse representation safely return `null`. */
    override fun get(index: Int): Any? {
        if (index !in indices) {
            throw IndexOutOfBoundsException("Index: $index, Size: $size")
        }
        return if (isSparse) {
            // Gaps naturally return null, which aligns with JSON array padding
            @Suppress("UNCHECKED_CAST") sparseMap!![index]
        } else {
            denseList[index]
        }
    }

    /** Appends the [element] to the list. */
    fun add(element: Any?): Boolean {
        if (isSparse) {
            val map = sparseMap!!
            map[size] = element
            _sparseSize++
        } else {
            denseList.add(element)
        }
        return true
    }

    /**
     * Appends a collection of [elements] to the end of the list.
     *
     * This only supports the dense mode, as this operation is never expected to be used in sparse
     * mode.
     */
    fun addAll(elements: Collection<Any?>): Boolean {
        if (isSparse) {
            throw UnsupportedOperationException(
                "Adding a collection in sparse mode is not supported"
            )
        }
        return denseList.addAll(elements)
    }

    /**
     * Safely sets the [element] at the specified [index], expanding the list or automatically
     * upgrading to a sparse map representation if the index is significantly out of bounds.
     *
     * @param index The target index.
     * @param element The element to set.
     */
    fun setOrExpand(index: Int, element: Any?) {
        if (!isSparse && index >= size) {
            if (index == size) {
                denseList.add(element)
                return
            } else {
                upgradeToSparse()
            }
        }

        if (isSparse) {
            if (index >= _sparseSize) {
                _sparseSize = index + 1
            }
            sparseMap!![index] = element
        } else {
            denseList[index] = element
        }
    }

    /** Converts the underlying `SnapshotStateList` into a [SnapshotStateMap] to handle gaps. */
    private fun upgradeToSparse() {
        val map = mutableStateMapOf<Int, Any?>()
        for (i in denseList.indices) {
            map[i] = denseList[i]
        }
        _sparseSize = denseList.size
        sparseMap = map
        isSparse = true
        denseList.clear()
    }
}

/** Restrict the maximum size of arrays to avoid OOM due to agent hallucinations. */
private const val MAX_ARRAY_SIZE = 100_000
