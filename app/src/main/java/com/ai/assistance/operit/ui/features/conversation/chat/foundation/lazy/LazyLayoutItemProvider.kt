/*
 * Copyright 2021 The Android Open Source Project
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

package com.ai.assistance.operit.ui.features.conversation.chat.foundation.lazy

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * Provides all the needed info about the items which could be later composed and displayed as
 * children or [LazyLayout]. The number of virtual items is limited by
 * [LazyLayoutItemProvider.itemCount]. For an efficient implementation this should be an immutable
 * representation of the underlying data/items, ideally changes to your data source/structure should
 * re-create this representation, instead of changing state values read inside this implementation.
 * See [LazyLayout] for additional context.
 *
 * @sample androidx.compose.foundation.samples.LazyLayoutItemProviderSample
 */
@Stable
interface LazyLayoutItemProvider {

    /** The total number of items in the lazy layout (visible or not). */
    @get:IntRange(from = 0) val itemCount: Int

    /**
     * The item for the given [index] and [key]. Indices are a core concept on LazyLayouts and
     * should always be supported, the key is an additional concept that can be introduced per item
     * and is used to guaranteed the uniqueness of a given item in the Lazy Layout implementation.
     * For instance, in lists, keys are used to keep an item's scroll position in case of dataset
     * changes. Therefore, the key provided here will depend if your LazyLayout implementation
     * supports this concept or not.
     *
     * Custom keys are provided in the [getKey] function. If a key concept is not needed by your
     * LazyLayout implementation ([getKey] is not overridden), the internal implementation will
     * simply use [getDefaultLazyLayoutKey] to implement a key based on an item's index.
     *
     * It is the responsibility of the implementation to ensure consistency between keys and indices
     * if custom keys are provided, since both the caller and the implementer of this function are
     * the same, a contract can be established to ensure the consistency.
     *
     * @param index the index of the item in the list
     * @param key The key of the item as described above.
     */
    @Composable fun Item(@IntRange(from = 0) index: Int, key: Any)

    /**
     * Returns the content type for the item on this index. It is used to improve the item
     * compositions reusing efficiency. Note that null is a valid type and items of such type will
     * be considered compatible.
     *
     * @param index the index of an item in the layout.
     * @return The content type mapped from [index].
     */
    fun getContentType(@IntRange(from = 0) index: Int): Any? = null

    /**
     * Returns the key for the item on this index.
     *
     * @param index the index of an item in the layout.
     * @return The key mapped from [index].
     * @see getDefaultLazyLayoutKey which you can use if the user didn't provide a key.
     */
    fun getKey(@IntRange(from = 0) index: Int): Any = getDefaultLazyLayoutKey(index)

    /**
     * Get index for given key. The index is not guaranteed to be known for all keys in layout for
     * optimization purposes, but must be present for elements in current viewport. If the key is
     * not present in the layout or is not known, return -1.
     *
     * @param key the key of an item in the layout.
     * @return The index mapped from [key] if it is present in the layout, otherwise -1.
     */
    fun getIndex(key: Any): Int = -1
}

/**
 * Finds a position of the item with the given key in the lists. This logic allows us to detect when
 * there were items added or removed before our current first item.
 */
internal fun LazyLayoutItemProvider.findIndexByKey(key: Any?, lastKnownIndex: Int): Int {
    if (key == null || itemCount == 0) {
        // there were no real item during the previous measure
        return lastKnownIndex
    }
    if (lastKnownIndex < itemCount && key == getKey(lastKnownIndex)) {
        // this item is still at the same index
        return lastKnownIndex
    }
    val newIndex = getIndex(key)
    if (newIndex != -1) {
        return newIndex
    }
    // fallback to the previous index if we don't know the new index of the item
    return lastKnownIndex
}

/**
 * This creates an object meeting following requirements:
 * 1) Objects created for the same index are equals and never equals for different indexes.
 * 2) This class is saveable via a default SaveableStateRegistry on the platform.
 * 3) This objects can't be equals to any object which could be provided by a user as a custom key.
 *
 * Note: this function is a part of [LazyLayout] harness that allows for building custom lazy
 * layouts. LazyLayout and all corresponding APIs are still under development and are subject to
 * change.
 */
