package com.ai.assistance.operit.ui.features.packages.market

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketCommentPaginationTest {
    @Test
    fun `page count covers every comment`() {
        assertEquals(0, commentPageCount(total = 0, pageSize = 50))
        assertEquals(1, commentPageCount(total = 1, pageSize = 50))
        assertEquals(1, commentPageCount(total = 50, pageSize = 50))
        assertEquals(2, commentPageCount(total = 51, pageSize = 50))
        assertEquals(2, commentPageCount(total = 66, pageSize = 50))
        assertEquals(3, commentPageCount(total = 101, pageSize = 50))
        assertEquals(5, commentPageCount(total = 101, pageSize = 25))
    }
}
