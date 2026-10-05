package com.ai.assistance.operit.core.tools.permissions

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionCapabilitySessionTest {
    private val admin = PermissionCapabilities(
        mode = AndroidPermissionLevel.ADMIN,
        shizukuRunning = true,
        shizukuGranted = true
    )

    private val root = PermissionCapabilities(
        mode = AndroidPermissionLevel.ROOT,
        rootAvailable = true
    )

    @Test
    fun reusesSnapshotUntilSessionBeginsAgain() {
        val session = PermissionCapabilitySession()
        session.cacheShell(admin)

        assertEquals(admin, session.cachedShell(null))
        assertEquals(admin, session.cachedShell(AndroidPermissionLevel.ADMIN))

        session.begin()

        assertNull(session.cachedShell(null))
    }

    @Test
    fun modeChangeDoesNotReuseSnapshotFromAnotherMode() {
        val session = PermissionCapabilitySession()
        session.cacheShell(admin)

        assertNull(session.cachedShell(AndroidPermissionLevel.ROOT))
        session.cacheShell(root)
        assertEquals(root, session.cachedShell(AndroidPermissionLevel.ROOT))
    }

    @Test
    fun separateMessageSessionsDoNotShareSnapshots() {
        val firstSession = PermissionCapabilityResolver.beginSession()
        val secondSession = PermissionCapabilityResolver.beginSession()
        firstSession.cacheShell(admin)

        assertNull(secondSession.cachedShell(null))
    }

    @Test
    fun uiSnapshotSharesTheShellSnapshotAndClearingEitherClearsTheSession() {
        val session = PermissionCapabilitySession()
        session.cacheUi(admin.copy(accessibilityAvailable = true))

        assertEquals(admin.mode, session.cachedShell(null)?.mode)
        assertEquals(true, session.cachedUi(AndroidPermissionLevel.ADMIN)?.accessibilityAvailable)

        session.cacheShell(root)

        assertEquals(root, session.cachedShell(null))
        assertNull(session.cachedUi(null))
    }
}
