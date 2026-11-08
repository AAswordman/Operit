package com.ai.assistance.operit.ui.main.navigation

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import com.ai.assistance.operit.core.tools.runtime.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.packTool.metadata.TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS
import com.ai.assistance.operit.ui.common.icons.MaterialIconNameResolver
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import com.ai.assistance.operit.ui.common.NavItem

object AppRouteCatalog {
    fun build(context: Context): AppNavigationModel {
        val packageManager =
            PackageManager.getInstance(context, AIToolHandler.getInstance(context))
        val toolPkgRoutes =
            packageManager.getToolPkgUiRoutes(resolveContext = context).map { route ->
                RouteSpec(
                    routeId = route.routeId,
                    runtime = RouteRuntime.TOOLPKG_COMPOSE_DSL,
                    title = route.title,
                    icon = Icons.Default.Extension,
                    ownerPackageName = route.containerPackageName,
                    toolPkgUiModuleId = route.uiModuleId,
                    keepAlive = route.keepAlive
                )
            }
        val toolPkgNavigationEntries =
            packageManager.getToolPkgNavigationEntries(resolveContext = context).mapNotNull { entry ->
                val surface =
                    when (entry.surface.trim().lowercase()) {
                        TOOLPKG_NAV_SURFACE_MAIN_SIDEBAR_PLUGINS ->
                            NavigationSurface.MAIN_SIDEBAR_PLUGINS
                        else -> null
                    } ?: return@mapNotNull null
                NavigationEntrySpec(
                    entryId = "toolpkg:${entry.containerPackageName}:${entry.entryId}",
                    routeId = entry.routeId,
                    surface = surface,
                    title = entry.title,
                    description = entry.description,
                    icon = MaterialIconNameResolver.resolveOrDefault(entry.icon, Icons.Default.Extension),
                    order = entry.order,
                    action =
                        entry.action?.let { action ->
                            NavigationEntryActionSpec(
                                functionName = action.functionName,
                                functionSource = action.functionSource
                            )
                        },
                    kind = NavigationEntryKind.PLUGIN,
                    ownerPackageName = entry.containerPackageName
                )
            }

        return AppNavigationModel(
            routes = ScreenRouteRegistry.hostRouteSpecs(context) + toolPkgRoutes,
            navigationEntries =
                (
                    ScreenRouteRegistry.mainSidebarEntries(context) +
                        toolPkgNavigationEntries
                    )
                    .sortedWith(
                        compareBy<NavigationEntrySpec>({ it.surface.ordinal }, { it.order }, { it.title })
                    )
        )
    }

    fun resolveScreen(model: AppNavigationModel, entry: RouteEntry): Screen? {
        ScreenRouteRegistry.screenFromEntry(entry)?.let { return it }

        val spec = model.routesById[entry.routeId] ?: return null
        if (spec.runtime != RouteRuntime.TOOLPKG_COMPOSE_DSL) {
            return null
        }
        val containerPackageName = spec.ownerPackageName ?: return null
        val uiModuleId = spec.toolPkgUiModuleId ?: return null
        return Screen.ToolPkgComposeDsl(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId,
            title = spec.title ?: uiModuleId,
            keepAlive = spec.keepAlive
        )
    }

    fun initialEntry(navItem: NavItem): RouteEntry {
        return ScreenRouteRegistry.initialEntry(navItem)
    }

    fun toEntry(
        screen: Screen,
        source: RouteEntrySource = RouteEntrySource.DEFAULT
    ): RouteEntry {
        return ScreenRouteRegistry.toEntry(screen = screen, source = source)
    }
}
