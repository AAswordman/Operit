package com.ai.assistance.operit.ui.features.settings.screens.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ThemeSettingsTypographyAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: ThemeEditorSession
    private var requestedAction: NativeThemeAssetActionV1? = null

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
                        val document = session.document.collectAsState().value
                        NativeThemeEditorGroupV1(
                            definition = NativeThemeEditorDefinitionV1.typographyFamily,
                            values = document.draft,
                            editorSession = session,
                            onAssetRequested = { definition ->
                                requestedAction = definition.action
                            },
                        )
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
    fun fontSourceAndAssetControlsFollowTheDraft() {
        val enable = context.getString(R.string.enable_custom_font)
        val system = context.getString(R.string.system_font)
        val file = context.getString(R.string.custom_font_file)
        val select = context.getString(R.string.select_font_file)
        val clear = context.getString(R.string.clear_font)

        composeTestRule.onNodeWithText(system).assertDoesNotExist()
        composeTestRule.onNodeWithText(enable).performClick()
        composeTestRule.onNodeWithText(system).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(file).performScrollTo().performClick()
        composeTestRule.onNodeWithText(select).performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(NativeThemeAssetActionV1.APP_FONT, requestedAction) }

        composeTestRule.runOnIdle {
            session.setOptionalString(
                NativeThemePreferenceSchemaV1.customFontPath,
                "file:///fonts/custom.ttf",
            )
        }
        composeTestRule.onNodeWithText(clear).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertNull(session.currentValues.string(NativeThemePreferenceSchemaV1.customFontPath))
        }
    }

    @Test
    fun fontScaleIsAvailableWithoutCustomFont() {
        val formattedScale = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(1f)
        val label = context.getString(R.string.font_size_scale_label, formattedScale)

        composeTestRule.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
    }
}
