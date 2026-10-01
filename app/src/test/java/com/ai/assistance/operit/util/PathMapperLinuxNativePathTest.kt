package com.ai.assistance.operit.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PathMapperLinuxNativePathTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun mapsNativeSearchPathAndRestoresLinuxResultPath() {
        val ubuntuRoot = File(temporaryFolder.root, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
        val hostPath = PathMapper.mapLinuxPath(ubuntuRoot, "/root/operit-src")

        assertEquals(File(ubuntuRoot, "root/operit-src").absolutePath, hostPath)
        assertEquals("/root/operit-src", PathMapper.unmapLinuxPath(ubuntuRoot, hostPath))
    }

    @Test
    fun mapsAndRestoresUbuntuRoot() {
        val ubuntuRoot = File(temporaryFolder.root, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")

        assertEquals(ubuntuRoot.absolutePath, PathMapper.mapLinuxPath(ubuntuRoot, "/"))
        assertEquals("/", PathMapper.unmapLinuxPath(ubuntuRoot, ubuntuRoot.absolutePath))
    }

    @Test
    fun doesNotUnmapPathsOutsideUbuntuRoot() {
        val ubuntuRoot = File(temporaryFolder.root, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
        val outsidePath = File(ubuntuRoot.parentFile, "ubuntu-other/root/operit-src")
        val escapedPath = PathMapper.mapLinuxPath(ubuntuRoot, "/../../outside")

        assertNull(PathMapper.unmapLinuxPath(ubuntuRoot, outsidePath.absolutePath))
        assertNull(PathMapper.unmapLinuxPath(ubuntuRoot, escapedPath))
    }
}
