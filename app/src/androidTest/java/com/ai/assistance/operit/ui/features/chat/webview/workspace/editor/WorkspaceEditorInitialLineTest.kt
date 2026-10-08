package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceEditorInitialLineTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun differentFilesWithTheSameInitialLineBothPositionTheCaret() {
        val fileKey = mutableStateOf("android:/first.txt")
        val original = (1..24).joinToString("\n") { "line-$it" }
        val content = mutableStateOf(original)
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor(code = content.value, language = "text", fileKey = fileKey.value,
                    onCodeChange = { content.value = it }, initialLine = 12,
                    enableCompletion = false, editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("FIRST", 1))
            assertEquals(original.replace("line-12", "FIRSTline-12"), content.value)
        }
        compose.runOnIdle {
            fileKey.value = "linux:/second.txt"
            content.value = original
        }
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("SECOND", 1))
            assertEquals(original.replace("line-12", "SECONDline-12"), content.value)
        }
        // 同一文件的内容更新不得再次把光标拉回行首。
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("-CONTINUED", 1))
            assertEquals(original.replace("line-12", "SECOND-CONTINUEDline-12"), content.value)
        }
    }

    @Test
    fun aRepeatedLinkRequestRepositionsWithoutLosingTypedContent() {
        val original = (1..24).joinToString("\n") { "line-$it" }
        val content = mutableStateOf(original)
        val request = mutableStateOf(0)
        var editor: NativeCodeEditor? = null
        compose.setContent {
            MaterialTheme {
                CodeEditor(code = content.value, language = "text", fileKey = "linux:/same.txt",
                    onCodeChange = { content.value = it }, initialLine = 12,
                    initialLineRequest = request.value, enableCompletion = false,
                    editorRef = { editor = it })
            }
        }
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("FIRST", 1))
            assertEquals(original.replace("line-12", "FIRSTline-12"), content.value)
        }
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("-CONTINUED", 1))
            assertEquals(original.replace("line-12", "FIRST-CONTINUEDline-12"), content.value)
            request.value += 1
        }
        compose.runOnIdle {
            assertTrue(editor!!.getChildAt(0).onCreateInputConnection(EditorInfo())!!.commitText("AGAIN", 1))
            assertEquals(original.replace("line-12", "AGAINFIRST-CONTINUEDline-12"), content.value)
        }
    }
}
