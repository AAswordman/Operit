package com.ai.assistance.operit.ui.features.chat.webview.workspace.markdown

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.links.openMarkdownLink
import com.ai.assistance.operit.ui.features.chat.webview.workspace.OpenFileInfo
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.WorkspaceFileLink
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.WorkspaceMarkdownLink
import com.ai.assistance.operit.ui.features.chat.webview.workspace.links.resolveWorkspaceMarkdownLink
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CancellationException

/** 原始 Markdown 保留渲染，文件跳转使用文档自身的目录和来源。 */
@Composable
internal fun WorkspaceMarkdownPreview(
    fileInfo: OpenFileInfo,
    onFileLink: (WorkspaceFileLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 960.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                StreamMarkdownRenderer(
                    content = fileInfo.content,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                    textColor = MaterialTheme.colorScheme.onSurface,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    onLinkClick = { url ->
                        try {
                            when (val link = resolveWorkspaceMarkdownLink(url, fileInfo.path, fileInfo.environment)) {
                                is WorkspaceMarkdownLink.File -> onFileLink(link.target)
                                is WorkspaceMarkdownLink.External -> openMarkdownLink(context, link.url)
                                WorkspaceMarkdownLink.Invalid -> Toast.makeText(
                                    context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            AppLogger.w("WorkspaceMarkdown", "处理文档链接失败：$url", error)
                            Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}