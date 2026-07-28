package com.ai.assistance.operit.ui.features.chat.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Build
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.CachePolicy
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleStyleChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage
import com.ai.assistance.operit.ui.theme.AppBackgroundLayer
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 消息图片生成器
 * 
 * 将选中的消息渲染为图片，用于分享
 * 使用 ScrollView + ComposeView 方案支持任意长度的内容
 */
object MessageImageGenerator {
    
    private const val TAG = "MessageImageGenerator"
    
    /**
     * 生成消息图片
     * 
     * @param context Android 上下文
     * @param messages 要渲染的消息列表
     * @param userMessageColor 用户消息背景色
     * @param aiMessageColor AI消息背景色
     * @param userTextColor 用户消息文字颜色
     * @param aiTextColor AI消息文字颜色
     * @param systemMessageColor 系统消息背景色
     * @param systemTextColor 系统消息文字颜色
     * @param thinkingBackgroundColor 思考块背景色
     * @param thinkingTextColor 思考块文字颜色
     * @param chatStyle 聊天风格
     * @param width 图片宽度（像素）
     * @return 生成的图片文件
     */
    suspend fun generateMessageImage(
        context: Context,
        messages: List<ChatMessage>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        chatStyle: ChatStyle = ChatStyle.CURSOR,
        cursorUserBubbleLiquidGlass: Boolean = false,
        cursorUserBubbleWaterGlass: Boolean = false,
        bubbleUserBubbleLiquidGlass: Boolean = false,
        bubbleUserBubbleWaterGlass: Boolean = false,
        bubbleAiBubbleLiquidGlass: Boolean = false,
        bubbleAiBubbleWaterGlass: Boolean = false,
        initialThinkingExpanded: Boolean = false,
        expandThinkToolsGroups: Boolean = false,
        includeBackground: Boolean = true,
        borderWidthDp: Float = 1.5f,
        forceShowThinkingProcess: Boolean = false,
        width: Int = 1440
    ): File {
        try {
            AppLogger.d(TAG, "开始生成消息图片（ComposeView），消息数量: ${messages.size}, 宽度: $width, 风格: $chatStyle")

            if (messages.isEmpty()) {
                throw IllegalArgumentException("Message list cannot be empty")
            }
            val allowExpandedThinkingFullHeight = initialThinkingExpanded
            val themeSnapshot =
                ActivePromptManager.getInstance(context)
                    .activeThemePreferenceSnapshotFlow
                    .first()
            
            // 获取 Activity 和根视图，用于临时附加 ComposeView
            val activity = context.findActivity() ?: throw IllegalStateException("Context is not an Activity.")
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            
            // 在主线程上创建、附加和捕获 Composable 内容
            val bitmap = withContext(Dispatchers.Main) {
                // 检查当前是否为暗色模式
                val isDarkTheme = (context.resources.configuration.uiMode and 
                    Configuration.UI_MODE_NIGHT_MASK) == 
                    Configuration.UI_MODE_NIGHT_YES
                
                // 根据暗色模式选择颜色方案
                val colorScheme = if (isDarkTheme) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                }
                
                // 创建 ComposeView，包含所有消息内容
                val composeView = ComposeView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setContent {
                        // 为截图渲染提供只使用软件 Bitmap 的 ImageLoader，避免
                        // "Software rendering doesn't support hardware bitmaps" 崩溃
                        val softwareImageLoader = ImageLoader.Builder(context)
                            .allowHardware(false)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()

                        CompositionLocalProvider(
                            LocalImageLoader provides softwareImageLoader,
                            LocalThemePreferenceSnapshot provides themeSnapshot,
                        ) {
                            MaterialTheme(colorScheme = colorScheme) {
                            // 不再使用 Capturable，直接渲染内容
                            val density = LocalDensity.current
                            val widthDp = with(density) { width.toDp() }
                            val colorScheme = MaterialTheme.colorScheme
                            val useBackgroundImage = themeSnapshot.useBackgroundImage
                            val backgroundImageUri = themeSnapshot.backgroundImageUri
                            val backgroundImageOpacity = themeSnapshot.backgroundImageOpacity
                            val backgroundMediaType = themeSnapshot.backgroundMediaType
                            val videoBackgroundMuted = themeSnapshot.videoBackgroundMuted
                            val videoBackgroundLoop = themeSnapshot.videoBackgroundLoop
                            val useBackgroundBlur = themeSnapshot.useBackgroundBlur
                            val backgroundBlurRadius = themeSnapshot.backgroundBlurRadius


                            val cardBackgroundColor = if (includeBackground) Color.Transparent else colorScheme.surface
                            val headerBackgroundColor = if (includeBackground) Color.Transparent else colorScheme.surfaceVariant
                            val contentBackgroundColor = if (includeBackground) Color.Transparent else colorScheme.surface

                            Box(
                                modifier = Modifier
                                    .width(widthDp)
                                    .wrapContentHeight()
                            ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val cardShape = RoundedCornerShape(12.dp)

                                        Box(
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .clip(cardShape)
                                                    .border(
                                                        width = borderWidthDp.dp,
                                                        color = colorScheme.outlineVariant,
                                                        shape = cardShape
                                                    )
                                                    .background(cardBackgroundColor)
                                        ) {
                                            if (includeBackground) {
                                                AppBackgroundLayer(
                                                    darkTheme = isDarkTheme,
                                                    useBackgroundImage = useBackgroundImage,
                                                    backgroundImageUri = backgroundImageUri,
                                                    backgroundImageOpacity = backgroundImageOpacity,
                                                    backgroundMediaType = backgroundMediaType,
                                                    videoBackgroundMuted = videoBackgroundMuted,
                                                    videoBackgroundLoop = videoBackgroundLoop,
                                                    useBackgroundBlur = useBackgroundBlur,
                                                    backgroundBlurRadius = backgroundBlurRadius,
                                                    modifier = Modifier.matchParentSize()
                                                )
                                            }

                                            Column(
                                                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                            ) {
                                                // 顶部品牌栏：Logo + "Operit AI"
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(headerBackgroundColor)
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    // Logo
                                                    Image(
                                                        painter = painterResource(id = com.ai.assistance.operit.R.drawable.ic_launcher_simple_foreground),
                                                        contentDescription = "Operit Logo",
                                                        modifier = Modifier.size(48.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    // 品牌名称
                                                    Text(
                                                        text = "Operit AI",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.onSurface
                                                    )
                                                }

                                                // 分隔线
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(colorScheme.outlineVariant)
                                                )

                                                // 消息内容区域
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .wrapContentHeight()
                                                        .background(contentBackgroundColor)
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 创建消息副本，清除 contentStream，确保只使用 content 字段
                                                    val staticMessages = messages.map { message ->
                                                        message.copy(contentStream = null)
                                                    }

                                                    staticMessages.forEach { message ->
                                                        when (chatStyle) {
                                                            ChatStyle.BUBBLE -> {
                                                                BubbleStyleChatMessage(
                                                                    message = message,
                                                                    userMessageColor = userMessageColor,
                                                                    aiMessageColor = aiMessageColor,
                                                                    userTextColor = userTextColor,
                                                                    aiTextColor = aiTextColor,
                                                                    systemMessageColor = systemMessageColor,
                                                                    systemTextColor = systemTextColor,
                                                                    userMessageLiquidGlassEnabled = bubbleUserBubbleLiquidGlass,
                                                                    userMessageWaterGlassEnabled = bubbleUserBubbleWaterGlass,
                                                                    aiMessageLiquidGlassEnabled = bubbleAiBubbleLiquidGlass,
                                                                    aiMessageWaterGlassEnabled = bubbleAiBubbleWaterGlass,
                                                                    initialThinkingExpanded = initialThinkingExpanded,
                                                                    allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                                                                    expandThinkToolsGroups = expandThinkToolsGroups,
                                                                    forceShowThinkingProcess = forceShowThinkingProcess,
                                                                    enableDialogs = false
                                                                )
                                                            }
                                                            ChatStyle.CURSOR -> {
                                                                CursorStyleChatMessage(
                                                                    message = message,
                                                                    userMessageColor = userMessageColor,
                                                                    userMessageLiquidGlassEnabled = cursorUserBubbleLiquidGlass,
                                                                    userMessageWaterGlassEnabled = cursorUserBubbleWaterGlass,
                                                                    aiMessageColor = aiMessageColor,
                                                                    userTextColor = userTextColor,
                                                                    aiTextColor = aiTextColor,
                                                                    systemMessageColor = systemMessageColor,
                                                                    systemTextColor = systemTextColor,
                                                                    thinkingBackgroundColor = thinkingBackgroundColor,
                                                                    thinkingTextColor = thinkingTextColor,
                                                                    supportToolMarkup = true,
                                                                    initialThinkingExpanded = initialThinkingExpanded,
                                                                    allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                                                                    expandThinkToolsGroups = expandThinkToolsGroups,
                                                                    forceShowThinkingProcess = forceShowThinkingProcess,
                                                                    enableDialogs = false
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 将 ComposeView 包装在 ScrollView 中，以支持任意高度
                val scrollView = ScrollView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    // 隐藏滚动条
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    addView(composeView, ViewGroup.LayoutParams(
                        width,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))
                    // 平移到屏幕宽度之外，而不是 INVISIBLE 或裁剪掉：ScrollView 仍以正常尺寸
                    // 挂在 rootView 下参与真实绘制流程，Compose 才会在背景图异步加载完成后正常重绘；
                    translationX = context.resources.displayMetrics.widthPixels.toFloat() + width
                }

                // 设置 ScrollView 布局参数
                scrollView.layoutParams = ViewGroup.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                // 添加到根视图（视图在屏幕外，用户看不到）
                rootView.addView(scrollView)
                
                // 手动触发测量和布局，确保内容完全展开
                // 使用 EXACTLY 模式指定宽度，UNSPECIFIED 模式让高度自由扩展
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                scrollView.measure(widthMeasureSpec, heightMeasureSpec)
                scrollView.layout(0, 0, scrollView.measuredWidth, scrollView.measuredHeight)
                
                AppLogger.d(TAG, "ScrollView 测量完成，尺寸: ${scrollView.measuredWidth}x${scrollView.measuredHeight}")
                
                // 等待 Compose 完成布局（给它一些时间）
                delay(500)
                
                val capturedBitmap: Bitmap
                try {
                    // 使用 ScrollView 子视图的完整高度创建 Bitmap
                    // 这是关键：getChildAt(0).height 获取完整的内容高度
                    val contentHeight = scrollView.getChildAt(0).height
                    AppLogger.d(TAG, "内容完整高度: $contentHeight")
                    
                    var tempBitmap = Bitmap.createBitmap(
                        scrollView.width,
                        contentHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(tempBitmap)
                    
                    // includeBackground=true 时，外围保持透明，仅卡片内部渲染应用背景
                    val backgroundColor = if (includeBackground) {
                        AndroidColor.TRANSPARENT
                    } else if (isDarkTheme) {
                        AndroidColor.BLACK
                    } else {
                        AndroidColor.WHITE
                    }
                    canvas.drawColor(backgroundColor)
                    
                    scrollView.draw(canvas)
                    
                    // 检查是否为硬件 Bitmap，如果是则转换为软件 Bitmap
                    // 软件渲染不支持硬件 Bitmap，需要转换为软件 Bitmap
                    capturedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && tempBitmap.config == Bitmap.Config.HARDWARE) {
                        AppLogger.d(TAG, "检测到硬件 Bitmap，转换为软件 Bitmap")
                        val softwareBitmap = tempBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        tempBitmap.recycle()
                        softwareBitmap
                    } else {
                        tempBitmap
                    }
                    
                    AppLogger.d(TAG, "捕获成功，图片尺寸: ${capturedBitmap.width}x${capturedBitmap.height}")
                } catch (e: Throwable) {
                    AppLogger.e(TAG, "捕获失败", e)
                    throw RuntimeException(context.getString(R.string.message_image_capture_failed, e.message ?: ""), e)
                } finally {
                    AppLogger.d(TAG, "从窗口移除 ScrollView")
                    // 确保无论成功还是失败，都将视图移除
                    rootView.removeView(scrollView)
                }
                capturedBitmap
            }
            
            // 在 IO 线程上保存文件
            return withContext(Dispatchers.IO) {
                val outputDir = File(context.cacheDir, "shared_images")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val outputFile = File(outputDir, "messages_$timestamp.png")
                
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                
                AppLogger.d(TAG, "图片已保存到: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
                
                // 回收 Bitmap
                bitmap.recycle()
                
                outputFile
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成消息图片失败", e)
            throw e
        }
    }
}


private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
