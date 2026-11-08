package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import com.ai.assistance.operit.ui.features.conversation.workspace.browser.FileBrowserClipboardItem
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceDeleteOperationTest {
    private fun item(name: String, environment: String? = null, directory: Boolean = false) =
        FileBrowserClipboardItem("/selected/$name", environment, cut = false, isDirectory = directory)

    @Test fun singleDirectoryRemainsIndeterminateUntilItsToolReturnsSuccess() = runBlocking {
        val target = item("folder", "linux", directory = true)
        lateinit var operation: WorkspaceDeleteOperation
        operation = WorkspaceDeleteOperation {
            assertEquals(target, it)
            val progress = checkNotNull(operation.progress.value)
            assertEquals(target, progress.target)
            assertEquals(0, progress.completedItems)
            assertEquals(1, progress.totalItems)
            assertNull(progress.fraction)
        }
        var notified = false
        operation.run(listOf(target)) {
            notified = true
            val progress = checkNotNull(operation.progress.value)
            assertEquals(1, progress.completedItems)
            assertEquals(1f, checkNotNull(progress.fraction), 0f)
        }
        assertTrue(notified)
        assertNull(operation.progress.value)
    }

    @Test fun batchProgressCountsOnlySuccessfulSelectedItemsAcrossEnvironments() = runBlocking {
        val targets = listOf(item("local"), item("ubuntu", "linux", true), item("document", "repo:docs"))
        val before = mutableListOf<WorkspaceDeleteProgress>()
        val after = mutableListOf<WorkspaceDeleteProgress>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        lateinit var operation: WorkspaceDeleteOperation
        operation = WorkspaceDeleteOperation { before += checkNotNull(operation.progress.value) }
        operation.run(targets) {
            deleted += it
            after += checkNotNull(operation.progress.value)
        }
        assertEquals(targets, before.map { it.target })
        assertEquals(listOf(0, 1, 2), before.map { it.completedItems })
        assertEquals(listOf(1, 2, 3), after.map { it.completedItems })
        assertEquals(targets, deleted)
        assertEquals(1f / 3f, checkNotNull(before[1].fraction), 0f)
        assertTrue(before.all { checkNotNull(it.fraction) < 1f })
        assertEquals(1f, checkNotNull(after.last().fraction), 0f)
        assertNull(operation.progress.value)
    }

    @Test fun failureIsNotCountedAndRemainingItemsAreNotStarted() = runBlocking {
        val targets = listOf(item("first"), item("failed"), item("remaining"))
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = WorkspaceDeleteOperation {
            attempted += it
            if (it == targets[1]) throw IOException("删除失败")
        }
        try {
            operation.run(targets) { deleted += it }
            fail("应传播工具删除错误")
        } catch (e: IOException) {
            assertEquals("删除失败", e.message)
        }
        assertEquals(targets.take(2), attempted)
        assertEquals(targets.take(1), deleted)
        assertNull(operation.progress.value)
    }

    @Test fun cancellingWhileCurrentToolRunsStopsTheNextItemWithoutUndoingSuccess() = runBlocking {
        val targets = listOf(item("current"), item("remaining"))
        val attempted = mutableListOf<FileBrowserClipboardItem>()
        val deleted = mutableListOf<FileBrowserClipboardItem>()
        val operation = WorkspaceDeleteOperation {
            attempted += it
            // 模拟同步工具收到取消后仍返回成功：已删除项不能被视作撤销。
            currentCoroutineContext().cancel()
        }
        val task = launch { operation.run(targets) { deleted += it } }
        task.join()
        assertTrue(task.isCancelled)
        assertEquals(targets.take(1), attempted)
        assertEquals(targets.take(1), deleted)
        assertNull(operation.progress.value)
    }

    @Test fun alreadyCancelledOperationNeverStartsDeletion() = runBlocking {
        var invoked = false
        val operation = WorkspaceDeleteOperation { invoked = true }
        val task = launch {
            currentCoroutineContext().cancel()
            operation.run(listOf(item("not-started")))
        }
        task.join()
        assertTrue(task.isCancelled)
        assertFalse(invoked)
        assertNull(operation.progress.value)
    }

    @Test fun emptySelectionDoesNotOpenProgressOrInvokeTools() = runBlocking {
        var invoked = false
        var notified = false
        val operation = WorkspaceDeleteOperation { invoked = true }
        operation.run(emptyList()) { notified = true }
        assertFalse(invoked)
        assertFalse(notified)
        assertNull(operation.progress.value)
    }

    @Test fun nextOperationStartsWithFreshProgressAfterFailure() = runBlocking {
        val failed = item("failed")
        val next = item("next")
        lateinit var operation: WorkspaceDeleteOperation
        operation = WorkspaceDeleteOperation {
            if (it == failed) throw IOException("删除失败")
            assertEquals(next, checkNotNull(operation.progress.value).target)
            assertEquals(0, checkNotNull(operation.progress.value).completedItems)
            assertEquals(1, checkNotNull(operation.progress.value).totalItems)
        }
        try {
            operation.run(listOf(failed, item("unprocessed")))
            fail("应传播工具删除错误")
        } catch (e: IOException) {
            assertNull(operation.progress.value)
        }
        operation.run(listOf(next))
        assertNull(operation.progress.value)
    }
}
