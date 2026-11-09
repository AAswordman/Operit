package com.ai.assistance.operit.ui.main.navigation

import androidx.lifecycle.ViewModel
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.ui.common.NavItem

/** The router belongs to the Activity, not to one setContent invocation. */
class AppNavigationViewModel : ViewModel() {
    private var router: AppRouterState? = null
    private var activityAttached = false
    var pendingSharedFileUris: List<Uri>? = null
    var pendingSharedText: String? = null
    var pendingShortcutNavItem by mutableStateOf<NavItem?>(null)
    var pendingShortcutRequestId by mutableStateOf(0L)
    var currentMainNavItem by mutableStateOf<NavItem>(NavItem.AiChat)
    var pendingRouteId by mutableStateOf<String?>(null)
    var pendingRouteArgs by mutableStateOf<Map<String, Any?>>(emptyMap())
    var pendingRouteRequestId by mutableStateOf(0L)
    var pendingOpenChatId by mutableStateOf<String?>(null)
    var pendingOpenChatRequestId by mutableStateOf(0L)
    var nextChatOpenRequestId = 0L

    /** Process-local state (such as a consumed share) survives configuration recreation. */
    fun attachActivity(): Boolean = activityAttached.also { activityAttached = true }

    fun getOrCreateRouter(initialEntry: () -> RouteEntry): AppRouterState =
        router ?: AppRouterState(initialEntry()).also { router = it }
}
