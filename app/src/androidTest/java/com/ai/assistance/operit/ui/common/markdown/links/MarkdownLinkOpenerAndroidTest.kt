package com.ai.assistance.operit.ui.common.markdown.links

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownLinkOpenerAndroidTest {
    @Test
    fun absoluteLinksDispatchTheExpectedIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var dispatched: Intent? = null
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun startActivity(intent: Intent) { dispatched = intent }
        }
        instrumentation.runOnMainSync {
            assertTrue(openMarkdownLink(context, "https://example.com/a#top"))
            assertEquals(Intent.ACTION_VIEW, dispatched!!.action)
            assertEquals("https://example.com/a#top", dispatched!!.dataString)
            assertTrue(dispatched!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }

    @Test
    fun emptyRelativeAndMalformedLinksDoNotDispatchAnIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var calls = 0
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun startActivity(intent: Intent) { calls += 1 }
        }
        instrumentation.runOnMainSync {
            for (url in listOf("", "target.md", "#页脚", "https://example.com/%invalid")) {
                assertFalse(openMarkdownLink(context, url))
            }
            assertEquals(0, calls)
        }
    }

    @Test
    fun missingHandlersAndPermissionErrorsReturnFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // 确定性模拟系统拒绝，验证异常不会离开实际外链打开入口。
        for (error in listOf(ActivityNotFoundException("没有处理器"), SecurityException("权限被拒绝"),
            IllegalArgumentException("参数无效"), IllegalStateException("系统打开失败"))) {
            val context = object : ContextWrapper(instrumentation.targetContext) {
                override fun startActivity(intent: Intent) { throw error }
            }
            instrumentation.runOnMainSync { assertFalse(openMarkdownLink(context, "foo://bar")) }
        }
    }
}