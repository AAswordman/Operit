package com.ai.assistance.operit.ui.common.composedsl

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslNode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolPkgComposeDslWebViewFocusAndroidTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var rootView: View

    @Test
    fun assistantPlayerMountKeepsComposerFocus() {
        val visible = mutableStateOf(false)
        val revision = mutableStateOf(0)
        setAssistantPlayerHost(visible, revision)
        compose.onNodeWithTag(COMPOSER).assertIsFocused()

        compose.runOnIdle { visible.value = true }

        assertPlayerMountedWithComposerFocus()
    }

    @Test
    fun assistantPlayerUpdatesAndRemountsKeepComposerFocus() {
        val visible = mutableStateOf(false)
        val revision = mutableStateOf(0)
        setAssistantPlayerHost(visible, revision)
        compose.runOnIdle { visible.value = true }
        assertPlayerMountedWithComposerFocus()

        compose.runOnIdle { revision.value += 1 }
        assertPlayerMountedWithComposerFocus()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithTag(COMPOSER).assertIsFocused()
        compose.runOnIdle { visible.value = true }
        assertPlayerMountedWithComposerFocus()
    }

    @Test
    fun mountedPlayerStillAcceptsExplicitFocus() {
        val visible = mutableStateOf(false)
        val revision = mutableStateOf(0)
        setAssistantPlayerHost(visible, revision)
        compose.runOnIdle { visible.value = true }
        assertPlayerMountedWithComposerFocus()

        compose.runOnIdle {
            val webView = requireNotNull(findWebView(rootView))
            assertTrue(webView.isFocusable)
            assertTrue(webView.isFocusableInTouchMode)
            assertTrue(webView.requestFocus())
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(requireNotNull(findWebView(rootView)).hasFocus())
        }
    }

    private fun setAssistantPlayerHost(visible: State<Boolean>, revision: State<Int>) {
        compose.setContent {
            MaterialTheme {
                val view = LocalView.current
                val requester = remember { FocusRequester() }
                var draft by remember { mutableStateOf("正在输入下一条消息") }
                SideEffect { rootView = view.rootView }
                Column(Modifier.width(320.dp)) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                            .testTag(COMPOSER),
                    )
                    // 语音条来自助手消息；输入框只用于检查播放器没有抢走现有焦点。
                    LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                        item {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                Text("助手正文，当前宽度：${maxWidth.value}")
                            }
                        }
                        item {
                            if (visible.value) {
                                RenderToolPkgComposeDslNode(
                                    node = assistantPlayerNode(revision.value),
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                )
                            }
                        }
                        items(4) { index -> Text("后续助手正文 $index") }
                    }
                }
                LaunchedEffect(Unit) { requester.requestFocus() }
            }
        }
    }

    private fun assertPlayerMountedWithComposerFocus() {
        compose.waitForIdle()
        compose.runOnIdle { assertNotNull(findWebView(rootView)) }
        compose.onNodeWithTag(COMPOSER).assertIsFocused()
    }

    private fun assistantPlayerNode(revision: Int) = ToolPkgComposeDslNode(
        type = "WebView",
        props = mapOf(
            // 使用本地 HTML 覆盖相同的 DSL WebView 挂载路径，不请求合成服务。
            "html" to "<html><body><button>助手语音条 $revision</button><audio controls></audio><input aria-label='网页输入'></body></html>",
            "baseUrl" to "about:blank",
            "javaScriptEnabled" to true,
            "mediaPlaybackRequiresUserGesture" to false,
        ),
        children = emptyList(),
    )

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findWebView(view.getChildAt(index))
                if (found != null) return found
            }
        }
        return null
    }

    private companion object {
        const val COMPOSER = "chat-composer"
    }
}
