package com.ai.assistance.operit.ui.theme.renderer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeThemeNavigationDrawerItemAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectedItemExposesNavigationSemanticsAndDispatchesActivation() {
        var activations = 0

        composeTestRule.setContent {
            TestTheme {
                NativeThemeNavigationDrawerItemV1(
                    label = "Chat",
                    selected = true,
                    semanticRole =
                        NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
                    leading = { modifier -> Box(modifier.testTag("leading")) },
                    onActivate = { activations += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Chat")
            .assertHasClickAction()
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performTouchInput { click() }
        composeTestRule.onNodeWithTag("leading", useUnmergedTree = true).assertExists()
        composeTestRule.runOnIdle { assertEquals(1, activations) }
    }

    @Test
    fun disabledItemDoesNotDispatchActivation() {
        var activations = 0

        composeTestRule.setContent {
            TestTheme {
                NativeThemeNavigationDrawerItemV1(
                    label = "Unavailable",
                    selected = false,
                    enabled = false,
                    semanticRole =
                        NativeThemeNavigationDrawerItemSemanticRoleV1.NAVIGATION_DESTINATION,
                    leading = { modifier -> Box(modifier) },
                    onActivate = { activations += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Unavailable")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(0, activations) }
    }

    @Test
    fun actionItemExposesButtonSemanticsAndDispatchesActivation() {
        var activations = 0

        composeTestRule.setContent {
            TestTheme {
                NativeThemeNavigationDrawerItemV1(
                    label = "Run action",
                    selected = false,
                    semanticRole = NativeThemeNavigationDrawerItemSemanticRoleV1.ACTION,
                    leading = { modifier -> Box(modifier) },
                    onActivate = { activations += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Run action")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performTouchInput { click() }
        composeTestRule.runOnIdle { assertEquals(1, activations) }
    }

    @androidx.compose.runtime.Composable
    private fun TestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(LocalThemePreferenceSnapshot provides testSnapshot()) {
            MaterialTheme(content = content)
        }
    }

    private fun testSnapshot(): ThemePreferenceSnapshot =
        ThemePreferenceSnapshot(
            source = "character_card",
            sourceId = "component-catalog-test",
            values = ThemePreferenceValues.defaultVisual(),
        )
}
