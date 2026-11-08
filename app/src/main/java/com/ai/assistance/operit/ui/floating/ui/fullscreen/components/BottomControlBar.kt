package com.ai.assistance.operit.ui.floating.ui.fullscreen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeOutlinedTextField as OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Color as AndroidColor

/**
 * 底部控制栏组件
 * 包含返回按钮、麦克风按钮和缩小按钮
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun BottomControlBar(
    visible: Boolean,
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    floatContext: FloatContext,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    isWaveActive: Boolean,
    onToggleWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    onUserMessageChange: (String) -> Unit,
    attachScreenContent: Boolean,
    onAttachScreenContentChange: (Boolean) -> Unit,
    attachNotifications: Boolean,
    onAttachNotificationsChange: (Boolean) -> Unit,
    attachLocation: Boolean,
    onAttachLocationChange: (Boolean) -> Unit,
    hasOcrSelection: Boolean,
    onHasOcrSelectionChange: (Boolean) -> Unit,
    isTtsMuted: Boolean,
    onToggleTtsMute: () -> Unit,
    onSendClick: () -> Unit,
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    // 底部输入模式：false = 文本输入框；true = 整条变成“按住说话”按钮
    var isHoldToSpeakMode by remember { mutableStateOf(false) }
    var isCancelRegion by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    // 简单的音量历史，用于在长按时绘制一个从右往左移动的波形条
    val volumeHistory = remember {
        mutableStateListOf<Float>().apply {
            repeat(24) { add(0f) }
        }
    }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeOffsetPx = (imeBottomPx - navigationBarBottomPx).coerceAtLeast(0)
    val attachmentTabsScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var allowBlankFocus by remember { mutableStateOf(false) }
    val pillHeight = 48.dp
    val holdToSpeakTriggerMs = 450L
    val iconRegionWidth = 72.dp
    // 取消区域：大致拖出胶囊高度（56dp）之外才算取消
    val cancelThresholdPx = with(density) { pillHeight.toPx() }

    // 在长按语音时，根据当前音量持续更新历史，用于绘制音量波形
    LaunchedEffect(volumeLevel, isPressed, isRecording) {
        if (isPressed && isRecording && volumeHistory.isNotEmpty()) {
            volumeHistory.removeAt(0)
            volumeHistory.add(volumeLevel.coerceIn(0f, 1f))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(220)
        ),
        exit = fadeOut(animationSpec = tween(120)) + slideOutVertically(
            targetOffsetY = { it / 3 },
            animationSpec = tween(160)
        ),
        modifier = modifier.graphicsLayer { translationY = -imeOffsetPx.toFloat() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val pillColor = when {
                isCancelRegion && isHoldToSpeakMode -> MaterialTheme.colorScheme.error
                isHoldToSpeakMode && isPressed -> Color(0xFFE0E0E0) // 按下时使用实心浅灰色
                else -> Color.White
            }

            val canSend = userMessage.isNotBlank() || attachScreenContent || attachNotifications || attachLocation || hasOcrSelection
            val glowPadding = 10.dp
            val glowBaseColors = listOf(
                Color(0xFF42A5F5),
                Color(0xFF26C6DA),
                Color(0xFF66BB6A)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(attachmentTabsScrollState)
                        .padding(horizontal = 12.dp)
                        .offset(y = 6.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isScreenOcrMode = floatContext.currentMode == FloatingMode.SCREEN_OCR
                    val isScreenOcrSelected = isScreenOcrMode || hasOcrSelection

                    // 朗读静音
                    GlassyChip(
                        selected = isTtsMuted,
                        text = "",
                        icon = if (isTtsMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        showIcon = true,
                        showText = false,
                        iconContentDescription = if (isTtsMuted) {
                            stringResource(R.string.theme_unmute)
                        } else {
                            stringResource(R.string.theme_mute)
                        },
                        onClick = onToggleTtsMute
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 屏幕内容
                    GlassyChip(
                        selected = attachScreenContent,
                        text = stringResource(R.string.floating_screen_content),
                        icon = Icons.Default.Check,
                        showIcon = attachScreenContent,
                        onClick = { onAttachScreenContentChange(!attachScreenContent) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 通知
                    GlassyChip(
                        selected = attachNotifications,
                        text = stringResource(R.string.floating_notification),
                        icon = Icons.Default.Check,
                        showIcon = attachNotifications,
                        onClick = { onAttachNotificationsChange(!attachNotifications) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 位置
                    GlassyChip(
                        selected = attachLocation,
                        text = stringResource(R.string.floating_position),
                        icon = Icons.Default.Check,
                        showIcon = attachLocation,
                        onClick = { onAttachLocationChange(!attachLocation) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 圈选识别
                    GlassyChip(
                        selected = isScreenOcrSelected,
                        text = if (hasOcrSelection) stringResource(R.string.floating_ocr_selected) else stringResource(R.string.floating_ocr_select),
                        icon = if (isScreenOcrSelected) Icons.Default.Check else Icons.Default.Crop,
                        showIcon = true,
                        onClick = {
                            if (hasOcrSelection) {
                                // 已有圈选内容，点击清除
                                onHasOcrSelectionChange(false)
                            } else if (isScreenOcrMode) {
                                floatContext.onModeChange(floatContext.previousMode)
                            } else {
                                floatContext.onModeChange(FloatingMode.SCREEN_OCR)
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    AnimatedGlowBorder(
                        modifier = Modifier.matchParentSize(),
                        glowPadding = glowPadding,
                        colors = glowBaseColors
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(glowPadding)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.10f)
                            )
                            .clip(CircleShape)
                            .background(pillColor)
                    ) {
                        OutlinedTextField(
                            value = userMessage,
                            onValueChange = { if (!isHoldToSpeakMode) onUserMessageChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pillHeight)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    canFocus = userMessage.isNotBlank() || allowBlankFocus
                                }
                            ,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            singleLine = true,
                            readOnly = isHoldToSpeakMode,
                            placeholder = {
                                if (!isHoldToSpeakMode) {
                                    Text(
                                        text = stringResource(R.string.floating_input_or_hold_voice),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            leadingIcon = {
                                IconButton(onClick = onToggleWaveMode) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = stringResource(R.string.floating_voice_call),
                                        tint = if (isWaveActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                if (!isHoldToSpeakMode) {
                                    IconButton(
                                        onClick = {
                                            allowBlankFocus = false
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onSendClick()
                                        },
                                        enabled = canSend
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = stringResource(R.string.floating_send),
                                            tint = if (canSend) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        )

                        if (userMessage.isBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(pillHeight)
                            ) {
                                Spacer(modifier = Modifier.width(iconRegionWidth))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(pillHeight)
                                        .pointerInput(cancelThresholdPx, userMessage) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)

                                                down.consume()
                                                allowBlankFocus = false
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()

                                                val releasedBeforeTimeout = withTimeoutOrNull(holdToSpeakTriggerMs) {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                                        if (event.changes.isEmpty()) {
                                                            return@withTimeoutOrNull true
                                                        }
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                        if (change == null) {
                                                            if (event.changes.none { it.pressed }) {
                                                                return@withTimeoutOrNull true
                                                            }
                                                            continue
                                                        }
                                                        if (!change.pressed) {
                                                            return@withTimeoutOrNull true
                                                        }
                                                    }
                                                }

                                                if (releasedBeforeTimeout == true) {
                                                    allowBlankFocus = true
                                                    focusRequester.requestFocus()
                                                    keyboardController?.show()
                                                    return@awaitEachGesture
                                                }

                                                val startPosition = down.position
                                                var totalDragY = 0f
                                                isHoldToSpeakMode = true
                                                isPressed = true
                                                isCancelRegion = false
                                                allowBlankFocus = false
                                                focusManager.clearFocus(force = true)
                                                keyboardController?.hide()
                                                onStartVoiceCapture()

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                                    if (event.changes.isEmpty()) {
                                                        allowBlankFocus = false
                                                        focusManager.clearFocus(force = true)
                                                        keyboardController?.hide()
                                                        onStopVoiceCapture(isCancelRegion)
                                                        isCancelRegion = false
                                                        isPressed = false
                                                        isHoldToSpeakMode = false
                                                        totalDragY = 0f
                                                        break
                                                    }

                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) {
                                                        allowBlankFocus = false
                                                        focusManager.clearFocus(force = true)
                                                        keyboardController?.hide()
                                                        onStopVoiceCapture(isCancelRegion)
                                                        isCancelRegion = false
                                                        isPressed = false
                                                        isHoldToSpeakMode = false
                                                        totalDragY = 0f
                                                        break
                                                    }

                                                    val position = change.position
                                                    val dy = position.y - startPosition.y
                                                    totalDragY = dy
                                                    isCancelRegion = abs(totalDragY) > cancelThresholdPx
                                                }
                                            }
                                        }
                                )
                                Spacer(modifier = Modifier.width(iconRegionWidth))
                            }
                        }
                    }

                    if (isHoldToSpeakMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(glowPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPressed && !isCancelRegion) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(pillHeight)
                                ) {
                                    val barCount = volumeHistory.size
                                    if (barCount > 0) {
                                        val horizontalMargin = size.width * 0.28f
                                        val availableWidth = size.width - horizontalMargin * 2f
                                        val barWidth = availableWidth / (barCount * 1.4f)
                                        val gap = barWidth * 0.4f
                                        val maxHeight = size.height * 0.3f
                                        val centerY = size.height / 2f

                                        volumeHistory.forEachIndexed { index: Int, value: Float ->
                                            val xRight = size.width - horizontalMargin - (barWidth + gap) * (barCount - 1 - index).toFloat()
                                            val barHeight = (value.coerceIn(0f, 1f)) * maxHeight
                                            val top = centerY - barHeight / 2f
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = Offset(xRight - barWidth, top),
                                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isCancelRegion) stringResource(R.string.floating_release_to_cancel) else stringResource(R.string.floating_release_to_finish),
                                    color = if (isCancelRegion) Color.White else Color.Black,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedGlowBorder(
    modifier: Modifier,
    glowPadding: androidx.compose.ui.unit.Dp,
    colors: List<Color>
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_border")
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_flow"
    )
    val bulgePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_bulge"
    )
    val anchors = listOf(0.08f, 0.22f, 0.35f, 0.50f, 0.64f, 0.78f, 0.92f)
    var activeAnchorIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val random = Random(System.currentTimeMillis())
        while (true) {
            delay(1800)
            activeAnchorIndex = random.nextInt(anchors.size)
        }
    }

    fun boostColor(color: Color, saturationBoost: Float, valueBoost: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hsv[1] = (hsv[1] * saturationBoost).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * valueBoost).coerceIn(0f, 1f)
        return Color(AndroidColor.HSVToColor(hsv)).copy(alpha = color.alpha)
    }

    Canvas(
        modifier = modifier.drawWithCache {
            val paddingPx = glowPadding.toPx()
            val innerRect = Rect(
                left = paddingPx,
                top = paddingPx,
                right = size.width - paddingPx,
                bottom = size.height - paddingPx
            )
            val baseRadius = innerRect.height / 2f
            val outerSpread = paddingPx * 1.2f
            val layers = 8
            val boostedColors = colors.map { boostColor(it, saturationBoost = 1.45f, valueBoost = 1.35f) }

            val roundRectPath = AndroidPath().apply {
                addRoundRect(
                    RectF(innerRect.left, innerRect.top, innerRect.right, innerRect.bottom),
                    baseRadius,
                    baseRadius,
                    AndroidPath.Direction.CW
                )
            }
            val pathMeasure = PathMeasure(roundRectPath, true)
            val pathLength = pathMeasure.length

            fun colorAt(t: Float): Color {
                if (colors.isEmpty()) return Color.White
                if (colors.size == 1) return colors.first()
                val value = ((t % 1f) + 1f) % 1f
                val scaled = value * (colors.size - 1)
                val index = floor(scaled).toInt().coerceIn(0, colors.size - 2)
                val localT = scaled - index
                return lerp(colors[index], colors[index + 1], localT)
            }

            onDrawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val angle = flowPhase * 2f * PI.toFloat()
                val gradientStart =
                    Offset(center.x + cos(angle) * size.width, center.y + sin(angle) * size.height)
                val gradientEnd =
                    Offset(center.x - cos(angle) * size.width, center.y - sin(angle) * size.height)

                // Base glowing ring layers
                for (i in 0 until layers) {
                    val progress = i / (layers - 1f)
                    val spread = outerSpread * (1f - progress)
                    val radius = baseRadius + spread
                    val alpha = 0.03f + 0.30f * (progress * progress)
                    val whiteFactor = progress * progress * 0.55f

                    val layerColors = boostedColors.map { color ->
                        lerp(color, Color.White, whiteFactor).copy(alpha = alpha)
                    }

                    val rect = Rect(
                        left = innerRect.left - spread,
                        top = innerRect.top - spread,
                        right = innerRect.right + spread,
                        bottom = innerRect.bottom + spread
                    )

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = layerColors,
                            start = gradientStart,
                            end = gradientEnd
                        ),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }

                // Single local bulge: expands outward then returns
                if (anchors.isNotEmpty()) {
                    val t = anchors[activeAnchorIndex.coerceIn(0, anchors.lastIndex)]
                    val pulse = kotlin.math.sin(PI.toFloat() * bulgePhase)
                    val alpha = 0.12f + 0.30f * pulse
                    val baseBulge = 3.0.dp.toPx()
                    val bulgeAmp = 7.0.dp.toPx()
                    val baseOut = 2.0.dp.toPx()
                    val radius = baseBulge + bulgeAmp * pulse

                    val pos = FloatArray(2)
                    val tan = FloatArray(2)
                    pathMeasure.getPosTan(pathLength * t, pos, tan)
                    val tanX = tan[0]
                    val tanY = tan[1]
                    val len = kotlin.math.sqrt(tanX * tanX + tanY * tanY).coerceAtLeast(0.0001f)
                    val normal = Offset(-tanY / len, tanX / len)
                    val centerPos = Offset(pos[0], pos[1]) + normal * (baseOut + bulgeAmp * 0.35f * pulse)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                boostColor(colorAt(t), saturationBoost = 1.55f, valueBoost = 1.45f)
                                    .copy(alpha = alpha),
                                Color.Transparent
                            ),
                            center = centerPos,
                            radius = radius
                        ),
                        radius = radius,
                        center = centerPos
                    )
                }

            }
        }
    ) {}
}

/**
 * 返回按钮
 */
@Composable
private fun BackButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            floatContext.onModeChange(FloatingMode.WINDOW)
        },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.floating_back_to_window),
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * 缩小成语音球按钮
 */
@Composable
private fun MinimizeToVoiceBallButton(
    floatContext: FloatContext,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = { floatContext.onModeChange(FloatingMode.VOICE_BALL) },
        modifier = modifier.size(42.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = stringResource(R.string.floating_shrink_to_ball),
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 麦克风按钮和拖动提示
 */
@Composable
private fun MicrophoneButtonWithHints(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    showDragHints: Boolean,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val isDraggingToCancel = remember { mutableStateOf(false) }
    val isDraggingToEdit = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 左侧编辑提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Edit,
            iconColor = MaterialTheme.colorScheme.primary,
            description = stringResource(R.string.floating_edit),
            isLeft = true,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp)
        )

        // 右侧取消提示
        DragHint(
            visible = showDragHints,
            icon = Icons.Default.Delete,
            iconColor = MaterialTheme.colorScheme.error,
            description = stringResource(R.string.floating_cancel),
            isLeft = false,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp)
        )

        // 麦克风按钮
        MicrophoneButton(
            isRecording = isRecording,
            isProcessingSpeech = isProcessingSpeech,
            isDraggingToCancel = isDraggingToCancel,
            isDraggingToEdit = isDraggingToEdit,
            onStartVoiceCapture = onStartVoiceCapture,
            onStopVoiceCapture = onStopVoiceCapture,
            onEnterWaveMode = onEnterWaveMode,
            onEnterEditMode = onEnterEditMode,
            onShowDragHintsChange = onShowDragHintsChange,
            onDragOffsetChange = { dragOffset = it },
            onDraggingToCancelChange = { isDraggingToCancel.value = it },
            onDraggingToEditChange = { isDraggingToEdit.value = it },
            userMessage = userMessage,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 拖动提示组件
 */
@Composable
private fun DragHint(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    description: String,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { if (isLeft) -it else it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { if (isLeft) -it else it }),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                // 编辑图标在左
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                DashedLine()
            } else {
                // 取消图标在右
                DashedLine()
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 虚线组件
 */
@Composable
private fun DashedLine() {
    Canvas(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = pathEffect
        )
    }
}

/**
 * 麦克风按钮
 */
@Composable
private fun MicrophoneButton(
    isRecording: Boolean,
    isProcessingSpeech: Boolean,
    isDraggingToCancel: MutableState<Boolean>,
    isDraggingToEdit: MutableState<Boolean>,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: (Boolean) -> Unit,
    onEnterWaveMode: () -> Unit,
    onEnterEditMode: (String) -> Unit,
    onShowDragHintsChange: (Boolean) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDraggingToCancelChange: (Boolean) -> Unit,
    onDraggingToEditChange: (Boolean) -> Unit,
    userMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isRecording || isProcessingSpeech) {
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primary
                        )
                    }
                )
            )
            .clickable(enabled = false, onClick = {})
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onEnterWaveMode()
                    },
                    onLongPress = {
                        onDragOffsetChange(0f)
                        onDraggingToCancelChange(false)
                        onDraggingToEditChange(false)
                        onShowDragHintsChange(true)
                        onStartVoiceCapture()
                    }
                )
            }
            .pointerInput(isRecording) {
                // 仅在录音时追踪拖动和释放
                if (!isRecording) return@pointerInput
                
                awaitPointerEventScope {
                    var previousPosition: Offset? = null
                    var currentOffset = 0f
                    
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull()
                        
                        if (change == null) break
                        
                        // 检查是否手指抬起
                        if (!change.pressed) {
                            // 释放时的 处理
                            onShowDragHintsChange(false)
                            when {
                                isDraggingToCancel.value -> {
                                    onStopVoiceCapture(true)
                                }
                                isDraggingToEdit.value -> {
                                    onEnterEditMode(userMessage)
                                }
                                else -> {
                                    onStopVoiceCapture(false)
                                }
                            }
                            break
                        }
                        
                        val position = change.position
                        
                        if (previousPosition == null) {
                            previousPosition = position
                        } else {
                            // 计算拖动偏移
                            val horizontalDrag = position.x - previousPosition.x
                            currentOffset += horizontalDrag
                            onDragOffsetChange(currentOffset)

                            val dragThreshold = 60f
                            when {
                                currentOffset > dragThreshold -> {
                                    onDraggingToCancelChange(true)
                                    onDraggingToEditChange(false)
                                }
                                currentOffset < -dragThreshold -> {
                                    onDraggingToEditChange(true)
                                    onDraggingToCancelChange(false)
                                }
                                else -> {
                                    onDraggingToCancelChange(false)
                                    onDraggingToEditChange(false)
                                }
                            }
                            
                            previousPosition = position
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 图标显示
        when {
            isRecording && isDraggingToCancel.value -> {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.floating_cancel_recording),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            isRecording && isDraggingToEdit.value -> {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.floating_edit_recording),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = stringResource(R.string.floating_hold_to_speak),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

