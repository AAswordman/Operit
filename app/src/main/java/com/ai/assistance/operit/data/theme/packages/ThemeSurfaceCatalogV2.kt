package com.ai.assistance.operit.data.theme.packages

private val THEME_SURFACE_ID_PATTERN_V2 =
    Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+$")

private val THEME_COMPONENT_ID_PATTERN_V2 =
    Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*$")

@JvmInline
internal value class ThemeSurfaceIdV2(val value: String) {
    init {
        require(THEME_SURFACE_ID_PATTERN_V2.matches(value)) { "Invalid theme surface ID: $value" }
    }
}

@JvmInline
internal value class ThemeComponentIdV2(val value: String) {
    init {
        require(THEME_COMPONENT_ID_PATTERN_V2.matches(value)) { "Invalid theme component ID: $value" }
    }
}

/**
 * The complete daily Operit-owned visual surface set. Native route classes map to these stable
 * IDs; theme authors never depend on a Kotlin route class name.
 */
internal object ThemeSurfaceCatalogV2 {
    val APP_SHELL = ThemeSurfaceIdV2("app.shell")
    val APP_NAVIGATION = ThemeSurfaceIdV2("app.navigation")
    val CHAT_MAIN = ThemeSurfaceIdV2("chat.main")
    val CHAT_FLOATING = ThemeSurfaceIdV2("chat.floating")
    val CHAT_PERMISSION_OVERLAY = ThemeSurfaceIdV2("chat.permission_overlay")
    val BROWSER_SHELL = ThemeSurfaceIdV2("browser.shell")
    val WEB_CHAT_MAIN = ThemeSurfaceIdV2("web_chat.main")
    val MEMORY_GRAPH_LIBRARY = ThemeSurfaceIdV2("memory.graph_library")
    val MARKET_HOME = ThemeSurfaceIdV2("market.home")
    val MARKET_CATEGORY = ThemeSurfaceIdV2("market.category")
    val MARKET_ENTRY_DETAIL = ThemeSurfaceIdV2("market.entry_detail")
    val MARKET_PUBLISHER_CONSOLE = ThemeSurfaceIdV2("market.publisher_console")
    val MARKET_ARTIFACT_EDITOR = ThemeSurfaceIdV2("market.artifact_editor")
    val MARKET_REPOSITORY_EDITOR = ThemeSurfaceIdV2("market.repository_editor")
    val PACKAGES_MANAGER = ThemeSurfaceIdV2("packages.manager")
    val WORKFLOW_LIBRARY = ThemeSurfaceIdV2("workflow.library")
    val WORKFLOW_CANVAS_EDITOR = ThemeSurfaceIdV2("workflow.canvas_editor")
    val FILES_BROWSER = ThemeSurfaceIdV2("files.browser")
    val ASSISTANT_PROFILE = ThemeSurfaceIdV2("assistant.profile")
    val PERSONA_CARD_STUDIO = ThemeSurfaceIdV2("persona.card_studio")
    val PROMPT_TAG_MARKET = ThemeSurfaceIdV2("prompt_tag.market")
    val SETTINGS_INDEX = ThemeSurfaceIdV2("settings.index")
    val SETTINGS_FORM = ThemeSurfaceIdV2("settings.form")
    val SETTINGS_STATISTICS = ThemeSurfaceIdV2("settings.statistics")
    val TOOLBOX_INDEX = ThemeSurfaceIdV2("toolbox.index")
    val TOOLBOX_TOOL = ThemeSurfaceIdV2("toolbox.tool")
    val TERMINAL_SHELL = ThemeSurfaceIdV2("terminal.shell")
    val MEDIA_SHELL = ThemeSurfaceIdV2("media.shell")
    val PLUGIN_HOST_SHELL = ThemeSurfaceIdV2("plugin.host_shell")
    val OVERLAY_DIALOG = ThemeSurfaceIdV2("overlay.dialog")
    val OVERLAY_SHEET = ThemeSurfaceIdV2("overlay.sheet")
    val OVERLAY_MENU = ThemeSurfaceIdV2("overlay.menu")
    val OVERLAY_SNACKBAR = ThemeSurfaceIdV2("overlay.snackbar")
    val OVERLAY_TOAST = ThemeSurfaceIdV2("overlay.toast")
    val STATE_LOADING = ThemeSurfaceIdV2("state.loading")
    val STATE_EMPTY = ThemeSurfaceIdV2("state.empty")
    val STATE_ERROR = ThemeSurfaceIdV2("state.error")

    val requiredDailySurfaces: Set<ThemeSurfaceIdV2> =
        setOf(
            APP_SHELL,
            APP_NAVIGATION,
            CHAT_MAIN,
            CHAT_FLOATING,
            CHAT_PERMISSION_OVERLAY,
            BROWSER_SHELL,
            WEB_CHAT_MAIN,
            MEMORY_GRAPH_LIBRARY,
            MARKET_HOME,
            MARKET_CATEGORY,
            MARKET_ENTRY_DETAIL,
            MARKET_PUBLISHER_CONSOLE,
            MARKET_ARTIFACT_EDITOR,
            MARKET_REPOSITORY_EDITOR,
            PACKAGES_MANAGER,
            WORKFLOW_LIBRARY,
            WORKFLOW_CANVAS_EDITOR,
            FILES_BROWSER,
            ASSISTANT_PROFILE,
            PERSONA_CARD_STUDIO,
            PROMPT_TAG_MARKET,
            SETTINGS_INDEX,
            SETTINGS_FORM,
            SETTINGS_STATISTICS,
            TOOLBOX_INDEX,
            TOOLBOX_TOOL,
            TERMINAL_SHELL,
            MEDIA_SHELL,
            PLUGIN_HOST_SHELL,
            OVERLAY_DIALOG,
            OVERLAY_SHEET,
            OVERLAY_MENU,
            OVERLAY_SNACKBAR,
            OVERLAY_TOAST,
            STATE_LOADING,
            STATE_EMPTY,
            STATE_ERROR,
        )
}

internal object ThemeComponentCatalogV2 {
    val APP_BAR = ThemeComponentIdV2("app_bar")
    val NAVIGATION = ThemeComponentIdV2("navigation")
    val PAGE = ThemeComponentIdV2("page")
    val SECTION = ThemeComponentIdV2("section")
    val LIST_ITEM = ThemeComponentIdV2("list_item")
    val BUTTON = ThemeComponentIdV2("button")
    val ICON_BUTTON = ThemeComponentIdV2("icon_button")
    val INPUT = ThemeComponentIdV2("input")
    val COMPOSER = ThemeComponentIdV2("composer")
    val MESSAGE_USER = ThemeComponentIdV2("message_user")
    val MESSAGE_ASSISTANT = ThemeComponentIdV2("message_assistant")
    val DIALOG = ThemeComponentIdV2("dialog")
    val SHEET = ThemeComponentIdV2("sheet")
    val MENU = ThemeComponentIdV2("menu")
    val SNACKBAR = ThemeComponentIdV2("snackbar")
    val STATUS = ThemeComponentIdV2("status")

    val requiredComponents: Set<ThemeComponentIdV2> =
        setOf(
            APP_BAR,
            NAVIGATION,
            PAGE,
            SECTION,
            LIST_ITEM,
            BUTTON,
            ICON_BUTTON,
            INPUT,
            COMPOSER,
            MESSAGE_USER,
            MESSAGE_ASSISTANT,
            DIALOG,
            SHEET,
            MENU,
            SNACKBAR,
            STATUS,
        )
}
