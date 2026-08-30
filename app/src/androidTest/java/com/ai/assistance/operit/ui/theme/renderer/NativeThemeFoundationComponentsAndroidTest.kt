package com.ai.assistance.operit.ui.theme.renderer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.ui.theme.renderer.action.NativeThemeActionButtonV1
import com.ai.assistance.operit.ui.theme.renderer.container.NativeThemeSectionV1
import com.ai.assistance.operit.ui.theme.renderer.data.NativeThemeStatV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusKindV1
import com.ai.assistance.operit.ui.theme.renderer.feedback.NativeThemeOperationStatusV1
import com.ai.assistance.operit.ui.theme.renderer.input.NativeThemeChoiceItemV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.theme.NativeThemeHostSurface
import com.ai.assistance.operit.ui.theme.NativeThemeOffscreenHost
import com.ai.assistance.operit.ui.theme.NativeThemeV1DarkColorScheme
import com.ai.assistance.operit.ui.theme.NativeThemeV1LightColorScheme
import com.ai.assistance.operit.ui.theme.resolveNativeThemeForDetachedComposeHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeFoundationComponentsAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionButtonExposesOneButtonActionAndDispatchesActivation() {
        var activations = 0

        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeActionButtonV1(
                    label = "Export",
                    leading = { modifier ->
                        Box(Modifier.testTag("action-leading").then(modifier))
                    },
                    onActivate = { activations += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Export")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }
        composeTestRule.onNodeWithTag("action-leading", useUnmergedTree = true).assertExists()
        composeTestRule.runOnIdle { assertEquals(1, activations) }
    }

    @Test
    fun disabledActionButtonDoesNotDispatchActivation() {
        var activations = 0

        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeActionButtonV1(
                    label = "Unavailable",
                    leading = { modifier -> Box(modifier) },
                    onActivate = { activations += 1 },
                    enabled = false,
                )
            }
        }

        composeTestRule
            .onNodeWithText("Unavailable")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(0, activations) }
    }

    @Test
    fun actionButtonKeepsItsTouchTargetUnderAConstrainedCallerModifier() {
        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeActionButtonV1(
                    label = "Compact export",
                    leading = { modifier -> Box(modifier) },
                    onActivate = {},
                    modifier = Modifier.height(32.dp),
                )
            }
        }

        composeTestRule.onNodeWithText("Compact export").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun choiceItemUsesOneRadioActionAndDispatchesSelection() {
        var selections = 0

        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeChoiceItemV1(
                    label = "Keep existing",
                    supportingText = "Do not replace matching records.",
                    selected = true,
                    onSelect = { selections += 1 },
                )
            }
        }

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeTestRule
            .onNodeWithText("Keep existing")
            .assertHasClickAction()
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performTouchInput { click() }
        composeTestRule.onNodeWithText("Do not replace matching records.").assertExists()
        composeTestRule.runOnIdle { assertEquals(1, selections) }
    }

    @Test
    fun disabledChoiceItemDoesNotDispatchSelection() {
        var selections = 0

        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeChoiceItemV1(
                    label = "Unavailable choice",
                    selected = false,
                    enabled = false,
                    onSelect = { selections += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Unavailable choice")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(0, selections) }
    }

    @Test
    fun sectionExposesHeadingAndBothControlledSlots() {
        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeSectionV1(
                    title = "Chat history",
                    description = "Manage stored conversations.",
                    leading = { modifier ->
                        Box(Modifier.testTag("section-leading").then(modifier))
                    },
                ) {
                    Text("Section content", modifier = Modifier.testTag("section-content"))
                }
            }
        }

        composeTestRule
            .onNodeWithText("Chat history")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeTestRule.onNodeWithTag("section-leading", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("section-content", useUnmergedTree = true).assertExists()
    }

    @Test
    fun loadingStatusExposesPoliteLiveRegionAndIndeterminateProgress() {
        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeOperationStatusV1(
                    message = "Exporting chat history",
                    kind = NativeThemeOperationStatusKindV1.LOADING,
                )
            }
        }

        composeTestRule
            .onNodeWithText("Exporting chat history")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                )
            )
    }

    @Test
    fun errorStatusExposesTitleLiveRegionAndOptionalLeadingSlot() {
        composeTestRule.setContent {
            MaterialTheme {
                NativeThemeOperationStatusV1(
                    title = "Backup failed",
                    message = "The destination cannot be written.",
                    kind = NativeThemeOperationStatusKindV1.ERROR,
                    leading = { modifier ->
                        Box(Modifier.testTag("status-leading").then(modifier))
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Backup failed")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag("status-leading", useUnmergedTree = true).assertExists()
    }

    @Test
    fun statMergesLabelValueAndLeadingSlotIntoOneDescription() {
        composeTestRule.setContent {
            NativeThemeStatTestHost {
                NativeThemeStatV1(
                    label = "Conversations",
                    value = "128",
                    leading = { modifier ->
                        Box(Modifier.testTag("stat-leading").then(modifier))
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Conversations")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "128"))
        composeTestRule.onNodeWithTag("stat-leading", useUnmergedTree = true).assertExists()
    }

    @Composable
    private fun NativeThemeStatTestHost(content: @Composable () -> Unit) {
        val snapshot =
            ThemePreferenceSnapshot(
                source = "character_card",
                sourceId = "default_character",
                values = ThemePreferenceValues.defaultVisual(),
            )
        val resolvedTheme =
            resolveNativeThemeForDetachedComposeHost(
                snapshot = snapshot,
                hostSurface = NativeThemeHostSurface.MAIN,
                systemDarkTheme = false,
                lightColorScheme = NativeThemeV1LightColorScheme,
                darkColorScheme = NativeThemeV1DarkColorScheme,
            )
        NativeThemeOffscreenHost(
            snapshot = snapshot,
            resolvedTheme = resolvedTheme,
            content = content,
        )
    }

}
