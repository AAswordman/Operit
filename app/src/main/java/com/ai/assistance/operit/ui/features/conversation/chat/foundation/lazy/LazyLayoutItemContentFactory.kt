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

import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.SaveableStateHolder
import kotlin.jvm.JvmInline

/**
 * This class:
 * 1) Caches the lambdas being produced by [itemProvider]. This allows us to perform less
 *    recompositions as the compose runtime can skip the whole composition if we subcompose with the
 *    same instance of the content lambda.
 * 2) Updates the mapping between keys and indexes when we have a new factory
 * 3) Adds state restoration on top of the composable returned by [itemProvider] with help of
 *    [saveableStateHolder].
 */
internal class LazyLayoutItemContentFactory(
    private val saveableStateHolder: SaveableStateHolder,
    val itemProvider: () -> LazyLayoutItemProvider,
) {
    /** Contains the cached lambdas produced by the [itemProvider]. */
    private val lambdasCache = mutableScatterMapOf<Any, CachedItemContent>()

    /**
     * Returns the content type for the item with the given key. It is used to improve the item
     * compositions reusing efficiency.
     */
    fun getContentType(key: Any?): Any? {
        if (key == null) return null

        val cachedContent = lambdasCache[key]
        return if (cachedContent != null) {
            cachedContent.contentType
        } else {
            val itemProvider = itemProvider()
            val index = itemProvider.getIndex(key)
            if (index != -1) {
                itemProvider.getContentType(index)
            } else {
                null
            }
        }
    }

    /** Return cached item content lambda or creates a new lambda and puts it in the cache. */
    fun getContent(index: Int, key: Any, contentType: Any?): @Composable () -> Unit {
        val cached = lambdasCache[key]
        return if (cached != null && cached.index == index && cached.contentType == contentType) {
            cached.content
        } else {
            val newContent = CachedItemContent(index, key, contentType)
            lambdasCache[key] = newContent
            newContent.content
        }
    }

    private inner class CachedItemContent(index: Int, val key: Any, val contentType: Any?) {
        // the index resolved during the latest composition
        var index = index
            private set

        private var _content: (@Composable () -> Unit)? = null
        val content: (@Composable () -> Unit)
            get() = _content ?: createContentLambda().also { _content = it }

        private fun createContentLambda() =
            @Composable {
                val itemProvider = itemProvider()

                var index = index
                if (index >= itemProvider.itemCount || itemProvider.getKey(index) != key) {
                    index = itemProvider.getIndex(key)
                    if (index != -1) this.index = index
                }

                if (index != -1) {
                    SkippableItem(
                        itemProvider,
                        StableValue(saveableStateHolder),
                        index,
                        StableValue(key),
                    )
                }
                DisposableEffect(key) {
                    onDispose {
                        // we clear the cached content lambda when disposed to not leak
                        // RecomposeScopes
                        _content = null
                    }
                }
            }
    }
}

@Stable @JvmInline private value class StableValue<T>(val value: T)

/**
 * Hack around skippable functions to force skip SaveableStateProvider and Item block when nothing
 * changed. It allows us to skip heavy-weight composition local providers.
 */
@Composable
private fun SkippableItem(
    itemProvider: LazyLayoutItemProvider,
    saveableStateHolder: StableValue<SaveableStateHolder>,
    index: Int,
    key: StableValue<Any>,
) {
    saveableStateHolder.value.SaveableStateProvider(key.value) {
        itemProvider.Item(index, key.value)
    }
}
