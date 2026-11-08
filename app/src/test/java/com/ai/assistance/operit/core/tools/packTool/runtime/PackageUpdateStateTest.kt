package com.ai.assistance.operit.core.tools.packTool.runtime

import org.junit.Assert.*
import org.junit.Test

class PackageUpdateStateTest {
    @Test fun overlappingPluginAndChildUpdatesHaveOneOwner() {
        val names = setOf("test.plugin", "test.plugin.child")
        assertTrue(PackageUpdateState.start(names, names))
        try {
            assertTrue(PackageUpdateState.contains("test.plugin.child"))
            assertTrue(PackageUpdateState.isPlugin("test.plugin.child"))
            assertFalse(PackageUpdateState.start(setOf("test.plugin.child")))
            assertTrue(PackageUpdateState.contains("test.plugin"))
        } finally { PackageUpdateState.finish(names) }
        assertFalse(PackageUpdateState.contains("test.plugin.child"))
        assertFalse(PackageUpdateState.isPlugin("test.plugin.child"))
    }
    @Test fun catalogChecksAndInstallsCannotChangeTheCatalogAtTheSameTime() {
        assertTrue(PackageUpdateState.beginCatalogCheck())
        try {
            assertFalse(PackageUpdateState.start(setOf("test.script")))
            assertFalse(PackageUpdateState.beginCatalogCheck())
        } finally { PackageUpdateState.endCatalogCheck() }
        assertTrue(PackageUpdateState.start(setOf("test.script")))
        try { assertFalse(PackageUpdateState.beginCatalogCheck()) }
        finally { PackageUpdateState.finish(setOf("test.script")) }
    }

}
