package com.ai.assistance.operit.ui.features.annualreport

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.time.Instant

/** 当前打包的年度报告年份，必须与 annual-report/data/ 里的项目快照一致。 */
object AnnualReportEdition {
    const val YEAR = 2026

    /**
     * 侧边栏入口的解锁时刻：2027-01-03 00:00 UTC。早于这个时刻，侧边栏不出现入口。
     *
     * 取 UTC 的绝对时刻，各地用户在同一时刻解锁。此时最晚进入新年的 UTC-12 也已是 1 月 2 日中午，
     * 用户侧统计的是完整的上一年。换年时和 [YEAR] 一起改。
     * 开发测试入口不受它限制，见 entry/AnnualReportDevEntry.kt。
     */
    val UNLOCK_AT: Instant = Instant.parse("2027-01-03T00:00:00Z")

    /** 以设备的系统时钟判断入口是否已解锁。 */
    fun isUnlocked(now: Instant = Instant.now()): Boolean = !now.isBefore(UNLOCK_AT)
}

/** 年度报告唯一的启动方法，所有入口都只调用它。 */
object AnnualReportLauncher {
    fun launch(context: Context, year: Int, dark: Boolean) {
        val intent =
            Intent(context, AnnualReportActivity::class.java)
                .putExtra(AnnualReportActivity.EXTRA_YEAR, year)
                .putExtra(AnnualReportActivity.EXTRA_THEME, if (dark) AnnualReportActivity.THEME_DARK else AnnualReportActivity.THEME_LIGHT)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
