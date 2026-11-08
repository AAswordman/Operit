package com.ai.assistance.operit.integrations.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import androidx.glance.appwidget.updateAll
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopGlanceWidget
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetConfigActivity

object ToolPkgDesktopWidgetHost {
    const val EXTRA_OPEN_ROUTE_ID = "com.ai.assistance.operit.extra.OPEN_ROUTE_ID"
    const val EXTRA_OPEN_ROUTE_ARGS_JSON = "com.ai.assistance.operit.extra.OPEN_ROUTE_ARGS_JSON"

    private const val PREFS_NAME = "toolpkg_desktop_widget_host"
    private const val KEY_PREFIX_SELECTION = "selection:"
    private const val KEY_PREFIX_ROUTE = "route:"
    private const val KEY_PREFIX_RENDER_ROUTE = "render_route:"
    private const val KEY_PREFIX_CONTAINER = "container:"
    private const val KEY_PREFIX_WIDGET_ID = "widget_id:"
    private const val KEY_PREFIX_TITLE = "title:"
    private const val KEY_PREFIX_SUBTITLE = "subtitle:"
    private const val KEY_PREFIX_DESCRIPTION = "description:"
    private const val KEY_PREFIX_ICON = "icon:"
    private const val KEY_PREFIX_ORDER = "order:"

    data class WidgetSelection(
        val key: String,
        val widget: PackageManager.ToolPkgDesktopWidget
    )

    fun buildSelectionKey(containerPackageName: String, widgetId: String): String {
        return "${containerPackageName.trim()}:${widgetId.trim()}"
    }

    fun listAvailableWidgets(context: Context): List<PackageManager.ToolPkgDesktopWidget> {
        val packageManager = PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        return packageManager.getToolPkgDesktopWidgets(resolveContext = context)
    }

    fun resolveSelection(
        context: Context,
        appWidgetId: Int
    ): WidgetSelection? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return null
        }
        val persisted = loadPersistedSelection(context, appWidgetId) ?: return null
        val current =
            listAvailableWidgets(context).firstOrNull { candidate ->
                buildSelectionKey(candidate.containerPackageName, candidate.widgetId) == persisted.key
            }
        return WidgetSelection(
            key = persisted.key,
            widget = current ?: persisted.widget
        )
    }

    fun saveSelection(
        context: Context,
        appWidgetId: Int,
        widget: PackageManager.ToolPkgDesktopWidget
    ): Boolean {
        val selectionKey = buildSelectionKey(widget.containerPackageName, widget.widgetId)
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(selectionPrefKey(appWidgetId), selectionKey)
            .putString(routePrefKey(appWidgetId), widget.routeId)
            .putString(renderRoutePrefKey(appWidgetId), widget.renderRouteId)
            .putString(containerPrefKey(appWidgetId), widget.containerPackageName)
            .putString(widgetIdPrefKey(appWidgetId), widget.widgetId)
            .putString(titlePrefKey(appWidgetId), widget.title)
            .putString(subtitlePrefKey(appWidgetId), widget.subtitle)
            .putString(descriptionPrefKey(appWidgetId), widget.description)
            .putString(iconPrefKey(appWidgetId), widget.icon)
            .putInt(orderPrefKey(appWidgetId), widget.order)
            .commit()
    }

    fun clearSelection(context: Context, appWidgetId: Int) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(selectionPrefKey(appWidgetId))
            .remove(routePrefKey(appWidgetId))
            .remove(renderRoutePrefKey(appWidgetId))
            .remove(containerPrefKey(appWidgetId))
            .remove(widgetIdPrefKey(appWidgetId))
            .remove(titlePrefKey(appWidgetId))
            .remove(subtitlePrefKey(appWidgetId))
            .remove(descriptionPrefKey(appWidgetId))
            .remove(iconPrefKey(appWidgetId))
            .remove(orderPrefKey(appWidgetId))
            .apply()
    }

    fun buildLaunchIntent(
        context: Context,
        routeId: String,
        routeArgsJson: String? = null
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ROUTE_ID, routeId)
            routeArgsJson?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_OPEN_ROUTE_ARGS_JSON, it) }
        }
    }

    fun buildConfigIntent(context: Context, appWidgetId: Int): Intent {
        return Intent(context, ToolPkgDesktopWidgetConfigActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    fun refreshAll(context: Context) {
        runBlocking {
            ToolPkgDesktopGlanceWidget().updateAll(context)
        }
    }

    private fun loadSelectionKey(context: Context, appWidgetId: Int): String? {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(selectionPrefKey(appWidgetId), null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun loadPersistedSelection(
        context: Context,
        appWidgetId: Int
    ): WidgetSelection? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectionKey = loadSelectionKey(context, appWidgetId) ?: return null
        val routeId = prefs.getString(routePrefKey(appWidgetId), null)?.trim().orEmpty()
        val renderRouteId =
            prefs.getString(renderRoutePrefKey(appWidgetId), null)?.trim().orEmpty().ifBlank {
                routeId
            }
        val containerPackageName =
            prefs.getString(containerPrefKey(appWidgetId), null)?.trim().orEmpty()
        val widgetId = prefs.getString(widgetIdPrefKey(appWidgetId), null)?.trim().orEmpty()
        if (routeId.isBlank() || renderRouteId.isBlank() || containerPackageName.isBlank() || widgetId.isBlank()) {
            return null
        }
        return WidgetSelection(
            key = selectionKey,
            widget =
                PackageManager.ToolPkgDesktopWidget(
                    containerPackageName = containerPackageName,
                    toolPkgId = containerPackageName,
                    widgetId = widgetId,
                    routeId = routeId,
                    renderRouteId = renderRouteId,
                    title = prefs.getString(titlePrefKey(appWidgetId), null)?.trim().orEmpty(),
                    subtitle = prefs.getString(subtitlePrefKey(appWidgetId), null)?.trim().orEmpty(),
                    description = prefs.getString(descriptionPrefKey(appWidgetId), null)?.trim().orEmpty(),
                    icon = prefs.getString(iconPrefKey(appWidgetId), null)?.trim()?.ifBlank { null },
                    order = prefs.getInt(orderPrefKey(appWidgetId), 0)
                )
        )
    }

    private fun selectionPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_SELECTION$appWidgetId"
    private fun routePrefKey(appWidgetId: Int): String = "$KEY_PREFIX_ROUTE$appWidgetId"
    private fun renderRoutePrefKey(appWidgetId: Int): String = "$KEY_PREFIX_RENDER_ROUTE$appWidgetId"
    private fun containerPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_CONTAINER$appWidgetId"
    private fun widgetIdPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_WIDGET_ID$appWidgetId"
    private fun titlePrefKey(appWidgetId: Int): String = "$KEY_PREFIX_TITLE$appWidgetId"
    private fun subtitlePrefKey(appWidgetId: Int): String = "$KEY_PREFIX_SUBTITLE$appWidgetId"
    private fun descriptionPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_DESCRIPTION$appWidgetId"
    private fun iconPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_ICON$appWidgetId"
    private fun orderPrefKey(appWidgetId: Int): String = "$KEY_PREFIX_ORDER$appWidgetId"
}
