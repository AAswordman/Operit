package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.LocalizedText
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.ui.features.extensions.packages.components.dialogs.packageui.PackageToolCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionDisabledControlsAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun disablingKeepsOpenVisibleAndActivationControlsInPlace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val checked = mutableStateOf(true)
        var opened = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    ExtensionDetailActions(
                        displayName = "Installed extension",
                        checked = checked.value,
                        showOpen = true,
                        openEnabled = checked.value,
                        toggleEnabled = true,
                        onOpen = { opened++ },
                        onToggle = { checked.value = it },
                    )
                }
            }
        }
        val open = compose.onNodeWithText(context.getString(R.string.open))
        val activation = compose.onNodeWithTag("extension-detail-activation", useUnmergedTree = true)
        val before = activation.getUnclippedBoundsInRoot()
        open.assertIsEnabled()
        val toggle = compose.onNodeWithContentDescription("Installed extension")
        val toggleBefore = toggle.getUnclippedBoundsInRoot()
        toggle.performClick()
        compose.waitForIdle()
        open.assertIsDisplayed().assertIsNotEnabled()
        val after = activation.getUnclippedBoundsInRoot()
        assertEquals(before.left.value, after.left.value, 0.1f)
        assertEquals(before.top.value, after.top.value, 0.1f)
        val toggleAfter = toggle.getUnclippedBoundsInRoot()
        assertEquals(toggleBefore.left.value, toggleAfter.left.value, 0.1f)
        assertEquals(toggleBefore.top.value, toggleAfter.top.value, 0.1f)
        open.performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithContentDescription("Installed extension").performClick()
        open.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun disabledToolCannotRunAndBecomesClickableAfterEnabling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabled = mutableStateOf(false)
        var executions = 0
        val tool = PackageTool(
            name = "get_current_date", description = LocalizedText.of("Read the current date"),
            parameters = emptyList(), script = "function get_current_date() {}",
        )
        compose.setContent {
            MaterialTheme {
                PackageToolCard(tool = tool, toolIdPrefix = "daily_life", enabled = enabled.value,
                    onExecute = { executions++ })
            }
        }
        val run = compose.onNodeWithText(context.getString(R.string.run))
        run.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, executions); enabled.value = true }
        run.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, executions) }
    }
}