package com.ai.assistance.operit.core.tools.agent

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android uses ICU for Regex; JVM-only tests accepted the previous unescaped closing braces. */
@RunWith(AndroidJUnit4::class)
class PhoneUiParsingAndroidTest {
    @Test fun ordinarySelectorBindingDoesNotCrashClassInitialization() {
        val target = PhoneTarget(id = "com.example.app:id/search")
        assertEquals(target, target.bind(emptyMap()))
    }

    @Test fun argumentBindingAndCatalogNamesWorkOnAndroidIcu() {
        assertEquals("燕云十六声", bindPhoneArguments("{{query}}", mapOf("query" to "燕云十六声")))
        assertEquals(setOf("query"), phoneArgumentNames("{{query}}"))
        assertTrue(isPhoneArgument("{{query}}"))
        assertFalse(isPhoneArgument("{{}}"))
    }

    @Test fun accessibilityBoundsUseExplicitLiteralBrackets() {
        assertEquals(PhoneBounds(12, 34, 56, 78), PhoneBounds.parse("[12,34][56,78]"))
        assertNull(PhoneBounds.parse("12,34,56,78"))
    }
}
