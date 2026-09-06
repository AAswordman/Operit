package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Density

const val DEFAULT_MAX_SCROLL_SPEED_DP_PER_SECOND = 800f

/**
 * 根据位移像素与屏幕密度，计算限速滚动所需的动画持续时间（毫秒）。
 * 当内容产生大量下移时，拉长动画时间以将滚动速度限制在 [maxSpeedDpPerSecond] 之下。
 */
internal fun calculateScrollDurationMs(
    deltaPx: Int,
    densityDpiRatio: Float,
    maxSpeedDpPerSecond: Float = DEFAULT_MAX_SCROLL_SPEED_DP_PER_SECOND,
    minDurationMs: Long = 100L,
    maxDurationMs: Long = 1000L
): Int {
    if (deltaPx <= 0 || densityDpiRatio <= 0f || maxSpeedDpPerSecond <= 0f) {
        return minDurationMs.toInt()
    }
    val deltaDp = deltaPx / densityDpiRatio
    val calculatedMs = ((deltaDp / maxSpeedDpPerSecond) * 1000f).toLong()
    return calculatedMs.coerceIn(minDurationMs, maxDurationMs).toInt()
}

/**
 * 带有最大速度限制的平滑滚动。
 * 如果 [enableLimit] 为 true 且目标位置在当前位置下方，
 * 则限制单位时间内的位移速度，避免流式大段输出或突发 chunk 导致页面猛烈滑移。
 */
suspend fun ScrollState.animateScrollToWithSpeedLimit(
    targetValue: Int,
    density: Density,
    enableLimit: Boolean = true,
    maxSpeedDpPerSecond: Float = DEFAULT_MAX_SCROLL_SPEED_DP_PER_SECOND
) {
    val clampedTarget = targetValue.coerceIn(0, maxValue)
    val delta = clampedTarget - value
    if (!enableLimit || delta <= 0) {
        animateScrollTo(clampedTarget)
        return
    }

    val duration = calculateScrollDurationMs(
        deltaPx = delta,
        densityDpiRatio = density.density,
        maxSpeedDpPerSecond = maxSpeedDpPerSecond
    )
    animateScrollTo(
        clampedTarget,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing)
    )
}
