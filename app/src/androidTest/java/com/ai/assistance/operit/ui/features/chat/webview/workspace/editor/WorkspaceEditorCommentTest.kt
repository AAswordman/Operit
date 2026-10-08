package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorCommentTest {
    @Test
    fun currentLineCommentsKeepTheCaretAndUndoInOneStep() {
        val document = EditorDocument("first\n  value\nlast")
        val caret = document.textString().indexOf("value") + 2
        document.collapseSelection(caret)

        assertTrue(document.toggleLineComment("//"))
        assertEquals("first\n  // value\nlast", document.textString())
        assertFalse(document.hasSelection())
        assertEquals(caret + 3, document.selectionEnd)
        assertTrue(document.undo())
        assertEquals("first\n  value\nlast", document.textString())
        assertEquals(caret, document.selectionEnd)
    }

    @Test
    fun blankCurrentLineCanBeCommentedAndUncommented() {
        val document = EditorDocument("")
        assertTrue(document.toggleLineComment("//"))
        assertEquals("// ", document.textString())
        assertTrue(document.toggleLineComment("//"))
        assertEquals("", document.textString())
        assertEquals(0, document.selectionEnd)
    }

    @Test
    fun cssCurrentLineBlockCommentsKeepFollowingLinesAndCaret() {
        val original = "body {\n  color: red;\n}\n"
        val document = EditorDocument(original)
        val caret = original.indexOf("color") + 3
        document.collapseSelection(caret)
        assertTrue(document.toggleBlockComment("/*", "*/"))
        assertEquals("body {\n/*  color: red;*/\n}\n", document.textString())
        assertFalse(document.hasSelection())
        assertEquals(caret + 2, document.selectionEnd)
        assertTrue(document.toggleBlockComment("/*", "*/"))
        assertEquals(original, document.textString())
        assertEquals(caret, document.selectionEnd)
    }

    @Test
    fun reversedSelectionStillUsesOneUndoRecord() {
        val document = EditorDocument("abc")
        document.setSelection(3, 0)
        assertTrue(document.toggleBlockComment("/*", "*/"))
        assertEquals("/*abc*/", document.textString())
        assertTrue(document.selectionStart > document.selectionEnd)
        assertTrue(document.undo())
        assertEquals("abc", document.textString())
        assertEquals(3, document.selectionStart)
        assertEquals(0, document.selectionEnd)
    }
}