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
class CrashRecoveryStateAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetState() {
        context.applicationContext
            .getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test fun consumeWithoutMark_returnsFalse() {
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun markThenConsume_returnsTrue() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun consumeClearsPendingFlag() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun multipleMarksRemainSinglePendingFlag() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun applicationContextPath_isUsedSafely() {
        CrashRecoveryState.markPendingCrashReportLaunch(context.applicationContext)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun unrelatedPreferenceKeys_arePreserved() {
        val prefs = context.applicationContext.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
        prefs.edit().putString("other", "value").commit()
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        assertTrue(prefs.contains("other"))
    }

    @Test fun repeatedConsumeAfterClear_staysFalse() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun markAfterConsume_becomesPendingAgain() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }

    @Test fun separateContextReferences_shareSameState() {
        val ctx1 = context
        val ctx2 = context.applicationContext
        CrashRecoveryState.markPendingCrashReportLaunch(ctx1)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(ctx2))
    }

    @Test fun pendingFlagUsesCommitAndPersistsImmediately() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        val prefs = context.applicationContext.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean("preserve_logs_for_crash_report", false))
    }

    @Test fun consumeRemovesStoredKey() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        val prefs = context.applicationContext.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("preserve_logs_for_crash_report"))
    }

    @Test fun doubleConsumeAfterSingleMark_onlyFirstIsTrue() {
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        assertTrue(CrashRecoveryState.consumePendingCrashReportLaunch(context))
        assertFalse(CrashRecoveryState.consumePendingCrashReportLaunch(context))
    }
}
