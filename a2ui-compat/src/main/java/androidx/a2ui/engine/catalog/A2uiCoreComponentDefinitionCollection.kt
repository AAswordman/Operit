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

@file:JvmName("A2uiCoreComponentDefinitionCollectionKt")

package androidx.a2ui.engine.catalog

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import androidx.collection.emptyScatterMap

/**
 * An immutable, indexed collection of [A2uiCoreComponentDefinition]s registered within an
 * [A2uiCoreCatalog].
 *
 * Provides list-based access that enables iterator-free iteration using indices, as well as lookup
 * by component name using the index operator.
 */
public sealed interface A2uiCoreComponentDefinitionCollection : List<A2uiCoreComponentDefinition> {
    /**
     * Retrieves a component definition by its unique name.
     *
     * @param name The name of the component.
     * @return The component definition, or `null` if it is not registered in this collection.
     */
    public operator fun get(name: String): A2uiCoreComponentDefinition?
}

/**
 * Creates an immutable [A2uiCoreComponentDefinitionCollection] from a list of
 * [A2uiCoreComponentDefinition]s.
 *
 * @param definitions The list of component definitions to include.
 * @return An initialized [A2uiCoreComponentDefinitionCollection].
 * @throws IllegalArgumentException If duplicate component definition names are detected.
 */
@JvmOverloads
public fun A2uiCoreComponentDefinitionCollection(
    definitions: List<A2uiCoreComponentDefinition> = emptyList()
): A2uiCoreComponentDefinitionCollection {
    if (definitions.isEmpty()) {
        return A2uiCoreComponentDefinitionCollectionImpl(emptyList(), emptyScatterMap())
    }
    val safeDefinitions = ArrayList(definitions)
    val map = MutableScatterMap<String, A2uiCoreComponentDefinition>(safeDefinitions.size)
    for (i in safeDefinitions.indices) {
        val def = safeDefinitions[i]
        val name = def.name
        require(map.put(name, def) == null) {
            "Duplicate component definition registered for name '$name'."
        }
    }
    return A2uiCoreComponentDefinitionCollectionImpl(safeDefinitions, map)
}

internal class A2uiCoreComponentDefinitionCollectionImpl(
    private val definitions: List<A2uiCoreComponentDefinition>,
    private val definitionMap: ScatterMap<String, A2uiCoreComponentDefinition>,
) : A2uiCoreComponentDefinitionCollection {

    override val size: Int
        get() = definitions.size

    override fun isEmpty(): Boolean = definitions.isEmpty()

    override fun contains(element: A2uiCoreComponentDefinition): Boolean =
        definitions.contains(element)

    override fun containsAll(elements: Collection<A2uiCoreComponentDefinition>): Boolean =
        definitions.containsAll(elements)

    override fun get(index: Int): A2uiCoreComponentDefinition = definitions[index]

    override fun get(name: String): A2uiCoreComponentDefinition? = definitionMap[name]

    override fun indexOf(element: A2uiCoreComponentDefinition): Int = definitions.indexOf(element)

    override fun lastIndexOf(element: A2uiCoreComponentDefinition): Int =
        definitions.lastIndexOf(element)

    override fun iterator(): Iterator<A2uiCoreComponentDefinition> = definitions.iterator()

    override fun listIterator(): ListIterator<A2uiCoreComponentDefinition> =
        definitions.listIterator()

    override fun listIterator(index: Int): ListIterator<A2uiCoreComponentDefinition> =
        definitions.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<A2uiCoreComponentDefinition> =
        definitions.subList(fromIndex, toIndex)

    override fun equals(other: Any?): Boolean = definitions == other

    override fun hashCode(): Int = definitions.hashCode()

    override fun toString(): String = definitions.toString()
}
