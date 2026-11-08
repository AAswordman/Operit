package com.ai.assistance.operit.core.diagnostics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashRecoveryStateRepeatAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clear() {
        context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun consumeTwiceAfterMark_onlyFirstReturnsTrue() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun consumeBeforeMarkThenAfterMark_behavesCorrectly() {
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }
}
