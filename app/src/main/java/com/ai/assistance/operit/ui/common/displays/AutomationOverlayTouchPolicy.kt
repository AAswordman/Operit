package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build

/** Keep three same-UID pass-through windows below Android 12's combined obscuring limit. */
internal fun automationPassthroughAlpha(context: Context): Float {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 1f
    val manager = requireNotNull(context.getSystemService(InputManager::class.java))
    val limit = manager.maximumObscuringOpacityForTouch
    return ((1.0 - Math.cbrt(1.0 - limit.toDouble())) * 0.98).toFloat()
}
