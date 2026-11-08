package com.ai.assistance.operit.ui.theme.transition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** 只有侧栏按钮主动调用，设置页和系统主题变化不创建转场。 */
class ThemeRevealController internal constructor(
    private val switchTheme: (Offset, suspend () -> Unit) -> Unit,
) {
    fun revealFrom(origin: Offset, changeTheme: suspend () -> Unit) = switchTheme(origin, changeTheme)
}

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController> {
    error("主题扩散宿主尚未安装")
}

private const val MAX_SNAPSHOT_SIDE = 1024
private data class ThemeRevealSnapshot(val image: ImageBitmap, val origin: Offset)
private class ThemeRevealCapture(val layer: GraphicsLayer) {
    val recorded = CompletableDeferred<Unit>()
}

@Composable
fun ThemeRevealHost(darkTheme: Boolean, content: @Composable () -> Unit) {
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    val currentDarkTheme by rememberUpdatedState(darkTheme)
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var captureRequest by remember { mutableStateOf<ThemeRevealCapture?>(null) }
    var snapshot by remember { mutableStateOf<ThemeRevealSnapshot?>(null) }
    var running by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val controller = remember(graphicsContext, scope) {
        ThemeRevealController { origin, changeTheme ->
            if (!running) {
                running = true
                scope.launch {
                    try {
                        val previousDarkTheme = currentDarkTheme
                        val layer = graphicsContext.createGraphicsLayer()
                        val capture = ThemeRevealCapture(layer)
                        val image = try {
                            captureRequest = capture
                            withTimeout(2_000) {
                                // 请求一次实际绘制，保证截图来自切换前的画面，而不是空图层或上一轮内容。
                                capture.recorded.await()
                                withContext(Dispatchers.Default) { layer.toImageBitmap() }
                            }
                        } finally {
                            captureRequest = null
                            graphicsContext.releaseGraphicsLayer(layer)
                        }
                        progress.snapTo(0f)
                        snapshot = ThemeRevealSnapshot(image, origin - rootPosition)
                        changeTheme()
                        // 等待主题进入组合；变化超时就释放截图和点击遮罩。
                        withTimeout(2_000) {
                            snapshotFlow { currentDarkTheme }.first { it != previousDarkTheme }
                        }
                        withFrameNanos { }
                        progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                    } catch (error: TimeoutCancellationException) {
                        AppLogger.e("ThemeReveal", "侧栏主题截图或变化超时", error)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e("ThemeReveal", "侧栏主题切换失败", error)
                    } finally {
                        snapshot = null
                        running = false
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalThemeRevealController provides controller) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { rootPosition = it.positionInRoot() }) {
            Box(Modifier.fillMaxSize().drawWithContent {
                val request = captureRequest
                if (request != null && !request.recorded.isCompleted) {
                    // 限制长边和纹理面积，高分辨率设备也不分配全屏大小的截图。
                    val factor = minOf(1f, MAX_SNAPSHOT_SIDE / max(size.width, size.height))
                    val captureSize = IntSize(
                        (size.width * factor).roundToInt().coerceAtLeast(1),
                        (size.height * factor).roundToInt().coerceAtLeast(1),
                    )
                    request.layer.record(size = captureSize) {
                        scale(factor, factor, pivot = Offset.Zero) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    request.recorded.complete(Unit)
                }
                // 日常绘制直接输出；截图完成后也不再改写正由后台读取的图层。
                drawContent()
            }) { content() }
            if (running) {
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                    // 从截图请求起屏蔽操作，避免旧截图上的导航修改当前页面。
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }) {
                    snapshot?.let { captured ->
                        val origin = captured.origin
                        val radius = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * progress.value
                        val oldArea = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius))
                        }
                        // 圆内显示实时界面，圆外将缩小的旧主题截图铺回原尺寸。
                        clipPath(oldArea) {
                            drawImage(
                                captured.image,
                                srcSize = IntSize(captured.image.width, captured.image.height),
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            )
                        }
                    }
                }
            }
        }
    }
}
