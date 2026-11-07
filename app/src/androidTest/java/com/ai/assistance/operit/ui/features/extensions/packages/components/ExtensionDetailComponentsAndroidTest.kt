package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionDetailComponentsAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longMarkdownStartsWithAPreviewAndCanReturnToTheCollapsedHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expand = context.getString(R.string.extension_description_expand)
        val collapse = context.getString(R.string.extension_description_collapse)
        val body = (1..35).joinToString("\n\n") { "Paragraph $it: a detailed installed extension description with enough text to wrap onto multiple lines." }
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    ExtensionDetailDescription("Overview", body, "installed", previewLines = 4, markdown = true)
                }
            }
        }
        compose.onNodeWithText(expand).assertIsDisplayed()
        val content = compose.onNodeWithTag("extension-description-body-installed", useUnmergedTree = true)
        val collapsedHeight = content.getUnclippedBoundsInRoot().height.value
        compose.onNodeWithText(expand).performClick()
        compose.waitForIdle()
        assertTrue(content.getUnclippedBoundsInRoot().height.value > collapsedHeight * 2)
        compose.onNodeWithText(collapse).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText(expand).assertIsDisplayed()
        assertEquals(collapsedHeight, content.getUnclippedBoundsInRoot().height.value, 1f)
    }

    @Test
    fun shortDescriptionRemainsReadableWithoutAnUnnecessaryExpandControl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme {
                ExtensionDetailDescription("Overview", "A short description.", "short", previewLines = 4)
            }
        }
        compose.onNodeWithText("A short description.").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.extension_description_expand)).assertDoesNotExist()
    }

    @Test
    fun theFifthStatisticRemainsAccessibleOnTheDetailPage() {
        compose.setContent {
            MaterialTheme {
                ExtensionDetailStats(listOf(
                    ExtensionDetailStat("Type", "Plugin"),
                    ExtensionDetailStat("Status", "Enabled"),
                    ExtensionDetailStat("Version", "0.7.303"),
                    ExtensionDetailStat("Subpackages", "0"),
                    ExtensionDetailStat("Interfaces", "1"),
                ))
            }
        }
        compose.onNodeWithText("Interfaces").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }
}