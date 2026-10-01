package com.ai.assistance.operit.core.tools.permissions

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class PermissionCapabilitiesTest {
    @Test fun userModesAreExactlyThreeAndLegacyNamesMapToAdmin() {
        assertEquals(listOf("STANDARD", "ADMIN", "ROOT"), AndroidPermissionLevel.values().map { it.name })
        for (name in listOf("ACCESSIBILITY", "DEBUGGER", "accessibility", "debugger", "ADMIN")) {
            assertEquals(AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.fromString(name))
        }
        assertEquals(AndroidPermissionLevel.STANDARD, AndroidPermissionLevel.fromString(null))
        assertEquals(AndroidPermissionLevel.STANDARD, AndroidPermissionLevel.fromString("unknown"))
        assertEquals(AndroidPermissionLevel.ROOT, AndroidPermissionLevel.fromString("ROOT"))
    }

    @Test fun legacyNamesDoNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.fromString("accessibility"))
            assertEquals(AndroidPermissionLevel.ADMIN, AndroidPermissionLevel.fromString("admin"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun allModeAndAuthorizationCombinationsChooseTheExpectedBackend() {
        for (mode in AndroidPermissionLevel.values()) {
            for (bits in 0 until 16) {
                val running = bits and 1 != 0
                val granted = bits and 2 != 0
                val accessibility = bits and 4 != 0
                val root = bits and 8 != 0
                val capabilities = PermissionCapabilities(mode, running, granted, accessibility, root)
                val allowed = mode != AndroidPermissionLevel.STANDARD
                val rootAllowed = mode == AndroidPermissionLevel.ROOT && root
                val shell = when {
                    rootAllowed -> PermissionBackend.ROOT
                    allowed && running && granted -> PermissionBackend.SHIZUKU
                    else -> PermissionBackend.STANDARD
                }
                val ui = when {
                    rootAllowed -> PermissionBackend.ROOT
                    allowed && accessibility -> PermissionBackend.ACCESSIBILITY
                    else -> shell
                }
                assertEquals(shell, capabilities.shellBackend)
                assertEquals(ui, capabilities.uiBackend())
                assertEquals(shell, capabilities.uiBackend(hasExplicitDisplay = true))
                assertEquals(shell != PermissionBackend.STANDARD, capabilities.hasPrivilegedShell)
                assertEquals(rootAllowed, capabilities.canUseRoot)
                assertEquals(shell, capabilities.shellBackendFor(ShellIdentity.DEFAULT))
                assertEquals(PermissionBackend.STANDARD, capabilities.shellBackendFor(ShellIdentity.APP))
                assertEquals(if (rootAllowed) PermissionBackend.ROOT else null, capabilities.shellBackendFor(ShellIdentity.ROOT))
                assertEquals(if (shell != PermissionBackend.STANDARD) shell else null, capabilities.shellBackendFor(ShellIdentity.SHELL))
            }
        }
    }

    @Test fun accessibilityAloneDoesNotGrantAdbOrVirtualDisplayCapabilities() {
        val capabilities = PermissionCapabilities(AndroidPermissionLevel.ADMIN, accessibilityAvailable = true)
        assertFalse(capabilities.hasPrivilegedShell)
        assertFalse(capabilities.canUseShizuku)
        assertEquals(PermissionBackend.ACCESSIBILITY, capabilities.uiBackend())
        assertEquals(PermissionBackend.STANDARD, capabilities.shellBackend)
    }
}
