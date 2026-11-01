package com.ai.assistance.operit.ui.features.support.demo.markdown

import com.ai.assistance.operit.util.logging.AppLogger
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.CustomXmlRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import android.net.Uri

private const val TAG = "StreamMarkdownDemo"

/** 流式Markdown演示屏幕 展示流式渲染的效果，包括字节一个一个发送时的表现和发送间隔控制 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamMarkdownDemoScreen(onBackClick: () -> Unit = {}) {
    // 状态
    var isStreaming by remember { mutableStateOf(false) }
    var speedFactor by remember { mutableStateOf(1.0f) }
    var hasStarted by remember { mutableStateOf(false) }
    var totalChars by remember { mutableStateOf(0) }
    var processedChars by remember { mutableStateOf(0) }
    var autoScroll by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    // 用于重置渲染器的键
    var streamKey by remember { mutableStateOf(0) }

    // 为渲染器创建通道和流，使用key确保重置时能重新创建
    val (channel, markdownStream) =
            remember(streamKey) {
                AppLogger.d(TAG, "创建新的Channel和Stream实例 (key=$streamKey)")
                val ch = Channel<Char>(Channel.UNLIMITED)
                val stream = ch.consumeAsFlow().asStream()
                ch to stream
            }

    // 缓存渲染器和事件处理器，防止不必要的重组
    val customXmlRenderer = remember { CustomXmlRenderer() }
    val linkClickHandler = remember<(String) -> Unit> { { /* 处理链接点击 */ } }

    // 在Composable销毁或streamKey改变时关闭通道以防泄漏
    DisposableEffect(streamKey) {
        onDispose {
            AppLogger.d(TAG, "关闭Channel (key=$streamKey)")
            channel.close()
        }
    }

    // 模拟的Markdown内容
    val fullMarkdownContent = stringResource(R.string.demo_complete_message)

    // 设置总字符数
    LaunchedEffect(fullMarkdownContent) { totalChars = fullMarkdownContent.length }

    // 启动流的协程
    LaunchedEffect(isStreaming, streamKey) {
        if (isStreaming) {
            hasStarted = true
            processedChars = 0
            totalChars = fullMarkdownContent.length
            try {
                for (char in fullMarkdownContent) {
                    if (!isStreaming) break // 检查暂停状态
                    channel.send(char)
                    processedChars++
                    val delayMillis = (10 / speedFactor).toLong()
                    delay(delayMillis)
                }
            } catch (e: CancellationException) {
                AppLogger.d(TAG, "流传输被取消")
            } catch (e: Exception) {
                AppLogger.e(TAG, "流传输异常", e)
            } finally {
                if (!channel.isClosedForSend) {
                    AppLogger.d(TAG, "流传输完成，关闭Channel")
                    channel.close()
                }
                isStreaming = false
            }
        }
    }

    CustomScaffold(
            topBar = {
                // ... TopAppBar can be added here if needed
            }
    ) { padding ->
        Column(
                modifier =
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState)
        ) {
            // 控制面板
            ControlPanel(
                    isStreaming = isStreaming,
                    hasStarted = hasStarted,
                    onStreamToggle = { isStreaming = !isStreaming },
                    onReset = {
                        isStreaming = false
                        hasStarted = false
                        streamKey++ // 改变key以重置流
                    },
                    speedFactor = speedFactor,
                    onSpeedChange = { speedFactor = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 流式渲染区域
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = stringResource(R.string.stream_markdown_rendering),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StreamMarkdownRenderer(
                            markdownStream = markdownStream,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onLinkClick = linkClickHandler,
                            xmlRenderer = customXmlRenderer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ControlPanel(
    isStreaming: Boolean,
    hasStarted: Boolean,
    onStreamToggle: () -> Unit,
    onReset: () -> Unit,
    speedFactor: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStreamToggle) {
                Icon(
                        if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isStreaming) "Pause" else "Play"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) stringResource(R.string.stream_markdown_pause) else if (hasStarted) stringResource(R.string.stream_markdown_resume) else stringResource(R.string.stream_markdown_start))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.stream_markdown_reset))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.stream_markdown_speed, String.format("%.1f", speedFactor)), fontSize = 14.sp)
        Slider(
                value = speedFactor,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..80f,
                steps = 18
        )
    }
}

@Preview
@Composable
fun StreamMarkdownDemoScreenPreview() {
    val staticContent = """
        # 静态Markdown演示
        
        这是一个**静态**的Markdown渲染示例。
        
        * 列表项1
        * 列表项2
        
        [点击这个链接](https://www.example.com)
        
        ```kotlin
        fun main() {
            println("Hello, World!")
        }
        ```
    """.trimIndent()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.stream_markdown_demo_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.stream_markdown_static_render), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        val context = LocalContext.current
        StreamMarkdownRenderer(
            content = staticContent,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onLinkClick = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        )
    }
}
