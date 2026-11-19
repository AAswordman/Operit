package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.floating.FloatContext
import kotlin.math.*

import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.floating.voice.SpeechInteractionManager
import kotlinx.coroutines.launch

// 内部状态枚举
private const val StateIdle = 0
private const val StatePressing = 1
private const val StateLoading = 2
private const val StateReplying = 3

/**
 * 通用的 Siri 风格悬浮球组件
 * 包含完整的状态管理、手势交互和渲染逻辑
 */
@Composable
fun SiriBall(
    floatContext: FloatContext,
    onClick: () -> Unit,
    onTriggerResult: () -> Unit
) {
    // Siri配色 - 精简为4个主色调（蓝、紫、粉、青）
    val mainColor = Color(0xFF0A84FF)      // 主蓝色
    val accentColor1 = Color(0xFFBF5AF2)   // 紫色
    val accentColor2 = Color(0xFFFF375F)   // 粉红色
    val accentColor3 = Color(0xFF00D4FF)   // 青色
    
    // 交互状态
    var ballState by remember { mutableIntStateOf(StateIdle) }
    var pressStartTime by remember { mutableStateOf(0L) }
    val particleSystem = rememberParticleSystem()
    
    // 语音交互管理器
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechManager = remember(context, scope) {
        SpeechInteractionManager(
            context = context,
            coroutineScope = scope,
            onSpeechResult = { text, _ ->
                // 收到语音结果，发送消息
                floatContext.onSendMessage?.invoke(text, PromptFunctionType.VOICE)
            },
            onStateChange = { msg ->
                // 根据状态消息判断是否需要重置状态
                if (msg == context.getString(R.string.floating_didnt_hear_clearly)) {
                    ballState = StateIdle
                }
            }
        )
    }

    // 初始化与清理语音服务
    DisposableEffect(Unit) {
        scope.launch {
            // 麦克风采集资格由 FloatingChatService 的 microphone 前台服务类型提供，
            // 语音球不需要再对 ComposeView 根节点做焦点模拟。
            speechManager.initialize()
        }
        onDispose {
            speechManager.cleanup()
        }
    }

    // 收集语音识别结果流并反馈给管理器处理
    LaunchedEffect(speechManager) {
        speechManager.recognitionResultFlow.collect { result ->
            speechManager.handleRecognitionResult(result.text, result.isFinal)
        }
    }
    
    // 监听 AI 处理状态，完成后触发结果展示
    val aiState by floatContext.inputProcessingState
    LaunchedEffect(aiState, ballState) {
        // 当处于 Loading 状态且 AI 处理完成时
        if (ballState == StateLoading && aiState is InputProcessingState.Completed) {
            onTriggerResult()
            ballState = StateIdle
        }
    }
    
    // 按压缩放动画
    val isPressed = ballState == StatePressing
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "pressScale"
    )
    
    // Loading 旋转动画
    val loadingRotation = remember { Animatable(0f) }
    LaunchedEffect(ballState == StateLoading) {
        if (ballState == StateLoading) {
            loadingRotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing)
                )
            )
        } else {
            loadingRotation.snapTo(0f)
        }
    }
    
    // 更新粒子系统 (只在 Pressing 状态显示)
    particleSystem.UpdateEffect(isPressed)
    
    // 淡出动画状态
    val isFadingOut = floatContext.windowState?.ballExploding?.value ?: false
    val fadeOutProgress = remember { Animatable(0f) }
    
    // 监听淡出触发
    LaunchedEffect(isFadingOut) {
        if (isFadingOut) {
            fadeOutProgress.snapTo(0f)
            fadeOutProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        } else {
            fadeOutProgress.snapTo(0f)
        }
    }
    
    // 动画
    val infiniteTransition = rememberInfiniteTransition(label = "siri")
    
    // 慢速旋转 - 更优雅
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 柔和呼吸
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    
    // 外圈音波 - 第一层
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Scale"
    )
    
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Alpha"
    )
    
    // 外圈音波 - 第二层
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(833)
        ),
        label = "ripple2Scale"
    )
    
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(833)
        ),
        label = "ripple2Alpha"
    )
    
    // 外圈音波 - 第三层
    val ripple3Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1666)
        ),
        label = "ripple3Scale"
    )
    
    val ripple3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1666)
        ),
        label = "ripple3Alpha"
    )
    
    Box(
        modifier = Modifier
            .size(floatContext.ballSize * 1.6f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { ballState = StatePressing },
                    onDragEnd = { 
                        floatContext.saveWindowState?.invoke()
                        ballState = StateIdle
                    },
                    onDragCancel = { ballState = StateIdle },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        floatContext.onMove(
                            dragAmount.x,
                            dragAmount.y,
                            floatContext.windowScale
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    }
                    // 长按功能已禁用
                    // onPress = {
                    //     pressStartTime = System.currentTimeMillis()
                    //     ballState = StatePressing
                    //     // 开始录音
                    //     speechManager.startListening()
                    //     
                    //     val released = tryAwaitRelease()
                    //     // 松手逻辑
                    //     if (released) {
                    //         if (System.currentTimeMillis() - pressStartTime >= 300) {
                    //             // 长按结束 -> 停止录音并发送 -> 进入 Loading 等待 AI 回复
                    //             speechManager.stopListening(isCancel = false)
                    //             ballState = StateLoading
                    //         } else {
                    //             // 短按 -> 取消录音 -> 重置为 Idle (onTap 会接管)
                    //             speechManager.stopListening(isCancel = true)
                    //             ballState = StateIdle
                    //         }
                    //     } else {
                    //         // 手势被取消 -> 取消录音
                    //         speechManager.stopListening(isCancel = true)
                    //         ballState = StateIdle
                    //     }
                    // }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(floatContext.ballSize * 3.0f)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            // 应用按压缩放
            val baseRadius = (size.minDimension / 2.0f) * pressScale
            
            // 淡出效果：只改变透明度，不改变缩放
            val fadeAlpha = if (isFadingOut) 1f - fadeOutProgress.value else 1f
            
            if (fadeAlpha <= 0.01f) {
                // 淡出完成，不绘制任何内容
                return@Canvas
            }
            
            // 0. 绘制后方粒子（3D效果 - 后景）
            with(particleSystem) {
                drawBackParticles(center, baseRadius * 0.5f)
            }
            
            // 1. 外圈音波扩散（3层，简洁的圆环）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        mainColor.copy(alpha = ripple3Alpha * 0.15f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple3Scale
                ),
                center = center,
                radius = baseRadius * ripple3Scale
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor1.copy(alpha = ripple2Alpha * 0.2f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple2Scale
                ),
                center = center,
                radius = baseRadius * ripple2Scale
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor3.copy(alpha = ripple1Alpha * 0.25f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple1Scale
                ),
                center = center,
                radius = baseRadius * ripple1Scale
            )
            
            // 2. 底部光晕（柔和的蓝紫光）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mainColor.copy(alpha = 0.3f * fadeAlpha),
                        accentColor1.copy(alpha = 0.2f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * breathe * 0.7f
                ),
                center = center,
                radius = baseRadius * breathe * 0.7f,
                blendMode = BlendMode.Screen
            )
            
            // 3. 流动的彩色光斑（4个色块，慢速旋转）
            // 主蓝色光斑
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation,
                distance = baseRadius * 0.2f * breathe,
                color = mainColor.copy(alpha = fadeAlpha),
                size = 0.7f
            )
            
            // 紫色光斑（相位差90度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 90f,
                distance = baseRadius * 0.25f * breathe,
                color = accentColor1.copy(alpha = fadeAlpha),
                size = 0.65f
            )
            
            // 粉红色光斑（相位差180度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 180f,
                distance = baseRadius * 0.22f * breathe,
                color = accentColor2.copy(alpha = fadeAlpha),
                size = 0.6f
            )
            
            // 青色光斑（相位差270度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 270f,
                distance = baseRadius * 0.23f * breathe,
                color = accentColor3.copy(alpha = fadeAlpha),
                size = 0.68f
            )
            
            // 4. 中心明亮核心
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.7f * fadeAlpha),
                        mainColor.copy(alpha = 0.6f * fadeAlpha),
                        accentColor1.copy(alpha = 0.5f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.65f * breathe
                ),
                center = center,
                radius = baseRadius * 0.65f * breathe,
                blendMode = BlendMode.Screen
            )
            
            // 5. 玻璃球体高光（模拟3D质感）
            val highlightCenter = Offset(
                center.x - baseRadius * 0.25f,
                center.y - baseRadius * 0.25f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f * fadeAlpha),
                        Color.White.copy(alpha = 0.25f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = baseRadius * 0.35f
                ),
                center = highlightCenter,
                radius = baseRadius * 0.35f,
                blendMode = BlendMode.Screen
            )
            
            // 6. 整体柔和外边界（让球体边缘更自然）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f * fadeAlpha),
                        Color.White.copy(alpha = 0.15f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * breathe
                ),
                center = center,
                radius = baseRadius * breathe
            )
            
            // 7. 绘制前方粒子（3D效果 - 前景）
            with(particleSystem) {
                drawFrontParticles(center, baseRadius * 0.5f)
            }
            
            // 8. 绘制 Loading (只在 Loading 状态)
            if (ballState == StateLoading) {
                 drawArc(
                     color = Color.White,
                     startAngle = loadingRotation.value,
                     sweepAngle = 270f,
                     useCenter = false,
                     topLeft = Offset(center.x - baseRadius * 0.5f, center.y - baseRadius * 0.5f),
                     size = Size(baseRadius, baseRadius),
                     style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                 )
            }
        }
    }
}

/**
 * 绘制流动的彩色光斑（类似Siri的色块）
 */
private fun DrawScope.drawColorBlob(
    center: Offset,
    radius: Float,
    angle: Float,
    distance: Float,
    color: Color,
    size: Float
) {
    val rad = angle * PI / 180f
    val blobCenter = Offset(
        center.x + distance * cos(rad).toFloat(),
        center.y + distance * sin(rad).toFloat()
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.8f),
                color.copy(alpha = 0.5f),
                color.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = blobCenter,
            radius = radius * size
        ),
        center = blobCenter,
        radius = radius * size,
        blendMode = BlendMode.Screen
    )
}
