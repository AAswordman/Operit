package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRendererState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.rememberRevisableTextStream
import com.ai.assistance.operit.ui.features.chat.components.part.CustomXmlRenderer
import com.ai.assistance.operit.ui.features.chat.components.part.ThinkToolsXmlNodeGrouper
import com.ai.assistance.operit.ui.features.chat.components.LinkPreviewDialog
import com.ai.assistance.operit.util.markdown.toCharStream
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.ToolCollapseMode
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.ProvideAiMarkdownTextLayoutSettings

/**
 * A composable function for rendering AI response messages in a Cursor IDE style. Supports text
 * selection and copy on long press for different segments. Always uses collapsed execution mode for
 * tool output display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    onLinkClick: ((String) -> Unit)? = null,
    initialThinkingExpanded: Boolean = false,
    allowExpandedThinkingFullHeight: Boolean = false,
    expandThinkToolsGroups: Boolean = false,
    forceShowThinkingProcess: Boolean = false,
    overrideStream: Stream<String>? = null,
    heightMemory: ChatMessageHeightMemory? = null,
    enableDialogs: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
) {
    val context = LocalContext.current
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val themeSnapshot = LocalThemePreferenceSnapshot.current
    val showThinkingProcess = themeSnapshot.showThinkingProcess
    val showStatusTags = themeSnapshot.showStatusTags
    val effectiveShowThinkingProcess = if (forceShowThinkingProcess) true else showThinkingProcess
    
    val showModelProvider = themeSnapshot.showModelProvider
    val showModelName = themeSnapshot.showModelName
    val showRoleName = themeSnapshot.showRoleName
    val toolCollapseMode by displayPreferencesManager.toolCollapseMode.collectAsState(initial = ToolCollapseMode.ALL)

    // 链接预览弹窗状态
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkToPreview by remember { mutableStateOf("") }
    
    // 创建并保存StreamMarkdownRenderer的状态，使用message.timestamp作为key确保同一条消息共享状态
    val rendererState = remember(message.timestamp) { StreamMarkdownRendererState() }

    // 创建自定义XML渲染器
    val xmlRenderer = remember(
        effectiveShowThinkingProcess,
        showStatusTags,
        initialThinkingExpanded,
        allowExpandedThinkingFullHeight,
        enableDialogs
    ) {
        CustomXmlRenderer(
            showThinkingProcess = effectiveShowThinkingProcess,
            showStatusTags = showStatusTags,
            initialThinkingExpanded = initialThinkingExpanded,
            allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
            enableDialogs = enableDialogs  // 传递弹窗启用状态
        )
    }

    val nodeGrouper = remember(effectiveShowThinkingProcess, toolCollapseMode, expandThinkToolsGroups) {
        ThinkToolsXmlNodeGrouper(
            showThinkingProcess = effectiveShowThinkingProcess,
            forceExpandGroups = expandThinkToolsGroups,
            toolCollapseMode = toolCollapseMode
        )
    }
    val rememberedOnLinkClick = remember(context, onLinkClick, enableDialogs) {
        onLinkClick ?: { url ->
            // 如果启用了弹窗，显示链接预览；否则使用系统浏览器打开
            if (enableDialogs) {
                linkToPreview = url
                showLinkDialog = true
            } else {
                // 在Service层，直接使用系统浏览器打开链接
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // 忽略打开失败
                }
            }
        }
    }

    ProvideAiMarkdownTextLayoutSettings {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .onSizeChanged { size ->
                        heightMemory?.updateMeasured(message.timestamp, size.height)
                    }
        ) {
        // 构建标题 - 分左右两部分显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：Response标题
            Text(
                text = "Response",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            
            // 右侧：详细信息（角色名、模型信息）
            val detailText = buildString {
                // 根据用户设置显示角色名称
                if (showRoleName && message.roleName.isNotEmpty()) {
                    append(message.roleName)
                }
                
                // 根据用户设置显示模型信息
                val showModel = showModelName && message.modelName.isNotEmpty()
                val showProvider = showModelProvider && message.provider.isNotEmpty()
                
                if (showModel && showProvider) {
                    if (isNotEmpty()) append(" | ")
                    append("${message.modelName} by ${message.provider}")
                } else if (showModel) {
                    if (isNotEmpty()) append(" | ")
                    append(message.modelName)
                } else if (showProvider) {
                    if (isNotEmpty()) append(" | ")
                    append(message.provider)
                }
            }
            
            if (detailText.isNotEmpty()) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        // 使用 message.timestamp 作为 key，确保在重组期间，
        // 只要是同一条消息，StreamMarkdownRenderer就不会被销毁和重建。
        // 这可以防止流被不必要地取消，保证了渲染的连续性。
        key(message.timestamp) {
            val streamToRender = rememberRevisableTextStream(overrideStream ?: message.contentStream)
            if (streamToRender != null) {
                // 对于正在流式传输的消息，使用流式渲染器
                // 将contentStream保存到本地变量以避免智能转换问题
                val charStream = remember(streamToRender) { streamToRender.toCharStream() }

                StreamMarkdownRenderer(
                    markdownStream = charStream,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    onLinkClick = rememberedOnLinkClick,
                    xmlRenderer = xmlRenderer,
                    nodeGrouper = nodeGrouper,
                    enableDialogs = enableDialogs,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    state = rendererState,
                )
            } else {
                // 对于已完成的静态消息，使用新的字符串渲染器以提高性能
                // 共享相同的state，避免重新计算nodes等状态
                StreamMarkdownRenderer(
                    content = message.content,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    onLinkClick = rememberedOnLinkClick,
                    xmlRenderer = xmlRenderer,
                    nodeGrouper = nodeGrouper,
                    enableDialogs = enableDialogs,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    state = rendererState,
                )
            }
        }
    }

        // 链接预览弹窗 - 仅在启用弹窗时显示
        if (showLinkDialog && linkToPreview.isNotEmpty() && enableDialogs) {
            LinkPreviewDialog(
                url = linkToPreview,
                onDismiss = {
                    showLinkDialog = false
                    linkToPreview = ""
                }
            )
        }
    }
}
