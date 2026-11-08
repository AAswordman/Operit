package com.ai.assistance.operit.util.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathMapperLinuxEnvAndroidTest {

    @Test fun isLinuxEnvironment_acceptsMixedCase() {
        assertTrue(PathMapper.isLinuxEnvironment("LiNuX"))
    }

    @Test fun isLinuxEnvironment_rejectsEmptyString() {
        assertFalse(PathMapper.isLinuxEnvironment(""))
    }

    @Test fun isLinuxEnvironment_rejectsWhitespaceString() {
        assertFalse(PathMapper.isLinuxEnvironment(" "))
    }
}
