package com.ai.nonoassistance.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GlobalMemoryStoreTest {

    private lateinit var store: GlobalMemoryStore

    @Before
    fun setup() {
        store = GlobalMemoryStore()
    }

    @Test
    fun `storeLesson adds lesson with generated ID`() = runTest {
        val lesson = MemoryLesson(
            taskPattern = "bash command failed",
            mistakeDescription = "XML tags in tool params",
            fixDescription = "Use sanitizer before execution"
        )

        store.storeLesson(lesson)
        assertEquals(1, store.size())
        assertNotNull(store.getAllLessons()[0].id)
    }

    @Test
    fun `storeLesson preserves existing ID`() = runTest {
        val lesson = MemoryLesson(
            id = "custom-id",
            taskPattern = "test",
            mistakeDescription = "test",
            fixDescription = "fix"
        )

        store.storeLesson(lesson)
        assertEquals("custom-id", store.getAllLessons()[0].id)
    }

    @Test
    fun `queryRelevantLessons returns matching lessons`() = runTest {
        store.storeLesson(MemoryLesson(
            taskPattern = "bash command execution failed",
            mistakeDescription = "XML tags corrupted JSON parsing",
            fixDescription = "Added sanitizer layer"
        ))
        store.storeLesson(MemoryLesson(
            taskPattern = "unrelated topic about UI",
            mistakeDescription = "Button not showing",
            fixDescription = "Fixed layout"
        ))

        val results = store.queryRelevantLessons("bash command execution")
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].taskPattern.contains("bash"))
    }

    @Test
    fun `queryRelevantLessons respects limit`() = runTest {
        repeat(10) { i ->
            store.storeLesson(MemoryLesson(
                taskPattern = "error type $i",
                mistakeDescription = "mistake $i",
                fixDescription = "fix $i"
            ))
        }

        val results = store.queryRelevantLessons("error", limit = 3)
        assertTrue(results.size <= 3)
    }

    @Test
    fun `getLessonsByTag filters correctly`() = runTest {
        store.storeLesson(MemoryLesson(
            taskPattern = "a", mistakeDescription = "b", fixDescription = "c",
            tags = listOf("tool-calling", "xml")
        ))
        store.storeLesson(MemoryLesson(
            taskPattern = "d", mistakeDescription = "e", fixDescription = "f",
            tags = listOf("ui")
        ))

        val results = store.getLessonsByTag("tool-calling")
        assertEquals(1, results.size)
    }

    @Test
    fun `exportSnapshot and importSnapshot work correctly`() = runTest {
        store.storeLesson(MemoryLesson(
            taskPattern = "test", mistakeDescription = "err", fixDescription = "fix"
        ))

        val snapshot = store.exportSnapshot()
        assertEquals(1, snapshot.lessons.size)

        val newStore = GlobalMemoryStore()
        newStore.importSnapshot(snapshot)
        assertEquals(1, newStore.size())
    }

    @Test
    fun `importSnapshot deduplicates existing lessons`() = runTest {
        store.storeLesson(MemoryLesson(
            taskPattern = "test", mistakeDescription = "err", fixDescription = "fix"
        ))

        val snapshot = store.exportSnapshot()
        store.importSnapshot(snapshot)

        assertEquals(1, store.size()) // Should not duplicate
    }

    @Test
    fun `removeLesson removes by ID`() = runTest {
        store.storeLesson(MemoryLesson(
            id = "to-remove",
            taskPattern = "test", mistakeDescription = "err", fixDescription = "fix"
        ))
        assertEquals(1, store.size())

        store.removeLesson("to-remove")
        assertEquals(0, store.size())
    }

    @Test
    fun `clear removes all lessons`() = runTest {
        store.storeLesson(MemoryLesson(
            taskPattern = "a", mistakeDescription = "b", fixDescription = "c"
        ))
        store.storeLesson(MemoryLesson(
            taskPattern = "d", mistakeDescription = "e", fixDescription = "f"
        ))

        store.clear()
        assertEquals(0, store.size())
    }
}
