package com.ai.assistance.operit.util.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathMapperLinuxPathAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ubuntuRoot = File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu").absolutePath

    @Test fun mapLinuxPath_binPath_mapsUnderUbuntuRoot() {
        assertEquals("$ubuntuRoot/bin/bash", PathMapper.mapLinuxPath(context, "/bin/bash"))
    }

    @Test fun mapLinuxPath_usrPath_mapsUnderUbuntuRoot() {
        assertEquals("$ubuntuRoot/usr/lib", PathMapper.mapLinuxPath(context, "/usr/lib"))
    }
}
