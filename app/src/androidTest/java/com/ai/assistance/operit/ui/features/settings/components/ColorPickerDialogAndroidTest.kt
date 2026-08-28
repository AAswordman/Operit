package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPickerDialogAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun confirmingWithoutInteractionPreservesInitialArgb() {
        val initialColor = 0x80123456.toInt()
        var selectedColor: Int? = null
        composeTestRule.setContent {
            MaterialTheme {
                ColorPickerDialog(
                    initialColor = initialColor,
                    title = "Test color",
                    recentColors = emptyList(),
                    onColorSelected = { color -> selectedColor = color },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.colorpicker_confirm)).performClick()
        composeTestRule.runOnIdle { assertEquals(initialColor, selectedColor) }
    }

    @Test
    fun applyingEightDigitHexPreservesAlpha() {
        val expectedColor = 0x40112233.toInt()
        var selectedColor: Int? = null
        composeTestRule.setContent {
            MaterialTheme {
                ColorPickerDialog(
                    initialColor = 0xFFABCDEF.toInt(),
                    title = "Test color",
                    recentColors = emptyList(),
                    onColorSelected = { color -> selectedColor = color },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextReplacement("#40112233")
        composeTestRule
            .onNodeWithText(context.getString(R.string.colorpicker_apply))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.colorpicker_confirm)).performClick()
        composeTestRule.runOnIdle { assertEquals(expectedColor, selectedColor) }
    }
}
