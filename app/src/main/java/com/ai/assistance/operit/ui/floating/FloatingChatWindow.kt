package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.features.chat.components.ChatToastHost
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingChatBallMode
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingResultDisplay
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingVoiceBallMode
import com.ai.assistance.operit.ui.floating.ui.fullscreen.FloatingFullscreenMode
import com.ai.assistance.operit.ui.floating.ui.screenocr.FloatingScreenOcrMode
import com.ai.assistance.operit.ui.floating.ui.window.screen.FloatingChatWindowMode
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.rememberActiveThemePreferenceSnapshot

/**
 * 悬浮聊天窗口的主要UI组件 - 重构版
 *
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param ballSize 球的大小
 * @param windowScale 窗口缩放比例
 * @param onScaleChange 缩放比例变化的回调
 * @param currentMode 当前的显示模式 (窗口或球)
 * @param previousMode 上一个显示模式，用于回退
 * @param onModeChange 模式切换的回调
 * @param onMove 悬浮窗移动的回调，传递相对移动距离和当前缩放比例
 * @param snapToEdge 靠边收起的回调
 * @param isAtEdge 是否处于屏幕边缘
 * @param screenWidth 屏幕宽度参数，用于边界检测
 * @param screenHeight 屏幕高度参数，用于边界检测
 * @param currentX 当前窗口X坐标
 * @param currentY 当前窗口Y坐标
 * @param saveWindowState 保存窗口状态的回调
 * @param onSendMessage 发送消息的回调
 * @param onCancelMessage 取消消息的回调
 * @param onAttachmentRequest 附件请求回调
 * @param attachments 当前附件列表
 * @param onRemoveAttachment 删除附件回调
 * @param onInputFocusRequest 请求输入焦点的回调，参数为true时请求获取焦点，false时释放焦点
 * @param chatService 聊天服务实例，用于访问音频焦点管理器
 * @param windowState 窗口状态
 */
@Composable
fun FloatingChatWindow(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        ballSize: Dp = 48.dp,
        windowScale: Float = 1.0f,
        onScaleChange: (Float) -> Unit = {},
        currentMode: FloatingMode = FloatingMode.WINDOW,
        previousMode: FloatingMode = FloatingMode.WINDOW,
        onModeChange: (FloatingMode) -> Unit = {},
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
) {
    val themeSnapshot = rememberActiveThemePreferenceSnapshot()
    val floatContext =
            rememberFloatContext(
                    messages = messages,
                    width = width,
                    height = height,
                    onClose = onClose,
                    onResize = onResize,
                    ballSize = ballSize,
                    windowScale = windowScale,
                    onScaleChange = onScaleChange,
                    currentMode = currentMode,
                    previousMode = previousMode,
                    onModeChange = onModeChange,
                    onMove = onMove,
                    snapToEdge = snapToEdge,
                    isAtEdge = isAtEdge,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    currentX = currentX,
                    currentY = currentY,
                    saveWindowState = saveWindowState,
                    onSendMessage = onSendMessage,
                    onCancelMessage = onCancelMessage,
                    onAttachmentRequest = onAttachmentRequest,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onInputFocusRequest = onInputFocusRequest,
                    chatService = chatService,
                    windowState = windowState,
                    inputProcessingState = inputProcessingState
            )
    val chatCore = remember(chatService) { chatService?.getChatCore() }
    val toastEventState =
        chatCore?.getUiStateDelegate()?.toastEvent?.collectAsState(initial = null)
            ?: remember { mutableStateOf<String?>(null) }
    val toastEvent by toastEventState

    // 将窗口缩放限制在合理范围内 - 已通过回调和状态源头处理，不再需要
    // LaunchedEffect(initialWindowScale) {
    //     floatContext.windowScale = initialWindowScale.coerceIn(0.5f, 1.0f)
    // }

    // 监听输入状态变化
    LaunchedEffect(floatContext.showInputDialog) {
        // 通知服务需要切换焦点模式
        floatContext.onInputFocusRequest?.invoke(floatContext.showInputDialog)

        // 如果隐藏输入框，清空消息
        if (!floatContext.showInputDialog) {
            floatContext.userMessage = ""
        }
    }

    // 根据currentMode参数渲染对应界面，使用AnimatedContent添加炫酷过渡动画
    CompositionLocalProvider(LocalThemePreferenceSnapshot provides themeSnapshot) {
        Box {
            AnimatedContent(
            targetState = currentMode, // 只监听 currentMode，避免消息更新时触发动画
            transitionSpec = {
                val targetMode = targetState
                val initialMode = initialState

                // 判断是否从球模式切换到其他模式，或从其他模式切换到球模式
                val isToBall = targetMode == FloatingMode.BALL || targetMode == FloatingMode.VOICE_BALL
                val isFromBall = initialMode == FloatingMode.BALL || initialMode == FloatingMode.VOICE_BALL

                // 判断是否是窗口和全屏之间的切换
                val isWindowFullscreenTransition =
                    (initialMode == FloatingMode.WINDOW && (targetMode == FloatingMode.FULLSCREEN || targetMode == FloatingMode.SCREEN_OCR)) ||
                    ((initialMode == FloatingMode.FULLSCREEN || initialMode == FloatingMode.SCREEN_OCR) && targetMode == FloatingMode.WINDOW)

                if (isWindowFullscreenTransition) {
                    // 窗口 ↔ 全屏：使用简洁的缩放 + 淡入淡出动画
                    (fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(160)) +
                            scaleOut(targetScale = 1.03f, animationSpec = tween(160))
                        )
                } else if (isToBall && !isFromBall) {
                    // 其他模式 -> 球模式：窗口快速缩小消失，球从极小爆炸式出现
                    (fadeIn(animationSpec = tween(350, delayMillis = 150, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.0f, animationSpec = tween(350, delayMillis = 150, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.0f, animationSpec = tween(150))
                        )
                } else if (isFromBall && !isToBall) {
                    // 球模式 -> 其他模式：球瞬间消失，窗口从中心炸开展现
                    (fadeIn(animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing)) +
                     scaleIn(initialScale = 0.0f, animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(100)) +
                            scaleOut(targetScale = 0.0f, animationSpec = tween(100))
                        )
                } else {
                    // 球模式之间切换：快速交叉淡入淡出
                    fadeIn(animationSpec = tween(250, delayMillis = 100))
                        .togetherWith(fadeOut(animationSpec = tween(100)))
                }
            },
            label = "mode_transition"
            ) { mode -> // 只接收 currentMode，不是整个 context
                when (mode) {
                    FloatingMode.WINDOW -> FloatingChatWindowMode(floatContext = floatContext)
                    FloatingMode.BALL -> {
                        // 根据前一个模式决定显示哪种球
                        when (previousMode) {
                            FloatingMode.VOICE_BALL -> FloatingVoiceBallMode(floatContext = floatContext)
                            else -> FloatingChatBallMode(floatContext = floatContext)
                        }
                    }
                    FloatingMode.VOICE_BALL -> FloatingVoiceBallMode(floatContext = floatContext)
                    FloatingMode.FULLSCREEN -> FloatingFullscreenMode(floatContext = floatContext)
                    FloatingMode.RESULT_DISPLAY -> FloatingResultDisplay(floatContext = floatContext)
                    FloatingMode.SCREEN_OCR -> FloatingScreenOcrMode(floatContext = floatContext)
                }
            }

            ChatToastHost(
                message = toastEvent,
                onDismiss = { chatCore?.getUiStateDelegate()?.clearToastEvent() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                maxWidth = 360.dp,
                maxHeight = 200.dp
            )
        }
    }
}
