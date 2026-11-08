package com.ai.assistance.operit.ui.features.management.settings.screens.theme.studio

import java.math.BigDecimal
import java.util.Locale

/** 常规小数保持可读位数；极细的正负值不能被显示为零，原始数值不在这里修改。 */
internal fun formatStudioNumber(value: Float, precision: Int): String {
    val displayed = String.format(Locale.ROOT, "%.${precision}f", value)
    return if (precision > 0 && value != 0f && displayed.toFloat() == 0f) {
        BigDecimal(value.toString()).toPlainString()
    } else displayed
}
