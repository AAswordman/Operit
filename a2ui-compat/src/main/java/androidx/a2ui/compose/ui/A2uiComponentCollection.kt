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

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.collection.emptyScatterMap
import androidx.compose.runtime.Immutable

/**
 * An immutable, indexed collection of [A2uiComponent]s registered within an [A2uiCatalog].
 *
 * Provides list-based access that enables iterator-free iteration using indices, as well as lookup
 * by component name using the index operator.
 */
@Immutable
public sealed interface A2uiComponentCollection : List<A2uiComponent> {
    /**
     * Retrieves an [A2uiComponent] implementation by its unique type name.
     *
     * @param name The unique type name of the component (e.g., `"Text"`, `"Button"`).
     * @return The [A2uiComponent] implementation, or `null` if no component with [name] is
     *   registered in this collection.
     */
    public operator fun get(name: String): A2uiComponent?
}

/**
 * Creates an immutable [A2uiComponentCollection] from a list of [A2uiComponent]s.
 *
 * @param components The list of components to include.
 * @return An initialized [A2uiComponentCollection].
 * @throws IllegalArgumentException If duplicate component names are detected.
 */
@JvmOverloads
public fun A2uiComponentCollection(
    components: List<A2uiComponent> = emptyList()
): A2uiComponentCollection {
    if (components.isEmpty()) {
        return A2uiComponentCollectionImpl(emptyList(), emptyScatterMap())
    }
    // Explicitly create an ArrayList to guarantee fast index-based iteration and ensure the
    // collection cannot be mutated by the caller.
    val safeComponents = ArrayList(components)
    val componentMap = MutableScatterMap<String, A2uiComponent>(safeComponents.size)
    for (i in safeComponents.indices) {
        val component = safeComponents[i]
        val name = component.name
        require(componentMap.put(name, component) == null) {
            "Duplicate component registered for name '$name'. " +
                "Catalogs must have unique component types."
        }
    }
    return A2uiComponentCollectionImpl(safeComponents, componentMap)
}

internal class A2uiComponentCollectionImpl(
    private val components: List<A2uiComponent>,
    private val componentMap: ScatterMap<String, A2uiComponent>,
) : A2uiComponentCollection {

    override val size: Int
        get() = components.size

    override fun isEmpty(): Boolean = components.isEmpty()

    override fun contains(element: A2uiComponent): Boolean = components.contains(element)

    override fun containsAll(elements: Collection<A2uiComponent>): Boolean =
        components.containsAll(elements)

    override fun get(index: Int): A2uiComponent = components[index]

    override fun get(name: String): A2uiComponent? = componentMap[name]

    override fun indexOf(element: A2uiComponent): Int = components.indexOf(element)

    override fun lastIndexOf(element: A2uiComponent): Int = components.lastIndexOf(element)

    override fun iterator(): Iterator<A2uiComponent> = components.iterator()

    override fun listIterator(): ListIterator<A2uiComponent> = components.listIterator()

    override fun listIterator(index: Int): ListIterator<A2uiComponent> =
        components.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<A2uiComponent> =
        components.subList(fromIndex, toIndex)

    override fun equals(other: Any?): Boolean = components == other

    override fun hashCode(): Int = components.hashCode()

    override fun toString(): String = components.toString()
}
