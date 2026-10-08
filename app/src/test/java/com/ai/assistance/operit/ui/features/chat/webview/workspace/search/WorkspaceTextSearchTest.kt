package com.ai.assistance.operit.ui.features.chat.webview.workspace.search

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceTextSearchTest {
    @Test
    fun editingRetainsTheActiveMatchAndClampsOnlyWhenNecessary() {
        assertEquals(2, activeSearchMatchAfterUpdate(2, 5))
        assertEquals(1, activeSearchMatchAfterUpdate(4, 2))
        assertEquals(-1, activeSearchMatchAfterUpdate(2, 0))
        assertEquals(0, activeSearchMatchAfterUpdate(-1, 3))
    }

    @Test
    fun matchesKeepOriginalOffsetsForChineseAndCaseInsensitiveQueries() {
        assertEquals(listOf(0..1, 4..5), findTextMatches("中文 a中文", "中文"))
        assertEquals(listOf(0..2, 4..6), findTextMatches("AbC abc", "abc"))
        assertEquals(emptyList<IntRange>(), findTextMatches("abc", ""))
        assertEquals(emptyList<IntRange>(), findTextMatches("abc", "abcd"))
    }
}