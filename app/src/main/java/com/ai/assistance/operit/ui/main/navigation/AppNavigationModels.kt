package com.ai.assistance.operit.ui.main.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

enum class RouteRuntime {
    NATIVE,
    TOOLPKG_COMPOSE_DSL
}

enum class NavigationSurface {
    MAIN_SIDEBAR_AI,
    MAIN_SIDEBAR_TOOLS,
    MAIN_SIDEBAR_PLUGINS,
    MAIN_SIDEBAR_SYSTEM,
}

enum class NavigationEntryKind {
    HOST,
    PLUGIN
}

@Immutable
data class NavigationEntryActionSpec(
    val functionName: String,
    val functionSource: String? = null
)

enum class RouteEntrySource {
    DEFAULT,
    DRAWER,
    SCRIPT
}

@Immutable
data class RouteSpec(
    val routeId: String,
    val runtime: RouteRuntime,
    val title: String? = null,
    val icon: ImageVector? = null,
    val ownerPackageName: String? = null,
    val toolPkgUiModuleId: String? = null,
    val keepAlive: Boolean = false,
    val reuseOnTop: Boolean = true
)

@Immutable
data class RouteEntry(
    val instanceId: String = UUID.randomUUID().toString(),
    val routeId: String,
    val args: Map<String, Any?> = emptyMap(),
    val source: RouteEntrySource = RouteEntrySource.DEFAULT
)

@Immutable
data class NavigationEntrySpec(
    val entryId: String,
    val routeId: String,
    val surface: NavigationSurface,
    val title: String,
    val description: String? = null,
    val icon: ImageVector,
    val order: Int = 0,
    val routeArgs: Map<String, Any?> = emptyMap(),
    val action: NavigationEntryActionSpec? = null,
    val kind: NavigationEntryKind = NavigationEntryKind.HOST,
    val ownerPackageName: String? = null
)

@Immutable
data class AppNavigationModel(
    val routes: List<RouteSpec>,
    val navigationEntries: List<NavigationEntrySpec>
) {
    val routesById: Map<String, RouteSpec> = routes.associateBy(RouteSpec::routeId)
    val navigationEntriesById: Map<String, NavigationEntrySpec> =
        navigationEntries.associateBy(NavigationEntrySpec::entryId)
}

@Stable
class AppRouterState(initialEntry: RouteEntry) {
    private val stack = mutableStateListOf(initialEntry)
    var currentEntry by mutableStateOf(initialEntry)
        private set

    val backStack: List<RouteEntry>
        get() = stack

    val canPop: Boolean
        get() = stack.size > 1

    fun navigate(
        routeId: String,
        args: Map<String, Any?> = emptyMap(),
        source: RouteEntrySource = RouteEntrySource.DEFAULT,
        routeSpec: RouteSpec? = null
    ): RouteEntry {
        val current = currentEntry
        if (
            routeSpec?.reuseOnTop != false &&
                current.routeId == routeId &&
                current.args == args
        ) {
            return current
        }
        val nextEntry = RouteEntry(routeId = routeId, args = args, source = source)
        stack.add(nextEntry)
        currentEntry = nextEntry
        return nextEntry
    }

    fun resetTo(entry: RouteEntry) {
        stack.clear()
        stack.add(entry)
        currentEntry = entry
    }

    fun pop(): RouteEntry? {
        if (!canPop) {
            return null
        }
        stack.removeAt(stack.lastIndex)
        currentEntry = stack.last()
        return currentEntry
    }
}

object AppRouterGateway {
    @Volatile
    private var navigateHandler: ((String, Map<String, Any?>, RouteEntrySource) -> Unit)? = null
    @Volatile
    private var resetHandler: ((String, Map<String, Any?>, RouteEntrySource) -> Unit)? = null

    fun install(
        handler: (String, Map<String, Any?>, RouteEntrySource) -> Unit,
        reset: (String, Map<String, Any?>, RouteEntrySource) -> Unit
    ) {
        navigateHandler = handler
        resetHandler = reset
    }

    fun clear() {
        navigateHandler = null
        resetHandler = null
    }

    fun navigate(
        routeId: String,
        args: Map<String, Any?> = emptyMap(),
        source: RouteEntrySource = RouteEntrySource.SCRIPT
    ) {
        navigateHandler?.invoke(routeId, args, source)
    }

    fun resetTo(
        routeId: String,
        args: Map<String, Any?> = emptyMap(),
        source: RouteEntrySource = RouteEntrySource.SCRIPT
    ) {
        resetHandler?.invoke(routeId, args, source)
    }
}

object AppRouteDiscoveryGateway {
    @Volatile
    private var routesProvider: (() -> List<RouteSpec>)? = null

    fun install(provider: () -> List<RouteSpec>) {
        routesProvider = provider
    }

    fun clear() {
        routesProvider = null
    }

    fun listRoutes(): List<RouteSpec> {
        return routesProvider?.invoke().orEmpty()
    }
}
