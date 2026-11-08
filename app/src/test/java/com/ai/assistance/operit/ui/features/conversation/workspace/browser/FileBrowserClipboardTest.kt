package com.ai.assistance.operit.ui.features.conversation.workspace.browser

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FileBrowserClipboardTest {
    private fun item(name: String, environment: String? = null, cut: Boolean = true) =
        FileBrowserClipboardItem("/source/$name", environment, cut, isDirectory = false)

    @After
    fun resetClipboard() {
        FileBrowserClipboard.set(emptyList())
    }

    @Test
    fun completingOneCutKeepsUnfinishedCutsAndCopies() {
        val completed = item("first.txt")
        val unfinished = item("second.txt")
        val copied = item("third.txt", cut = false)
        FileBrowserClipboard.set(listOf(completed, unfinished, copied))
        FileBrowserClipboard.clear(completed)
        assertEquals(listOf(unfinished, copied), FileBrowserClipboard.content.value)
    }

    @Test
    fun completingOldCutDoesNotClearAReplacementCopy() {
        val previous = item("same.txt")
        val replacement = previous.copy(cut = false)
        FileBrowserClipboard.set(previous)
        FileBrowserClipboard.set(replacement)
        FileBrowserClipboard.clear(previous)
        assertEquals(listOf(replacement), FileBrowserClipboard.content.value)
    }

    @Test
    fun duplicateAndroidAliasesAreRemovedButOtherEnvironmentsRemainDistinct() {
        val android = item("same.txt")
        val linux = item("same.txt", environment = "linux")
        val repository = item("same.txt", environment = "repo:documents")
        FileBrowserClipboard.set(listOf(android, android.copy(environment = "android"), linux, repository))
        assertEquals(listOf(android, linux, repository), FileBrowserClipboard.content.value)
    }
}