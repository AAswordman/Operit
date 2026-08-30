package com.ai.assistance.operit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.GlobalPresentationManager
import com.ai.assistance.operit.ui.theme.NativeThemeGlanceHost
import com.ai.assistance.operit.ui.theme.NativeThemeGlancePaletteV1
import kotlinx.coroutines.flow.first

class ToolPkgDesktopGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val presentation =
            GlobalPresentationManager.getInstance(context).snapshotFlow.first()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val selection = ToolPkgDesktopWidgetHost.resolveSelection(context, appWidgetId)
        val renderData =
            selection?.let {
                loadToolPkgDesktopWidgetRenderData(
                    context = context,
                    appWidgetId = appWidgetId,
                    selection = it
                )
            }
        provideContent {
            NativeThemeGlanceHost(
                context = context,
                initialPresentation = presentation,
            ) { themePalette ->
                ToolPkgDesktopWidgetContent(
                    context = context,
                    appWidgetId = appWidgetId,
                    selection = selection,
                    renderData = renderData,
                    themePalette = themePalette,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalGlanceApi::class)
private fun ToolPkgDesktopWidgetContent(
    context: Context,
    appWidgetId: Int,
    selection: ToolPkgDesktopWidgetHost.WidgetSelection?,
    renderData: ToolPkgDesktopWidgetRenderData?,
    themePalette: NativeThemeGlancePaletteV1,
) {
    val clickAction =
        selection?.let {
            actionStartActivity(
                ToolPkgDesktopWidgetHost.buildLaunchIntent(
                    context = context,
                    routeId = it.widget.routeId
                )
            )
        } ?: actionStartActivity(ToolPkgDesktopWidgetHost.buildConfigIntent(context, appWidgetId))

    GlanceTheme {
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(themePalette.surface.toColorProvider())
                    .padding(14.dp)
                    .clickable(clickAction),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selection == null) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.toolpkg_widget_unconfigured_title),
                        style = TextStyle(
                            color = themePalette.onSurface.toColorProvider(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = context.getString(R.string.toolpkg_widget_unconfigured_subtitle),
                        style = TextStyle(
                            color = themePalette.onSurfaceVariant.toColorProvider(),
                            fontSize = 12.sp
                        )
                    )
                }
                return@Box
            }

            val widget = selection.widget
            val renderResult = renderData?.renderResult
            if (renderResult != null) {
                RenderToolPkgDesktopWidgetDsl(
                    node = renderResult.tree,
                    routeClickAction = clickAction,
                    themePalette = themePalette,
                )
                return@Box
            }

            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = widget.title,
                    style = TextStyle(
                        color = themePalette.onSurface.toColorProvider(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (!renderData?.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = renderData?.errorMessage.orEmpty(),
                        style = TextStyle(
                            color = themePalette.onSurfaceVariant.toColorProvider(),
                            fontSize = 11.sp
                        )
                    )
                } else if (widget.subtitle.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = widget.subtitle,
                        style = TextStyle(
                            color = themePalette.onSurfaceVariant.toColorProvider(),
                            fontSize = 12.sp
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = context.getString(R.string.toolpkg_widget_open_label),
                    style = TextStyle(
                        color = themePalette.primary.toColorProvider(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                )
            }
        }
    }
}
