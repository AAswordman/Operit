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
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeAssetActionV1
import com.ai.assistance.operit.ui.features.settings.theme.editor.contract.NativeThemeEditorDefinitionV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsBackgroundAndroidTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: ThemeEditorSession
    private var requestedAsset: NativeThemeAssetActionV1? = null

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
                            definition = NativeThemeEditorDefinitionV1.backgroundMedia,
                            values = document.draft,
                            editorSession = session,
                            onAssetRequested = { definition -> requestedAsset = definition.action },
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
    fun enablingBackgroundExposesOnlyRelevantControls() {
        val enable = context.getString(R.string.theme_use_custom_bg)
        val mediaType = context.getString(R.string.theme_media_type)
        val opacity = context.getString(R.string.theme_bg_opacity, 30)
        val blur = context.getString(R.string.theme_background_blur)
        val blurRadius = context.getString(R.string.theme_background_blur_radius)
        val videoMuted = context.getString(R.string.theme_mute)

        composeTestRule.onNodeWithText(mediaType).assertDoesNotExist()
        composeTestRule.onNodeWithText(enable).performClick()
        composeTestRule.onNodeWithText(mediaType).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(opacity).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(blur).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(blurRadius).assertDoesNotExist()
        composeTestRule.onNodeWithText(videoMuted).assertDoesNotExist()
    }

    @Test
    fun mediaTypeIsDraftedBeforeAnAssetIsSelected() {
        val enable = context.getString(R.string.theme_use_custom_bg)
        val video = context.getString(R.string.theme_media_video)
        val selectVideo = context.getString(R.string.theme_select_video)

        composeTestRule.onNodeWithText(enable).performClick()
        composeTestRule.onNodeWithText(video).performScrollTo().performClick()
        composeTestRule.onNodeWithText(selectVideo).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(
                NativeThemePreferenceOptionsV1.MEDIA_TYPE_VIDEO,
                session.currentValues.requiredString(NativeThemePreferenceSchemaV1.backgroundMediaType),
            )
            assertNull(session.currentValues.string(NativeThemePreferenceSchemaV1.backgroundImageUri))
            assertEquals(NativeThemeAssetActionV1.BACKGROUND_MEDIA, requestedAsset)
        }
    }
}
