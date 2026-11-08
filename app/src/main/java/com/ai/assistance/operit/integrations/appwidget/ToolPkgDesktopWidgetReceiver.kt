package com.ai.assistance.operit.integrations.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetHost

class ToolPkgDesktopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = ToolPkgDesktopGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ToolPkgDesktopWidgetHost.clearSelection(context, appWidgetId)
            }
        }
    }
}
