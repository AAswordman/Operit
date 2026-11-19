package com.ai.assistance.operit.core.tools.packTool

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the default-enable decision table used by [PackageManager.initializeDefaultPackages]
 * and [PackageManager.addToDisabledIfDefaultEnabled].
 *
 * The regression this guards is issue #1258: externally installed ToolPkg containers report
 * `isBuiltIn == false`, so gating auto-enable on `isBuiltIn` left them permanently disabled and
 * their prompt hooks never dispatched.
 */
class ToolPkgDefaultEnableTest {
    @Test
    fun `externally installed toolpkg container is auto-enabled`() {
        assertTrue(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = false,
                isToolPkgContainer = true,
                enabledByDefault = true,
                isSubpackage = false,
                isUserDisabled = false,
            )
        )
    }

    @Test
    fun `built-in package keeps auto-enabling`() {
        assertTrue(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = true,
                isToolPkgContainer = false,
                enabledByDefault = true,
                isSubpackage = false,
                isUserDisabled = false,
            )
        )
    }

    @Test
    fun `legacy external script package keeps requiring a manual enable`() {
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = false,
                isToolPkgContainer = false,
                enabledByDefault = true,
                isSubpackage = false,
                isUserDisabled = false,
            )
        )
    }

    @Test
    fun `opted-out package is not auto-enabled`() {
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = true,
                isToolPkgContainer = false,
                enabledByDefault = false,
                isSubpackage = false,
                isUserDisabled = false,
            )
        )
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = false,
                isToolPkgContainer = true,
                enabledByDefault = false,
                isSubpackage = false,
                isUserDisabled = false,
            )
        )
    }

    @Test
    fun `package the user turned off is never re-enabled`() {
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = true,
                isToolPkgContainer = false,
                enabledByDefault = true,
                isSubpackage = false,
                isUserDisabled = true,
            )
        )
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = false,
                isToolPkgContainer = true,
                enabledByDefault = true,
                isSubpackage = false,
                isUserDisabled = true,
            )
        )
    }

    @Test
    fun `subpackage is never auto-enabled through its container`() {
        assertFalse(
            shouldAutoEnableDefaultPackage(
                isBuiltIn = true,
                isToolPkgContainer = true,
                enabledByDefault = true,
                isSubpackage = true,
                isUserDisabled = false,
            )
        )
    }

    /**
     * A disable only survives a restart when the package would otherwise have been auto-enabled;
     * if the two rules drift apart, turning a package off silently undoes itself on next launch.
     */
    @Test
    fun `disable persistence mirrors the auto-enable rule`() {
        listOf(false, true).forEach { isBuiltIn ->
            listOf(false, true).forEach { isToolPkgContainer ->
                listOf(false, true).forEach { enabledByDefault ->
                    val autoEnabled = shouldAutoEnableDefaultPackage(
                        isBuiltIn = isBuiltIn,
                        isToolPkgContainer = isToolPkgContainer,
                        enabledByDefault = enabledByDefault,
                        isSubpackage = false,
                        isUserDisabled = false,
                    )
                    val rememberedAsDisabled = shouldRememberDisabledDefaultPackage(
                        isBuiltIn = isBuiltIn,
                        isToolPkgContainer = isToolPkgContainer,
                        enabledByDefault = enabledByDefault,
                    )
                    assertTrue(
                        "isBuiltIn=$isBuiltIn isToolPkgContainer=$isToolPkgContainer " +
                            "enabledByDefault=$enabledByDefault",
                        autoEnabled == rememberedAsDisabled,
                    )
                }
            }
        }
    }
}
