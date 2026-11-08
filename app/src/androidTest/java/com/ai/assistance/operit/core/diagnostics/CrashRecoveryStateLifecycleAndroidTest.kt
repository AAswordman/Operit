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
class CrashRecoveryStateLifecycleAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun markPending_setsFlagVisibleToDirectPrefsRead() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
            .getBoolean("preserve_logs_for_crash_report", false))
    }

    @Test fun consumeWithoutMark_doesNotCreateFlag() {
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        assertFalse(context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
            .contains("preserve_logs_for_crash_report"))
    }

    @Test fun secondMark_afterConsume_setsFlagAgain() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }
}
