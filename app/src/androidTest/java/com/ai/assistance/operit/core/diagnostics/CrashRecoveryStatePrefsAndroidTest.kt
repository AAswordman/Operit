package com.ai.assistance.operit.core.diagnostics

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashRecoveryStatePrefsAndroidTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun otherPreferenceKey_survivesMarkAndConsume() {
        val prefs = context.getSharedPreferences("crash_recovery_state", Context.MODE_PRIVATE)
        prefs.edit().putInt("count", 2).commit()
        CrashRecoveryState.markPendingCrashReportLaunch(context)
        CrashRecoveryState.consumePendingCrashReportLaunch(context)
        assertEquals(2, prefs.getInt("count", 0))
    }
}
