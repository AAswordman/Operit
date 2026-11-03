package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.chat.attachment.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.core.config.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.floating.ui.window.models.ResizeEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 简化后的FloatContext类，移除了路由耦合逻辑 */
@Composable
fun rememberFloatContext(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        ballSize: Dp = 48.dp,
        windowScale: Float = 1.0f,
        onScaleChange: (Float) -> Unit,
        currentMode: FloatingMode,
        previousMode: FloatingMode = FloatingMode.WINDOW,
        onModeChange: (FloatingMode) -> Unit,
        onMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
        snapToEdge: (Boolean) -> Unit = { _ -> },
        isAtEdge: Boolean = false,
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f,
        saveWindowState: (() -> Unit)? = null,
        onSendMessage: ((String, PromptFunctionType) -> Unit)? = null,
        onCancelMessage: (() -> Unit)? = null,
        onAttachmentRequest: ((String) -> Unit)? = null,
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: ((String) -> Unit)? = null,
        onInputFocusRequest: ((Boolean) -> Unit)? = null,
        chatService: FloatingChatService? = null,
        windowState: FloatingWindowState? = null,
        inputProcessingState: State<InputProcessingState> = mutableStateOf(InputProcessingState.Idle)
): FloatContext {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 创建稳定的 FloatContext 实例，只依赖真正不会改变的架构性参数
    // 这样可以避免不必要的实例重建，提高性能
    val floatContext = remember(
            chatService,      // 服务实例通常是稳定的
            windowState,      // 窗口状态实例是稳定的
            density,          // Density 在配置改变前是稳定的
            scope             // CoroutineScope 是稳定的
    ) {
        FloatContext(
                initialMessages = messages,
                initialWidth = width,
                initialHeight = height,
                ballSize = ballSize,
                initialWindowScale = windowScale,
                initialMode = currentMode,
                initialPreviousMode = previousMode,
                initialIsAtEdge = isAtEdge,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                initialX = currentX,
                initialY = currentY,
                initialAttachments = attachments,
                density = density,
                coroutineScope = scope,
                chatService = chatService,
                windowState = windowState,
                inputProcessingState = inputProcessingState
        )
    }

    // 使用 rememberUpdatedState 来持有最新的回调函数，避免作为 remember 的 key
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnModeChange by rememberUpdatedState(onModeChange)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentSnapToEdge by rememberUpdatedState(snapToEdge)
    val currentSaveWindowState by rememberUpdatedState(saveWindowState)
    val currentOnSendMessage by rememberUpdatedState(onSendMessage)
    val currentOnCancelMessage by rememberUpdatedState(onCancelMessage)
    val currentOnAttachmentRequest by rememberUpdatedState(onAttachmentRequest)
    val currentOnRemoveAttachment by rememberUpdatedState(onRemoveAttachment)
    val currentOnInputFocusRequest by rememberUpdatedState(onInputFocusRequest)

    // 使用 SideEffect 更新回调函数和频繁变化的状态
    SideEffect {
        // 更新回调函数
        floatContext.onClose = currentOnClose
        floatContext.onResize = currentOnResize
        floatContext.onScaleChange = currentOnScaleChange
        floatContext.onModeChange = currentOnModeChange
        floatContext.onMove = currentOnMove
        floatContext.snapToEdge = currentSnapToEdge
        floatContext.saveWindowState = currentSaveWindowState
        floatContext.onSendMessage = currentOnSendMessage
        floatContext.onCancelMessage = currentOnCancelMessage
        floatContext.onAttachmentRequest = currentOnAttachmentRequest
        floatContext.onRemoveAttachment = currentOnRemoveAttachment
        floatContext.onInputFocusRequest = currentOnInputFocusRequest
        
        // 更新频繁变化的数据
        floatContext.messages = messages
        floatContext.windowWidthState = width
        floatContext.windowHeightState = height
        floatContext.windowScale = windowScale
        floatContext.currentMode = currentMode
        floatContext.previousMode = previousMode
        floatContext.isAtEdge = isAtEdge
        floatContext.currentX = currentX
        floatContext.currentY = currentY
        floatContext.attachments = attachments
    }

    return floatContext
}

/** 简化的悬浮窗状态与回调上下文 */
class FloatContext(
        initialMessages: List<ChatMessage>,
        initialWidth: Dp,
        initialHeight: Dp,
        val ballSize: Dp,
        initialWindowScale: Float,
        initialMode: FloatingMode,
        initialPreviousMode: FloatingMode,
        initialIsAtEdge: Boolean,
        val screenWidth: Dp,
        val screenHeight: Dp,
        initialX: Float,
        initialY: Float,
        initialAttachments: List<AttachmentInfo>,
        val density: Density,
        val coroutineScope: CoroutineScope,
        val chatService: FloatingChatService? = null,
        val windowState: FloatingWindowState? = null,
        val inputProcessingState: State<InputProcessingState>
) {
    // 回调函数使用 var 以便通过 SideEffect 更新
    var onClose: () -> Unit = {}
    var onResize: (Dp, Dp) -> Unit = { _, _ -> }
    var onScaleChange: (Float) -> Unit = {}
    var onModeChange: (FloatingMode) -> Unit = {}
    var onMove: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var snapToEdge: (Boolean) -> Unit = {}
    var saveWindowState: (() -> Unit)? = null
    var onSendMessage by mutableStateOf<((String, PromptFunctionType) -> Unit)?>(null)
    var onCancelMessage: (() -> Unit)? = null
    var onAttachmentRequest: ((String) -> Unit)? = null
    var onRemoveAttachment: ((String) -> Unit)? = null
    var onInputFocusRequest: ((Boolean) -> Unit)? = null

    // 使用 mutableStateOf 让 Compose 能感知变化
    var messages by mutableStateOf(initialMessages)
    var windowWidthState by mutableStateOf(initialWidth)
    var windowHeightState by mutableStateOf(initialHeight)
    var windowScale by mutableStateOf(initialWindowScale)
    var currentMode by mutableStateOf(initialMode)
    var previousMode by mutableStateOf(initialPreviousMode)
    var isAtEdge by mutableStateOf(initialIsAtEdge)
    var currentX by mutableStateOf(initialX)
    var currentY by mutableStateOf(initialY)
    var attachments by mutableStateOf(initialAttachments)
    // inputProcessingState is a State object, accessed directly

    // 动画与转换相关状态
    val animatedAlpha = Animatable(1f)
    val transitionFeedback = Animatable(0f)

    // 大小调整相关状态
    var isEdgeResizing: Boolean = false
    var activeEdge: ResizeEdge = ResizeEdge.NONE
    var initialWindowWidth: Float = 0f
    var initialWindowHeight: Float = 0f

    // 对话框与内容显示状态
    var showInputDialog: Boolean by mutableStateOf(false)
    var userMessage: String by mutableStateOf("")
    var contentVisible: Boolean by mutableStateOf(true)
    var showAttachmentPanel: Boolean by mutableStateOf(false)
    
     // 标识是否刚完成了屏幕圈选，用于返回全屏模式时自动勾选"屏幕内容"
    var pendingScreenSelection: Boolean by mutableStateOf(false)
}
