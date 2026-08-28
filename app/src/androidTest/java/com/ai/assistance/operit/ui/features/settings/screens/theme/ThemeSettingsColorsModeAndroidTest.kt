package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeColorTargetV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsColorsModeAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: ThemeEditorSession
    private var requestedTarget: NativeThemeColorTargetV1? = null
    private var requestedColor: Int? = null

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
                        NativeThemeEditorDefinitionV1.colorsAndMode.groups.forEachIndexed { index, group ->
                            if (index > 0) HorizontalDivider()
                            NativeThemeEditorGroupV1(
                                definition = group,
                                values = session.document.collectAsState().value.draft,
                                editorSession = session,
                                onColorRequested = { definition, color ->
                                    requestedTarget = definition.target
                                    requestedColor = color
                                },
                            )
                        }
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
    fun explicitAppearanceModeUpdatesTheDraft() {
        val followSystem = context.getString(R.string.theme_follow_system)
        val light = context.getString(R.string.theme_light)
        val dark = context.getString(R.string.theme_dark)

        composeTestRule.onNodeWithText(light).assertDoesNotExist()
        composeTestRule.onNodeWithText(followSystem).performClick()
        composeTestRule.onNodeWithText(light).assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText(dark).performScrollTo().performClick().assertIsSelected()
        composeTestRule.runOnIdle {
            assertEquals(
                NativeThemePreferenceOptionsV1.THEME_MODE_DARK,
                session.currentValues.requiredString(NativeThemePreferenceSchemaV1.themeMode),
            )
        }
    }

    @Test
    fun customPaletteMaterializesDefaultsAndRequestsTheTypedColor() {
        val useCustomColors = context.getString(R.string.theme_use_custom_color)
        val primaryColor = context.getString(R.string.theme_primary_color)
        val advanced = context.getString(R.string.advanced_settings)

        composeTestRule.onNodeWithText(primaryColor).assertDoesNotExist()
        composeTestRule.onNodeWithText(useCustomColors).performScrollTo().performClick()
        composeTestRule.onNodeWithText(primaryColor).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(advanced).performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                0xFFFF00FF.toInt(),
                session.currentValues.int(NativeThemePreferenceSchemaV1.customPrimaryColor),
            )
            assertEquals(
                0xFF0000FF.toInt(),
                session.currentValues.int(NativeThemePreferenceSchemaV1.customSecondaryColor),
            )
            assertEquals(NativeThemeColorTargetV1.PRIMARY, requestedTarget)
            assertEquals(0xFFFF00FF.toInt(), requestedColor)
        }
    }
}
