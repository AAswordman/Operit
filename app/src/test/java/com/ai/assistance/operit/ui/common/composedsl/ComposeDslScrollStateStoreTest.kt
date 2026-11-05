package com.ai.assistance.operit.ui.common.composedsl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ComposeDslScrollStateStoreTest {

    @Test
    fun `scroll state key uses node path and normalized type without explicit key`() {
        assertEquals(
            "0:content/1:lazycolumn",
            composeDslLazyListStateKey(
                parentNodePath = "0:content/1",
                nodeType = "Lazy-Column",
                nodeKey = null
            )
        )
    }

    @Test
    fun `scroll state key uses explicit node key`() {
        assertEquals(
            "0:lazycolumn:key:worldbook_list",
            composeDslLazyListStateKey(
                parentNodePath = "0",
                nodeType = "LazyColumn",
                nodeKey = "worldbook_list"
            )
        )
    }

    @Test
    fun `blank explicit key falls back to node path`() {
        assertEquals(
            "0:lazycolumn",
            composeDslLazyListStateKey(
                parentNodePath = "0",
                nodeType = "LazyColumn",
                nodeKey = "   "
            )
        )
    }

    @Test
    fun `store reuses one state for the same key and isolates different keys`() {
        val store = ComposeDslScrollStateStore()

        val first = store.lazyListState("0:lazycolumn:key:list")
        val second = store.lazyListState("0:lazycolumn:key:list")
        val other = store.lazyListState("0:content/0:lazycolumn:key:form")

        assertSame(first, second)
        assertNotSame(first, other)
    }

    @Test
    fun `created state is a fresh lazy list state`() {
        val state: LazyListState = ComposeDslScrollStateStore().lazyListState("key")
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun `saver keeps one position per list key`() {
        val source =
            ComposeDslScrollStateStore(
                restoredStates =
                    mapOf(
                        "list" to (3 to 120),
                        "form" to (7 to 0)
                    )
            )
        val scope =
            object : SaverScope {
                override fun canBeSaved(value: Any): Boolean = true
            }

        val saved = with(ComposeDslScrollStateStore.Saver) { scope.save(source) }
        assertEquals(
            mapOf("list" to listOf(3, 120), "form" to listOf(7, 0)),
            saved
        )

        val restored = ComposeDslScrollStateStore.Saver.restore(saved!!)!!
        val listState = restored.lazyListState("list")
        val formState = restored.lazyListState("form")
        assertEquals(3, listState.firstVisibleItemIndex)
        assertEquals(120, listState.firstVisibleItemScrollOffset)
        assertEquals(7, formState.firstVisibleItemIndex)
        assertEquals(0, formState.firstVisibleItemScrollOffset)
    }
}
