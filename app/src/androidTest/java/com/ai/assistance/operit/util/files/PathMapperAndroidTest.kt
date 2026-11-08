package com.ai.assistance.operit.util.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathMapperAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ubuntuRoot = File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu").absolutePath

    @Test fun mapLinuxPath_rootSlash_returnsUbuntuRoot() {
        assertEquals(ubuntuRoot, PathMapper.mapLinuxPath(context, "/"))
    }

    @Test fun mapLinuxPath_emptyString_returnsUbuntuRoot() {
        assertEquals(ubuntuRoot, PathMapper.mapLinuxPath(context, ""))
    }

    @Test fun mapLinuxPath_simpleFile_joinsUnderUbuntuRoot() {
        assertEquals("$ubuntuRoot/etc/hosts", PathMapper.mapLinuxPath(context, "/etc/hosts"))
    }

    @Test fun mapLinuxPath_nestedFile_joinsUnderUbuntuRoot() {
        assertEquals("$ubuntuRoot/home/user/file.txt", PathMapper.mapLinuxPath(context, "/home/user/file.txt"))
    }

    @Test fun mapLinuxPath_relativePath_stillJoinsUnderUbuntuRoot() {
        assertEquals("$ubuntuRoot/tmp/data.txt", PathMapper.mapLinuxPath(context, "tmp/data.txt"))
    }

    @Test fun mapLinuxPath_multipleLeadingSlashes_areTrimmed() {
        assertEquals("$ubuntuRoot/var/log", PathMapper.mapLinuxPath(context, "///var/log"))
    }

    @Test fun mapLinuxPath_trailingSlash_isPreservedByFileJoin() {
        assertTrue(PathMapper.mapLinuxPath(context, "/home/user/").startsWith("$ubuntuRoot/home/user"))
    }

    @Test fun mapLinuxPath_pathWithSpaces_isSupported() {
        assertEquals("$ubuntuRoot/home/user/my file.txt", PathMapper.mapLinuxPath(context, "/home/user/my file.txt"))
    }

    @Test fun isLinuxEnvironment_acceptsLowercaseLinux() {
        assertTrue(PathMapper.isLinuxEnvironment("linux"))
    }

    @Test fun isLinuxEnvironment_acceptsUppercaseLinux() {
        assertTrue(PathMapper.isLinuxEnvironment("LINUX"))
    }

    @Test fun isLinuxEnvironment_rejectsAndroid() {
        assertFalse(PathMapper.isLinuxEnvironment("android"))
    }

    @Test fun isLinuxEnvironment_rejectsNull() {
        assertFalse(PathMapper.isLinuxEnvironment(null))
    }

    @Test fun resolvePath_linuxEnvironment_mapsPath() {
        assertEquals("$ubuntuRoot/etc/passwd", PathMapper.resolvePath(context, "/etc/passwd", "linux"))
    }

    @Test fun resolvePath_androidEnvironment_returnsOriginalPath() {
        assertEquals("/sdcard/file.txt", PathMapper.resolvePath(context, "/sdcard/file.txt", "android"))
    }

    @Test fun resolvePath_unknownEnvironment_returnsOriginalPath() {
        assertEquals("/custom/file.txt", PathMapper.resolvePath(context, "/custom/file.txt", "custom"))
    }
}
