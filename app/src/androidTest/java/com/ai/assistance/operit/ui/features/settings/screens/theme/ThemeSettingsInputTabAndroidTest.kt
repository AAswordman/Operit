package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsInputTabAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: ThemeEditorSession

    @Before
    fun setUp() {
        session =
            ThemeEditorSession(
                persistentPreferences = UserPreferencesManager.getInstance(context),
                initialValues = ThemePreferenceValues.defaultVisual(),
            )
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        ThemeSettingsInputTab(editorSession = session)
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        session.dispose()
    }

    @Test
    fun composerStyleSelectionUpdatesTheDraft() {
        val classic = context.getString(R.string.input_style_classic)
        val agent = context.getString(R.string.input_style_agent)

        composeTestRule.onNodeWithText(agent).assertIsSelected()
        composeTestRule.onNodeWithText(classic).performClick().assertIsSelected()
        composeTestRule.runOnIdle {
            assertEquals(
                NativeThemePreferenceOptionsV1.INPUT_STYLE_CLASSIC,
                session.currentValues.requiredString(NativeThemePreferenceSchemaV1.inputStyle),
            )
        }
    }

    @Test
    fun advancedGlassControlsFollowTransparencyAndDisclosure() {
        val transparent = context.getString(R.string.theme_chat_input_transparent)
        val advanced = context.getString(R.string.advanced_settings)
        val expanded = context.getString(R.string.expanded)
        val liquidGlass = context.getString(R.string.theme_chat_input_liquid_glass)
        val waterGlass = context.getString(R.string.theme_chat_input_water_glass)

        composeTestRule.onNodeWithText(advanced).assertDoesNotExist()
        composeTestRule.onNodeWithText(transparent).performScrollTo().performClick()
        composeTestRule
            .onNodeWithText(advanced)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.collapsed),
                ),
            )
        composeTestRule.onNodeWithText(advanced).performClick().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expanded),
        )
        composeTestRule.onNodeWithText(liquidGlass).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(waterGlass).performScrollTo().assertIsDisplayed()
    }
}
