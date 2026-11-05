package com.ai.assistance.operit.ui.features.annualreport

import android.app.Activity
import android.content.Context
import android.content.Intent

/** 当前打包的年度报告年份，必须与 annual-report/data/ 里的项目快照一致。 */
object AnnualReportEdition {
    const val YEAR = 2026
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
