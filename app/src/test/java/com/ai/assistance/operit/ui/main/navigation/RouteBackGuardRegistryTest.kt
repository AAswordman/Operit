package com.ai.assistance.operit.ui.main.navigation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBackGuardRegistryTest {
    @Test
    fun routeWithoutGuard_canNavigateBack() = runTest {
        val registry = RouteBackGuardRegistry()

        assertTrue(registry.canNavigateBack("route"))
    }

    @Test
    fun registeredGuard_controlsOnlyItsRoute() = runTest {
        val registry = RouteBackGuardRegistry()
        registry.register("guarded") { false }

        assertTrue(registry.hasGuard("guarded"))
        assertFalse(registry.canNavigateBack("guarded"))
        assertTrue(registry.canNavigateBack("other"))
    }

    @Test
    fun staleUnregister_doesNotRemoveNewGuard() = runTest {
        val registry = RouteBackGuardRegistry()
        val unregisterOld = registry.register("route") { false }
        registry.register("route") { true }

        unregisterOld()

        assertTrue(registry.hasGuard("route"))
        assertTrue(registry.canNavigateBack("route"))
    }
}
