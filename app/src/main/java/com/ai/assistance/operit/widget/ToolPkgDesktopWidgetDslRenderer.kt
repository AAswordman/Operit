package com.ai.assistance.operit.widget

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.ui.common.composedsl.normalizeToken
import com.ai.assistance.operit.ui.theme.NativeThemeGlanceColor
import com.ai.assistance.operit.ui.theme.NativeThemeGlancePaletteV1
import com.ai.assistance.operit.util.AppLogger

private const val WIDGET_DSL_TAG = "ToolPkgDesktopWidgetDsl"

internal data class ToolPkgDesktopWidgetRenderData(
    val renderRouteId: String,
    val renderResult: ToolPkgComposeDslRenderResult?,
    val errorMessage: String? = null
)

internal fun loadToolPkgDesktopWidgetRenderData(
    context: Context,
    appWidgetId: Int,
    selection: ToolPkgDesktopWidgetHost.WidgetSelection
): ToolPkgDesktopWidgetRenderData {
    val packageManager = PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    val renderRoute =
        packageManager
            .getToolPkgUiRoutes(resolveContext = context)
            .firstOrNull { route ->
                route.containerPackageName.equals(
                    selection.widget.containerPackageName,
                    ignoreCase = true
                ) &&
                    route.routeId.equals(selection.widget.renderRouteId, ignoreCase = true)
            }
            ?: return ToolPkgDesktopWidgetRenderData(
                renderRouteId = selection.widget.renderRouteId,
                renderResult = null,
                errorMessage = "widget render route not found: ${selection.widget.renderRouteId}"
            )

    val script =
        packageManager.getToolPkgComposeDslScript(
            containerPackageName = renderRoute.containerPackageName,
            uiModuleId = renderRoute.uiModuleId
        )
            ?: return ToolPkgDesktopWidgetRenderData(
                renderRouteId = selection.widget.renderRouteId,
                renderResult = null,
                errorMessage = "widget render script not found: ${renderRoute.uiModuleId}"
            )

    val screenPath =
        packageManager.getToolPkgComposeDslScreenPath(
            containerPackageName = renderRoute.containerPackageName,
            uiModuleId = renderRoute.uiModuleId
        ).orEmpty()

    val executionContextKey =
        "toolpkg_widget:${appWidgetId}:${renderRoute.containerPackageName}:${renderRoute.uiModuleId}"
    val jsEngine =
        packageManager.acquireToolPkgExecutionEngine(
            contextKey = executionContextKey,
            containerPackageName = renderRoute.containerPackageName
        )
    return try {
        val runtimeOptions =
            mapOf(
                "packageName" to renderRoute.containerPackageName,
                "toolPkgId" to renderRoute.toolPkgId,
                "uiModuleId" to renderRoute.uiModuleId,
                "__operit_ui_package_name" to renderRoute.containerPackageName,
                "__operit_ui_toolpkg_id" to renderRoute.toolPkgId,
                "__operit_ui_module_id" to renderRoute.uiModuleId,
                "__operit_script_screen" to screenPath,
                "moduleSpec" to renderRoute.moduleSpec,
                "state" to emptyMap<String, Any?>(),
                "memo" to emptyMap<String, Any?>()
            )
        val initialRaw = jsEngine.executeComposeDslScript(script = script, runtimeOptions = runtimeOptions)
        val initialParsed = ToolPkgComposeDslParser.parseRenderResult(initialRaw)
        if (initialParsed == null) {
            return ToolPkgDesktopWidgetRenderData(
                renderRouteId = selection.widget.renderRouteId,
                renderResult = null,
                errorMessage = initialRaw?.toString()?.trim().orEmpty().ifBlank { "invalid widget render result" }
            )
        }

        val onLoadActionId =
            ToolPkgComposeDslParser.extractActionId(initialParsed.tree.props["onLoad"])
        if (onLoadActionId.isNullOrBlank()) {
            return ToolPkgDesktopWidgetRenderData(
                renderRouteId = selection.widget.renderRouteId,
                renderResult = initialParsed
            )
        }

        val finalRaw =
            jsEngine.executeComposeDslAction(
                actionId = onLoadActionId,
                runtimeOptions = runtimeOptions
            )
        val finalParsed = ToolPkgComposeDslParser.parseRenderResult(finalRaw)
        ToolPkgDesktopWidgetRenderData(
            renderRouteId = selection.widget.renderRouteId,
            renderResult = finalParsed ?: initialParsed,
            errorMessage = null
        )
    } catch (error: Exception) {
        AppLogger.e(
            WIDGET_DSL_TAG,
            "Failed to render desktop widget DSL: route=${selection.widget.renderRouteId}",
            error
        )
        ToolPkgDesktopWidgetRenderData(
            renderRouteId = selection.widget.renderRouteId,
            renderResult = null,
            errorMessage = error.message ?: error.javaClass.simpleName
        )
    } finally {
        packageManager.releaseToolPkgExecutionEngine(executionContextKey, jsEngine)
    }
}

@Composable
internal fun RenderToolPkgDesktopWidgetDsl(
    node: ToolPkgComposeDslNode,
    routeClickAction: Action,
    themePalette: NativeThemeGlancePaletteV1,
) {
    renderToolPkgDesktopWidgetDslNode(
        node = node,
        routeClickAction = routeClickAction,
        themePalette = themePalette,
        defaultClickable = true,
    )
}

@Composable
private fun renderToolPkgDesktopWidgetDslNode(
    node: ToolPkgComposeDslNode,
    routeClickAction: Action,
    themePalette: NativeThemeGlancePaletteV1,
    defaultClickable: Boolean = false,
) {
    when (normalizeToken(node.type)) {
        "column", "lazycolumn", "scaffold", "surface" -> {
            Column(
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
            ) {
                renderToolPkgDesktopWidgetDslChildren(node, routeClickAction, themePalette)
            }
        }
        "row" -> {
            Row(
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
            ) {
                renderToolPkgDesktopWidgetDslChildren(node, routeClickAction, themePalette)
            }
        }
        "box", "card", "elevatedcard", "outlinedcard" -> {
            Box(
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
            ) {
                renderToolPkgDesktopWidgetDslChildren(node, routeClickAction, themePalette)
            }
        }
        "text" -> {
            Text(
                text = extractNodeText(node),
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
                style =
                    TextStyle(
                        color = parseTextColor(node.props["color"], themePalette),
                        fontSize = (node.props["fontSize"] as? Number)?.toFloat()?.sp,
                        fontWeight = parseFontWeight(node.props["fontWeight"]?.toString())
                    )
            )
        }
        "button", "textbutton", "outlinedbutton", "filledtonalbutton", "elevatedbutton" -> {
            Box(
                modifier =
                    widgetModifierFromNode(
                        node = node,
                        routeClickAction = routeClickAction,
                        themePalette = themePalette,
                        clickable = true,
                    )
                        .background(themePalette.primary.toColorProvider())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = extractNodeText(node),
                    style =
                        TextStyle(
                            color = themePalette.onPrimary.toColorProvider(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                )
            }
        }
        "spacer" -> {
            Spacer(
                modifier =
                    widgetModifierFromNode(
                        node = node,
                        routeClickAction = routeClickAction,
                        themePalette = themePalette,
                        defaultClickable = false,
                    ),
            )
        }
        "linearprogressindicator" -> {
            val progress =
                (node.props["progress"] as? Number)?.toFloat()?.coerceIn(0f, 1f)
                    ?: (node.props["value"] as? Number)?.toFloat()?.coerceIn(0f, 1f)
                    ?: 0f
            Text(
                text = "${(progress * 100).toInt()}%",
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
                style =
                    TextStyle(
                        color = themePalette.primary.toColorProvider(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
            )
        }
        "circularprogressindicator" -> {
            Text(
                text = "Loading",
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
                style =
                    TextStyle(
                        color = parseTextColor(node.props["color"], themePalette),
                        fontSize = 12.sp
                    )
            )
        }
        else -> {
            val flattenedChildren = collectWidgetChildren(node)
            if (flattenedChildren.isEmpty()) {
                return
            }
            Column(
                modifier = widgetModifierFromNode(node, routeClickAction, themePalette, defaultClickable),
            ) {
                flattenedChildren.forEach { child ->
                    renderToolPkgDesktopWidgetDslNode(
                        node = child,
                        routeClickAction = routeClickAction,
                        themePalette = themePalette,
                    )
                }
            }
        }
    }
}

@Composable
private fun renderToolPkgDesktopWidgetDslChildren(
    node: ToolPkgComposeDslNode,
    routeClickAction: Action,
    themePalette: NativeThemeGlancePaletteV1,
) {
    collectWidgetChildren(node).forEach { child ->
        renderToolPkgDesktopWidgetDslNode(
            node = child,
            routeClickAction = routeClickAction,
            themePalette = themePalette,
        )
    }
}

private fun collectWidgetChildren(node: ToolPkgComposeDslNode): List<ToolPkgComposeDslNode> {
    return buildList {
        addAll(node.children)
        node.slots.values.forEach { slotChildren ->
            addAll(slotChildren)
        }
    }
}

private fun extractNodeText(node: ToolPkgComposeDslNode): String {
    node.props["text"]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    val fromChildren =
        collectWidgetChildren(node)
            .joinToString(separator = "") { child ->
                if (normalizeToken(child.type) == "text") {
                    child.props["text"]?.toString().orEmpty()
                } else {
                    ""
                }
            }
            .trim()
    return fromChildren.ifBlank { node.type }
}

private fun widgetModifierFromNode(
    node: ToolPkgComposeDslNode,
    routeClickAction: Action,
    themePalette: NativeThemeGlancePaletteV1,
    defaultClickable: Boolean = false,
    clickable: Boolean = defaultClickable
): GlanceModifier {
    var modifier: GlanceModifier = GlanceModifier
    val props = node.props

    val width = (props["width"] as? Number)?.toFloat()
    if (width != null) {
        modifier = modifier.width(width.dp)
    }

    val height = (props["height"] as? Number)?.toFloat()
    if (height != null) {
        modifier = modifier.height(height.dp)
    }

    if (props["fillMaxSize"] == true) {
        modifier = modifier.fillMaxSize()
    } else if (props["fillMaxWidth"] == true) {
        modifier = modifier.fillMaxWidth()
    }

    modifier = applyWidgetPadding(modifier, props["padding"], props)
    parseColorProvider(
        value = props["backgroundColor"] ?: props["containerColor"] ?: props["background"],
        themePalette = themePalette,
    )
        ?.let { modifier = modifier.background(it) }

    val hasExplicitClick =
        ToolPkgComposeDslParser.extractActionId(props["onClick"]) != null ||
            hasClickableModifierOp(props["modifier"])
    if (clickable || hasExplicitClick) {
        modifier = modifier.clickable(routeClickAction)
    }
    return modifier
}

private fun applyWidgetPadding(
    base: GlanceModifier,
    rawPadding: Any?,
    props: Map<String, Any?>
): GlanceModifier {
    if (rawPadding is Number) {
        return base.padding(rawPadding.toFloat().dp)
    }
    if (rawPadding is Map<*, *>) {
        val horizontal = (rawPadding["horizontal"] as? Number)?.toFloat() ?: 0f
        val vertical = (rawPadding["vertical"] as? Number)?.toFloat() ?: 0f
        return base.padding(horizontal = horizontal.dp, vertical = vertical.dp)
    }

    val horizontal = (props["paddingHorizontal"] as? Number)?.toFloat()
    val vertical = (props["paddingVertical"] as? Number)?.toFloat()
    return if (horizontal != null || vertical != null) {
        base.padding(horizontal = (horizontal ?: 0f).dp, vertical = (vertical ?: 0f).dp)
    } else {
        base
    }
}

private fun hasClickableModifierOp(rawModifier: Any?): Boolean {
    val container = rawModifier as? Map<*, *> ?: return false
    val list = container["__modifierOps"] as? List<*> ?: return false
    return list.any { item ->
        val map = item as? Map<*, *> ?: return@any false
        normalizeToken(map["name"]?.toString().orEmpty()) == "clickable"
    }
}

private fun parseTextColor(
    value: Any?,
    themePalette: NativeThemeGlancePaletteV1,
): ColorProvider {
    return parseColorProvider(value, themePalette) ?: themePalette.onSurface.toColorProvider()
}

private fun parseColorProvider(
    value: Any?,
    themePalette: NativeThemeGlancePaletteV1,
): ColorProvider? {
    return when (value) {
        is Number -> ColorProvider(Color(value.toLong().toULong()))
        is Map<*, *> ->
            resolveToolPkgDesktopWidgetColorToken(
                token = value["__colorToken"]?.toString().orEmpty(),
                themePalette = themePalette,
            )
                ?.toColorProvider()

        is String -> parseColorString(value, themePalette)
        else -> null
    }
}

internal fun resolveToolPkgDesktopWidgetColorToken(
    token: String,
    themePalette: NativeThemeGlancePaletteV1,
): NativeThemeGlanceColor? = themePalette.colorForToken(token)

private fun parseColorString(
    raw: String,
    themePalette: NativeThemeGlancePaletteV1,
): ColorProvider? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    resolveToolPkgDesktopWidgetColorToken(trimmed, themePalette)
        ?.let { return it.toColorProvider() }
    return try {
        ColorProvider(Color(AndroidColor.parseColor(trimmed)))
    } catch (_: Exception) {
        null
    }
}

private fun parseFontWeight(raw: String?): FontWeight? {
    return when (normalizeToken(raw.orEmpty())) {
        "bold", "semibold", "medium" -> FontWeight.Bold
        else -> null
    }
}
