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

@file:JvmName("A2uiFunctionCollectionKt")

package androidx.a2ui.model.catalog

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.collection.emptyScatterMap

/**
 * An immutable, indexed collection of [A2uiFunction]s.
 *
 * Provides list-based access that enables iterator-free iteration using indices, as well as lookup
 * by function name using the index operator.
 */
public sealed interface A2uiFunctionCollection : List<A2uiFunction> {
    /**
     * Retrieves a function by its unique name.
     *
     * @param name The name of the function.
     * @return The function, or `null` if it is not registered in this collection.
     */
    public operator fun get(name: String): A2uiFunction?
}

/**
 * Creates an immutable [A2uiFunctionCollection] from a list of [A2uiFunction]s.
 *
 * @param functions The list of functions to include.
 * @return An initialized [A2uiFunctionCollection].
 * @throws IllegalArgumentException If duplicate function names are detected.
 */
@JvmOverloads
public fun A2uiFunctionCollection(
    functions: List<A2uiFunction> = emptyList()
): A2uiFunctionCollection {
    if (functions.isEmpty()) {
        return A2uiFunctionCollectionImpl(emptyList(), emptyScatterMap())
    }
    // Explicitly create an ArrayList to guarantee fast index-based iteration and ensure the
    // collection cannot be mutated by the caller.
    val safeFunctions = ArrayList(functions)
    val map = MutableScatterMap<String, A2uiFunction>(safeFunctions.size)
    for (i in safeFunctions.indices) {
        val func = safeFunctions[i]
        val name = func.definition.name
        require(map.put(name, func) == null) { "Duplicate function registered for name '$name'." }
    }
    return A2uiFunctionCollectionImpl(safeFunctions, map)
}

internal class A2uiFunctionCollectionImpl(
    private val functions: List<A2uiFunction>,
    private val functionMap: ScatterMap<String, A2uiFunction>,
) : A2uiFunctionCollection {

    override val size: Int
        get() = functions.size

    override fun isEmpty(): Boolean = functions.isEmpty()

    override fun contains(element: A2uiFunction): Boolean = functions.contains(element)

    override fun containsAll(elements: Collection<A2uiFunction>): Boolean =
        functions.containsAll(elements)

    override fun get(index: Int): A2uiFunction = functions[index]

    override fun get(name: String): A2uiFunction? = functionMap[name]

    override fun indexOf(element: A2uiFunction): Int = functions.indexOf(element)

    override fun lastIndexOf(element: A2uiFunction): Int = functions.lastIndexOf(element)

    override fun iterator(): Iterator<A2uiFunction> = functions.iterator()

    override fun listIterator(): ListIterator<A2uiFunction> = functions.listIterator()

    override fun listIterator(index: Int): ListIterator<A2uiFunction> =
        functions.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<A2uiFunction> =
        functions.subList(fromIndex, toIndex)

    override fun equals(other: Any?): Boolean = functions == other

    override fun hashCode(): Int = functions.hashCode()

    override fun toString(): String = functions.toString()
}
