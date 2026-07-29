package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnhancedTableBlockAndroidTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun streamingContentUpdate_keepsHorizontalScrollInteractive() {
        val content = mutableStateOf(WIDE_TABLE)
        val tableDescription =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.table_block)

        composeTestRule.setContent {
            MaterialTheme {
                Column(
                    modifier =
                        Modifier
                            .width(240.dp)
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                ) {
                    EnhancedTableBlock(tableContent = content.value)
                    Spacer(modifier = Modifier.height(500.dp))
                }
            }
        }

        val table = composeTestRule.onNodeWithContentDescription(tableDescription)
        table.assertIsDisplayed()
        val initialImage = table.captureToImage().asAndroidBitmap()

        table.performTouchInput {
            swipe(
                start = center + Offset(80f, 0f),
                end = center - Offset(80f, 0f),
                durationMillis = 2_000L,
            )
        }
        composeTestRule.waitForIdle()
        val afterFirstSwipe = table.captureToImage().asAndroidBitmap()
        assertFalse(initialImage.sameAs(afterFirstSwipe))

        composeTestRule.runOnIdle {
            content.value = "$WIDE_TABLE\n"
        }
        composeTestRule.waitForIdle()
        val afterStreamingUpdate = table.captureToImage().asAndroidBitmap()
        assertTrue(afterFirstSwipe.sameAs(afterStreamingUpdate))

        table.performTouchInput {
            swipe(
                start = center - Offset(80f, 0f),
                end = center + Offset(80f, 0f),
                durationMillis = 2_000L,
            )
        }
        composeTestRule.waitForIdle()
        val afterSecondSwipe = table.captureToImage().asAndroidBitmap()
        assertFalse(afterStreamingUpdate.sameAs(afterSecondSwipe))
    }

    private companion object {
        val WIDE_TABLE =
            """
            | Package identifier with a long value | Recommendation with detailed status | Reason for the recommendation | Application category | Discovery source | Additional notes |
            | --- | --- | --- | --- | --- | --- |
            | com.example.application.identifier | Keep installed for system behavior | Required by a core device service | System application | Package manager | Do not disable this package |
            """.trimIndent()
    }
}
