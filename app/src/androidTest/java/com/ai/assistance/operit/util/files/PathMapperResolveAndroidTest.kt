package com.ai.assistance.operit.util.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathMapperResolveAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ubuntuRoot = File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu").absolutePath

    @Test fun resolvePath_linuxCaseInsensitive_mapsPath() {
        assertEquals("$ubuntuRoot/bin/sh", PathMapper.resolvePath(context, "/bin/sh", "LiNuX"))
    }

    @Test fun resolvePath_nullEnvironment_returnsOriginal() {
        assertEquals("/data/local/tmp", PathMapper.resolvePath(context, "/data/local/tmp", null))
    }

    @Test fun resolvePath_blankEnvironment_returnsOriginal() {
        assertEquals("/data/local/tmp", PathMapper.resolvePath(context, "/data/local/tmp", ""))
    }
}
