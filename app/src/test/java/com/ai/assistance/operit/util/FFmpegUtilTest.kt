package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FFmpegUtilTest {
    @Test
    fun scaleFilterEscapesCommaForFfmpegKit() {
        assertEquals("scale=min(640\\,iw):-2", FFmpegUtil.scaleFilterMaxWidth(640))
    }
}
