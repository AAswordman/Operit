package com.ai.assistance.operit.data.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDatabaseHealthManagerTest {
    @Test
    fun indexEntryFailuresAreRepairable() {
        assertTrue(
            RoomDatabaseHealthManager.isIndexOnlyQuickCheckFailure(
                listOf(
                    "*** in database main ***\nrow 42 missing from index index_messages_chatId",
                    "wrong # of entries in index index_messages_chatId_timestamp"
                )
            )
        )
    }

    @Test
    fun pageDamageIsNotClassifiedAsIndexOnly() {
        assertFalse(
            RoomDatabaseHealthManager.isIndexOnlyQuickCheckFailure(
                listOf("Page 8 is never used")
            )
        )
    }

    @Test
    fun unrelatedFailureContainingIndexWordsIsNotRepairable() {
        assertFalse(
            RoomDatabaseHealthManager.isIndexOnlyQuickCheckFailure(
                listOf("database page damage caused a row missing from index metadata")
            )
        )
    }

    @Test
    fun successfulQuickCheckDoesNotRequestIndexRepair() {
        assertFalse(
            RoomDatabaseHealthManager.isIndexOnlyQuickCheckFailure(listOf("ok"))
        )
    }
}
