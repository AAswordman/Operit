package com.ai.assistance.operit.util.logging

import java.time.LocalDate

/** 仅当天的关闭操作生效，过期日期无需访问磁盘即可恢复默认开启。 */
internal class DailyLogWritePolicy(
    private val today: () -> String = { LocalDate.now().toString() }
) {
    var disabledDate: String? = null
        private set

    fun restore(savedDisabledDate: String?) {
        disabledDate = savedDisabledDate
        isEnabled()
    }

    fun isEnabled(): Boolean {
        val date = disabledDate ?: return true
        if (date == today()) return false
        disabledDate = null
        return true
    }

    fun setEnabled(enabled: Boolean) {
        disabledDate = if (enabled) null else today()
    }
}
