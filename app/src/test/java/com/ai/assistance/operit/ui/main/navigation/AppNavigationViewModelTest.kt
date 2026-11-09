package com.ai.assistance.operit.ui.main.navigation

import com.ai.assistance.operit.data.api.MarketV2Entry
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenRouteRegistry
import org.junit.Assert.*
import org.junit.Test

class AppNavigationViewModelTest {
    @Test fun activityRecreationRetainsTheSameRouterAndBackStack() {
        val model = AppNavigationViewModel()
        assertFalse(model.attachActivity())
        val first = RouteEntry(routeId = "native.settings")
        val router = model.getOrCreateRouter { first }
        val second = router.navigate("native.workflow_detail", mapOf("workflowId" to "work"), RouteEntrySource.DRAWER)
        assertTrue(model.attachActivity())
        val recreated = model.getOrCreateRouter { error("Must not create a new router") }
        assertSame(router, recreated)
        assertEquals(listOf(first, second), recreated.backStack)
        assertTrue(recreated.canPop)
        assertEquals(first, recreated.pop())
    }

    @Test fun complexScreenArgumentsAreRetainedWithoutSerialization() {
        val model = AppNavigationViewModel()
        val market = MarketV2Entry(id = "entry", title = "title", detail = "markdown", versions = emptyList())
        val screen = Screen.MarketEntryDetail(market, true)
        val route = ScreenRouteRegistry.toEntry(screen)
        model.getOrCreateRouter { route }
        val retained = model.getOrCreateRouter { error("Must retain the screen") }.currentEntry
        assertSame(route, retained)
        assertEquals(screen, ScreenRouteRegistry.screenFromEntry(retained))
    }

    @Test fun recreationKeepsPendingRequestsButDoesNotReplayConsumedOnes() {
        val model = AppNavigationViewModel()
        assertFalse(model.attachActivity())
        model.pendingRouteId = "plugin.pending"
        model.pendingRouteArgs = mapOf("token" to "payload")
        model.pendingRouteRequestId = 1
        model.pendingOpenChatId = "chat"
        model.pendingOpenChatRequestId = 2
        assertTrue(model.attachActivity())
        assertEquals("plugin.pending", model.pendingRouteId)
        assertEquals(mapOf("token" to "payload"), model.pendingRouteArgs)
        assertEquals("chat", model.pendingOpenChatId)
        model.pendingRouteId = null
        model.pendingRouteArgs = emptyMap()
        model.pendingRouteRequestId = 0
        model.pendingOpenChatId = null
        model.pendingOpenChatRequestId = 0
        assertTrue(model.attachActivity())
        assertNull(model.pendingRouteId)
        assertNull(model.pendingOpenChatId)
        // Process death starts a new in-memory navigation session.
        assertFalse(AppNavigationViewModel().attachActivity())
    }
}
