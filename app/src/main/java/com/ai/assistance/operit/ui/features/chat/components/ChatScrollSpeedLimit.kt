package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Density
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager

/**
 * 表示“不限速”的速度值：滚动速度完全跟随模型吞吐量。
 */
const val UNLIMITED_SCROLL_SPEED = DisplayPreferencesManager.STREAM_SCROLL_SPEED_UNLIMITED

/**
 * 根据位移像素与屏幕密度，计算限速滚动所需的动画持续时间（毫秒）。
 * 当内容产生大量下移时，拉长动画时间以将滚动速度限制在 [maxSpeedDpPerSecond] 之下。
 */
internal fun calculateScrollDurationMs(
    deltaPx: Int,
    densityDpiRatio: Float,
    maxSpeedDpPerSecond: Float,
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
 *
 * [maxSpeedDpPerSecond] 是速度上限而非固定速度：模型输出较慢时不会介入，
 * 只有在单次位移会导致滚动超速时才拉长动画时长削峰。
 * 传入 [UNLIMITED_SCROLL_SPEED] 时不做任何限制，滚动速度完全跟随模型吞吐量。
 */
suspend fun ScrollState.animateScrollToWithSpeedLimit(
    targetValue: Int,
    density: Density,
    maxSpeedDpPerSecond: Int
) {
    val clampedTarget = targetValue.coerceIn(0, maxValue)
    val delta = clampedTarget - value
    if (maxSpeedDpPerSecond == UNLIMITED_SCROLL_SPEED || delta <= 0) {
        animateScrollTo(clampedTarget)
        return
    }

    val duration = calculateScrollDurationMs(
        deltaPx = delta,
        densityDpiRatio = density.density,
        maxSpeedDpPerSecond = maxSpeedDpPerSecond.toFloat()
    )
    animateScrollTo(
        clampedTarget,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing)
    )
}
