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

import androidx.compose.runtime.Immutable

/**
 * An immutable wrapper around the raw component property map supplied by the A2UI protocol.
 *
 * This class represents a frozen snapshot of a component's payload. It provides mechanisms to
 * extract property values and guarantees efficient O(1) equality checking to optimize Compose
 * recompositions, as structural equality is checked by [A2uiComponentRegistry] before creating a
 * new instance of [A2uiComponentProperties].
 */
@Immutable
public class A2uiComponentProperties internal constructor(internal val raw: Map<String, Any?>) {

    /**
     * Retrieves the property value for a given static property.
     *
     * Note: This does not evaluate dynamic expressions or bindings.
     *
     * @param property The static property to retrieve.
     * @return The property value, or `null` if the property is missing and is not required.
     * @throws IllegalStateException If the property is required but missing, or if the value is of
     *   an invalid type. These cases indicate a mismatch in the property definition, as incoming
     *   payloads are expected to be validated before reaching the UI.
     */
    public operator fun <T> get(property: StaticA2uiProperty<T>): T? {
        if (property.isRequired && !raw.contains(property.key)) {
            throw IllegalStateException("Required static property '${property.key}' is missing.")
        }
        val value = raw[property.key] ?: return null

        return property.safeCast(value)
            ?: throw IllegalStateException(
                "Type mismatch for static property '${property.key}'. " +
                    "Expected a value compatible with ${property.javaClass.simpleName}, " +
                    "but received ${value.javaClass.simpleName}. "
            )
    }

    /**
     * Checks whether the specified [property] is present in the component's payload.
     *
     * @param property property to check for presence
     * @return `true` if the property is present in the payload, `false` otherwise
     */
    public operator fun contains(property: A2uiProperty<*>): Boolean = raw.containsKey(property.key)

    /**
     * Retrieves the raw payload for the given property key.
     *
     * @param key The name of the property to retrieve.
     * @return The property value, or `null` if the property is missing.
     */
    internal operator fun get(key: String): Any? = raw[key]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is A2uiComponentProperties) return false

        // Fast O(1) comparison natively skipping heavy map diffs
        return this.raw === other.raw
    }

    override fun hashCode(): Int = System.identityHashCode(raw)
}
