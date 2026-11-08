package com.ai.assistance.operit.ui.main.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageDetailRouteTest {
    @Test
    fun detailCanBeReconstructedFromPersistedArguments() {
        val screen = Screen.PackageDetail("com.example.tools", "工具 · Tools")
        val routeId = ScreenRouteRegistry.routeIdOf(screen)
        val rebuilt = ScreenRouteRegistry.buildScreen(routeId, mapOf(
            "packageName" to "com.example.tools", "title" to "工具 · Tools",
        ))
        assertEquals(screen, rebuilt)
        assertEquals(screen.navItem, rebuilt?.navItem)
    }
    @Test
    fun builtInMarketDetailCanBeReconstructedFromItsResourceId() {
        val screen = Screen.BuiltInMarketDetail("sandbox-code-runner")
        val rebuilt = ScreenRouteRegistry.buildScreen(ScreenRouteRegistry.routeIdOf(screen),
            mapOf("resourceId" to "sandbox-code-runner"))
        assertEquals(screen, rebuilt)
        assertEquals(screen.navItem, rebuilt?.navItem)
    }
}