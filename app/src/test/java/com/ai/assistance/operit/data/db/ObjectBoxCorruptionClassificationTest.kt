package com.ai.assistance.operit.data.db

import io.objectbox.exception.DbException
import io.objectbox.exception.FileCorruptException
import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectBoxCorruptionClassificationTest {
    @Test
    fun recognizesObjectBoxAndMdbxContentCorruption() {
        val corruptionErrors =
            listOf(
                FileCorruptException("ObjectBox file corruption"),
                DbException("Requested page not found", -30797),
                DbException("Database is corrupted", -30796),
                DbException("Not a database file", -30793),
                IllegalStateException(
                    "Wrapped ObjectBox file corruption",
                    FileCorruptException("ObjectBox file corruption")
                ),
                IllegalStateException(
                    "Wrapped ObjectBox corruption",
                    DbException("Not a database file", -30793)
                )
            )

        corruptionErrors.forEach { error ->
            assertTrue(ObjectBoxManager.isObjectBoxContentCorruption(error))
        }
    }

    @Test
    fun preservesOperationalAndCompatibilityFailures() {
        val nonCorruptionErrors =
            listOf(
                DbException("Database panic", -30795),
                DbException("Database version mismatch", -30794),
                DbException("Database map is full", -30792),
                DbException("Permission denied", 13),
                DbException("Database is locked", 16),
                DbException("Not a database file"),
                IllegalStateException("I/O failed", IOException("read failed"))
            )

        nonCorruptionErrors.forEach { error ->
            assertFalse(ObjectBoxManager.isObjectBoxContentCorruption(error))
        }
    }
}
