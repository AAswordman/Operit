package com.ai.assistance.operit.ui.features.chat.historytree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDragOrderTest {
    @Test fun preservesDragOrderWhileAddingRowsAndRemovingHiddenRows() {
        assertEquals(
            listOf("prefix", "c", "after-c", "a", "after-a", "after-a2"),
            mergeHistoryDragOrder(
                listOf("c", "hidden", "a"),
                listOf("prefix", "a", "after-a", "after-a2", "c", "after-c"),
            ) { it },
        )
    }

    @Test fun unchangedLargeListUsesLinearKeyLookups() {
        val rows = (1..10_000).map(Int::toString)
        var lookups = 0
        assertEquals(rows.reversed(), mergeHistoryDragOrder(rows.reversed(), rows) {
            lookups++
            it
        })
        assertTrue(lookups <= rows.size * 6)
    }
}
