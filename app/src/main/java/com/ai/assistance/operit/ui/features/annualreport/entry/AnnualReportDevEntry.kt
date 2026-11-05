package com.ai.assistance.operit.ui.features.annualreport.entry

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ai.assistance.operit.ui.features.annualreport.AnnualReportEdition
import com.ai.assistance.operit.ui.features.annualreport.AnnualReportLauncher

/** 开发测试入口要连续点击的次数。 */
private const val DEV_ENTRY_TAPS = 10

/** 相邻两次点击允许的最大间隔，超过就从头计数。 */
private const val DEV_ENTRY_MAX_GAP_MILLIS = 1_000L

/** 报告页跟随应用当前主题：背景偏暗就用深色。侧边栏入口和开发测试入口共用这一条判断。 */
@Composable
fun isAnnualReportDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * 开发测试入口的状态：连续点击挂了 [annualReportDevEntry] 的元素 [DEV_ENTRY_TAPS] 次，
 * 就直接全屏打开年度报告，同时给一次长按触感。
 *
 * 它不看 [AnnualReportEdition.UNLOCK_AT]，用来在侧边栏入口解锁之前验收和排查。所有构建都带这个入口。
 * 挂载点是关于页顶部的 Operit 图标。
 */
class AnnualReportDevEntry internal constructor(private val onEnter: () -> Unit) {
    private var count = 0
    private var lastTapAt = 0L

    internal fun onTap() {
        // 用单调时钟计间隔：系统时间被用户或网络校时改动时，不会把一串连点误判成超时，
        // 也不会把两串相隔很久的点击凑成一次。
        val now = SystemClock.elapsedRealtime()
        count = if (count > 0 && now - lastTapAt <= DEV_ENTRY_MAX_GAP_MILLIS) count + 1 else 1
        lastTapAt = now
        if (count < DEV_ENTRY_TAPS) return
        count = 0
        onEnter()
    }
}

@Composable
fun rememberAnnualReportDevEntry(): AnnualReportDevEntry {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // 深浅色会在使用中切换，入口只创建一次，所以读取最新值而不是创建时的值。
    val dark = rememberUpdatedState(isAnnualReportDark())
    return remember(context, haptics) {
        AnnualReportDevEntry {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            AnnualReportLauncher.launch(context, AnnualReportEdition.YEAR, dark.value)
        }
    }
}

/**
 * 用 pointerInput 而不是 clickable：不产生点击语义，读屏软件不会把这个元素读成可点击控件，
 * 也没有水波纹。用 clickable 的话，这个本该隐藏的入口会被主动提示出来。
 * 不做成返回 Modifier 的 @Composable 函数，那样会触发 Compose 的 ComposableModifierFactory 检查。
 */
fun Modifier.annualReportDevEntry(entry: AnnualReportDevEntry): Modifier =
    pointerInput(entry) { detectTapGestures(onTap = { entry.onTap() }) }
