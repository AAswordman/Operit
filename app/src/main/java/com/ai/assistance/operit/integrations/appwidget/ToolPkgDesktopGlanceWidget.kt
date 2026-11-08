package com.ai.assistance.operit.integrations.appwidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.color.ColorProvider
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
import com.ai.assistance.operit.integrations.appwidget.RenderToolPkgDesktopWidgetDsl
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetHost
import com.ai.assistance.operit.integrations.appwidget.ToolPkgDesktopWidgetRenderData
import com.ai.assistance.operit.integrations.appwidget.loadToolPkgDesktopWidgetRenderData

class ToolPkgDesktopGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
            ToolPkgDesktopWidgetContent(
                context = context,
                appWidgetId = appWidgetId,
                selection = selection,
                renderData = renderData
            )
        }
    }
}

@Composable
@OptIn(ExperimentalGlanceApi::class)
private fun ToolPkgDesktopWidgetContent(
    context: Context,
    appWidgetId: Int,
    selection: ToolPkgDesktopWidgetHost.WidgetSelection?,
    renderData: ToolPkgDesktopWidgetRenderData?
) {
    val background =
        ColorProvider(
            day = Color(0xFFF6F4EE),
            night = Color(0xFF1F242B)
        )
    val titleColor =
        ColorProvider(
            day = Color(0xFF1E2A35),
            night = Color(0xFFF4F6F8)
        )
    val bodyColor =
        ColorProvider(
            day = Color(0xFF52606D),
            night = Color(0xFFD0D7DE)
        )
    val accentColor =
        ColorProvider(
            day = Color(0xFF1E88E5),
            night = Color(0xFF64B5F6)
        )

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
                    .background(background)
                    .padding(14.dp)
                    .clickable(clickAction),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selection == null) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.toolpkg_widget_unconfigured_title),
                        style = TextStyle(
                            color = titleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = context.getString(R.string.toolpkg_widget_unconfigured_subtitle),
                        style = TextStyle(
                            color = bodyColor,
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
                    routeClickAction = clickAction
                )
                return@Box
            }

            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = widget.title,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (!renderData?.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = renderData?.errorMessage.orEmpty(),
                        style = TextStyle(
                            color = bodyColor,
                            fontSize = 11.sp
                        )
                    )
                } else if (widget.subtitle.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = widget.subtitle,
                        style = TextStyle(
                            color = bodyColor,
                            fontSize = 12.sp
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(
                    text = context.getString(R.string.toolpkg_widget_open_label),
                    style = TextStyle(
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
