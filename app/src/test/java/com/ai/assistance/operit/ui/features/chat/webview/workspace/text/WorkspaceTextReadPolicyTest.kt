package com.ai.assistance.operit.ui.features.chat.webview.workspace.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceTextReadPolicyTest {
    @Test
    fun oversizedFilesNeverInvokeTheFullTextReader() {
        for (size in listOf(WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES + 1, Long.MAX_VALUE)) {
            assertNull(readWorkspaceTextWithinLimit(size) {
                fail("超限文件不得调用全量文本读取")
                "未读取"
            })
        }
    }

    @Test
    fun emptySmallAndExactLimitFilesReadOnceAndKeepTheirContent() {
        for (size in listOf(0L, 1L, WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES - 1, WORKSPACE_TEXT_PREVIEW_LIMIT_BYTES)) {
            var calls = 0
            val content = readWorkspaceTextWithinLimit(size) {
                calls++
                "中文文本"
            }
            assertEquals("中文文本", content)
            assertEquals(1, calls)
        }
    }

    @Test
    fun invalidNegativeSizeDoesNotInvokeTheReader() {
        assertNull(readWorkspaceTextWithinLimit(-1) {
            fail("非法大小不得触发全量读取")
            "未读取"
        })
    }
}
