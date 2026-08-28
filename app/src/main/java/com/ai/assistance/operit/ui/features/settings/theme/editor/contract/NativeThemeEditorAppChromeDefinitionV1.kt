package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemePreferenceOptionsV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1

internal object NativeThemeEditorAppChromeDefinitionV1 {
    private val statusBarVisible =
        NativeThemeEditorPredicateV1.Not(
            NativeThemeEditorPredicateV1.BooleanEquals(
                field = NativeThemePreferenceSchemaV1.statusBarHidden,
                expected = true,
            ),
        )
    private val backgroundMediaEnabled =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.useBackgroundImage,
                    expected = true,
                ),
                NativeThemeEditorPredicateV1.StringPresent(
                    field = NativeThemePreferenceSchemaV1.backgroundImageUri,
                ),
            ),
        )
    private val statusBarColorAvailable =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                statusBarVisible,
                NativeThemeEditorPredicateV1.Not(
                    backgroundMediaEnabled,
                ),
            ),
        )
    private val statusBarColorVisible =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                statusBarColorAvailable,
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.statusBarTransparent,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.useCustomStatusBarColor,
                    expected = true,
                ),
            ),
        )
    private val appBarColorVisible =
        NativeThemeEditorPredicateV1.AllOf(
            listOf(
                NativeThemeEditorPredicateV1.Not(
                    NativeThemeEditorPredicateV1.BooleanEquals(
                        field = NativeThemePreferenceSchemaV1.toolbarTransparent,
                        expected = true,
                    ),
                ),
                NativeThemeEditorPredicateV1.BooleanEquals(
                    field = NativeThemePreferenceSchemaV1.useCustomAppBarColor,
                    expected = true,
                ),
            ),
        )
    private val navigationDrawerBackgroundColorVisible =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.useCustomNavigationDrawerBackgroundColor,
            expected = true,
        )
    private val navigationDrawerAccentColorVisible =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.useCustomNavigationDrawerAccentColor,
            expected = true,
        )
    private val chatHeaderOverlayVisible =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.chatHeaderTransparent,
            expected = true,
        )
    private val appBarContentModeVisible =
        NativeThemeEditorPredicateV1.BooleanEquals(
            field = NativeThemePreferenceSchemaV1.forceAppBarContentColorEnabled,
            expected = true,
        )

    val statusBar =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.status_bar"),
            title = NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.status_bar.hidden"),
                        title = NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_HIDDEN,
                        description = NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_HIDDEN_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.statusBarHidden,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.status_bar.transparent"),
                        title = NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_TRANSPARENT,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_TRANSPARENT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.statusBarTransparent,
                        visibleWhen = statusBarVisible,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.status_bar.custom"),
                        title = NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useCustomStatusBarColor,
                        visibleWhen = statusBarColorAvailable,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.status_bar.color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_STATUS_BAR_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.STATUS_BAR,
                        displayDefault = 0xFF6200EE.toInt(),
                        visibleWhen = statusBarColorVisible,
                    ),
                ),
        )

    val toolbar =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.toolbar"),
            title = NativeThemeEditorTextKey.APP_CHROME_TOOLBAR,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.toolbar.transparent"),
                        title = NativeThemeEditorTextKey.APP_CHROME_TOOLBAR_TRANSPARENT,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_TOOLBAR_TRANSPARENT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.toolbarTransparent,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.toolbar.custom_app_bar"),
                        title = NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_APP_BAR_COLOR,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_APP_BAR_COLOR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useCustomAppBarColor,
                        visibleWhen =
                            NativeThemeEditorPredicateV1.Not(
                                NativeThemeEditorPredicateV1.BooleanEquals(
                                    field = NativeThemePreferenceSchemaV1.toolbarTransparent,
                                    expected = true,
                                ),
                            ),
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.toolbar.app_bar_color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_APP_BAR_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.APP_BAR,
                        displayDefault = 0xFF6200EE.toInt(),
                        visibleWhen = appBarColorVisible,
                    ),
                ),
        )

    val navigationDrawer =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.navigation_drawer"),
            title = NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.water_glass"),
                        title = NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.navigationDrawerWaterGlass,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.button_liquid_glass"),
                        title =
                            NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.navigationDrawerButtonLiquidGlass,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.custom_background"),
                        title =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR_DESCRIPTION,
                        field =
                            NativeThemePreferenceSchemaV1.useCustomNavigationDrawerBackgroundColor,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.background_color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_BACKGROUND_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.NAVIGATION_DRAWER_BACKGROUND,
                        displayDefault = 0xFFF5F5F5.toInt(),
                        visibleWhen = navigationDrawerBackgroundColorVisible,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.custom_accent"),
                        title =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.useCustomNavigationDrawerAccentColor,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.navigation_drawer.accent_color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_NAVIGATION_DRAWER_ACCENT_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.NAVIGATION_DRAWER_ACCENT,
                        displayDefault = 0xFF6200EE.toInt(),
                        visibleWhen = navigationDrawerAccentColorVisible,
                    ),
                ),
        )

    val chatHeader =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.chat_header"),
            title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.chat_header.transparent"),
                        title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_TRANSPARENT,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_TRANSPARENT_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatHeaderTransparent,
                    ),
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.chat_header.overlay"),
                        title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_OVERLAY_MODE,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_OVERLAY_MODE_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.chatHeaderOverlayMode,
                        visibleWhen = chatHeaderOverlayVisible,
                    ),
                ),
        )

    val appBarContent =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.app_bar_content"),
            title = NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR,
            items =
                listOf(
                    NativeThemeBooleanControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.app_bar_content.force"),
                        title = NativeThemeEditorTextKey.APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR,
                        description =
                            NativeThemeEditorTextKey.APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR_DESCRIPTION,
                        field = NativeThemePreferenceSchemaV1.forceAppBarContentColorEnabled,
                    ),
                    NativeThemeStringChoiceDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.app_bar_content.mode"),
                        title = NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_MODE,
                        description = null,
                        field = NativeThemePreferenceSchemaV1.appBarContentColorMode,
                        options =
                            listOf(
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_LIGHT,
                                    title = NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_LIGHT,
                                ),
                                NativeThemeStringOptionDefinitionV1(
                                    value = NativeThemePreferenceOptionsV1.APP_BAR_CONTENT_COLOR_MODE_DARK,
                                    title = NativeThemeEditorTextKey.APP_CHROME_APP_BAR_CONTENT_COLOR_DARK,
                                ),
                            ),
                        presentation = NativeThemeChoicePresentation.SEGMENTED,
                        visibleWhen = appBarContentModeVisible,
                    ),
                ),
        )

    val headerIcons =
        NativeThemeEditorGroupDefinitionV1(
            id = NativeThemeEditorGroupId("app_chrome.chat_header.icons"),
            title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_ICONS,
            items =
                listOf(
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.chat_header.history_icon_color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_HISTORY_ICON_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.HISTORY_ICON,
                        displayDefault = 0xFF808080.toInt(),
                        advanced = true,
                    ),
                    NativeThemeColorControlDefinitionV1(
                        id = NativeThemeEditorItemId("app_chrome.chat_header.pip_icon_color"),
                        title = NativeThemeEditorTextKey.APP_CHROME_CHAT_HEADER_PIP_ICON_COLOR,
                        description = null,
                        target = NativeThemeColorTargetV1.PIP_ICON,
                        displayDefault = 0xFF808080.toInt(),
                        advanced = true,
                    ),
                ),
        )

    val section =
        NativeThemeEditorSectionDefinitionV1(
            id = NativeThemeEditorSectionId("app_chrome"),
            title = NativeThemeEditorTextKey.APP_CHROME,
            groups = listOf(statusBar, toolbar, navigationDrawer, chatHeader, appBarContent, headerIcons),
        )
}
