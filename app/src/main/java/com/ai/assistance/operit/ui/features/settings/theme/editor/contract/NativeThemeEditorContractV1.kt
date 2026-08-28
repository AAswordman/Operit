package com.ai.assistance.operit.ui.features.settings.theme.editor.contract

import com.ai.assistance.operit.data.preferences.NativeThemeBooleanField
import com.ai.assistance.operit.data.preferences.NativeThemeIntField
import com.ai.assistance.operit.data.preferences.NativeThemeFloatField
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceRulesV1
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.NativeThemeStringField
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues

@JvmInline
internal value class NativeThemeEditorSectionId(val value: String)

@JvmInline
internal value class NativeThemeEditorGroupId(val value: String)

@JvmInline
internal value class NativeThemeEditorItemId(val value: String)

internal enum class NativeThemeEditorTextKey(val value: String) {
    COMPOSER("composer"),
    COMPOSER_STYLE("composer.style"),
    COMPOSER_STYLE_DESCRIPTION("composer.style.description"),
    COMPOSER_STYLE_CLASSIC("composer.style.classic"),
    COMPOSER_STYLE_AGENT("composer.style.agent"),
    COMPOSER_APPEARANCE("composer.appearance"),
    COMPOSER_TRANSPARENT("composer.transparent"),
    COMPOSER_TRANSPARENT_DESCRIPTION("composer.transparent.description"),
    COMPOSER_FLOATING("composer.floating"),
    COMPOSER_FLOATING_DESCRIPTION("composer.floating.description"),
    COMPOSER_LIQUID_GLASS("composer.liquid_glass"),
    COMPOSER_LIQUID_GLASS_DESCRIPTION("composer.liquid_glass.description"),
    COMPOSER_WATER_GLASS("composer.water_glass"),
    COMPOSER_WATER_GLASS_DESCRIPTION("composer.water_glass.description"),
    ADVANCED_SETTINGS("advanced_settings"),
    EXPANDED("expanded"),
    COLLAPSED("collapsed"),
    COLORS_AND_MODE("colors_and_mode"),
    APPEARANCE_MODE("appearance_mode"),
    FOLLOW_SYSTEM("appearance_mode.follow_system"),
    FOLLOW_SYSTEM_DESCRIPTION("appearance_mode.follow_system.description"),
    APPEARANCE_MODE_SELECT("appearance_mode.select"),
    APPEARANCE_MODE_LIGHT("appearance_mode.light"),
    APPEARANCE_MODE_DARK("appearance_mode.dark"),
    PALETTE("palette"),
    USE_CUSTOM_COLORS("palette.use_custom_colors"),
    USE_CUSTOM_COLORS_DESCRIPTION("palette.use_custom_colors.description"),
    PRIMARY_COLOR("palette.primary"),
    SECONDARY_COLOR("palette.secondary"),
    PICK_PRIMARY_COLOR("palette.primary.pick"),
    PICK_SECONDARY_COLOR("palette.secondary.pick"),
    ON_COLOR_MODE("palette.on_color_mode"),
    ON_COLOR_AUTO("palette.on_color.auto"),
    ON_COLOR_LIGHT("palette.on_color.light"),
    ON_COLOR_DARK("palette.on_color.dark"),
    COLOR_STATUS_BAR("color.status_bar"),
    COLOR_NAVIGATION_DRAWER_BACKGROUND("color.navigation_drawer.background"),
    COLOR_NAVIGATION_DRAWER_ACCENT("color.navigation_drawer.accent"),
    COLOR_HISTORY_ICON("color.history_icon"),
    COLOR_PIP_ICON("color.pip_icon"),
    COLOR_CURSOR_USER_BUBBLE("color.cursor.user_bubble"),
    COLOR_BUBBLE_USER_BUBBLE("color.bubble.user_bubble"),
    COLOR_BUBBLE_AI_BUBBLE("color.bubble.ai_bubble"),
    COLOR_BUBBLE_USER_TEXT("color.bubble.user_text"),
    COLOR_BUBBLE_AI_TEXT("color.bubble.ai_text"),
    PICK_COLOR("color.pick"),
    APP_CHROME("app_chrome"),
    APP_CHROME_STATUS_BAR("app_chrome.status_bar"),
    APP_CHROME_STATUS_BAR_HIDDEN("app_chrome.status_bar.hidden"),
    APP_CHROME_STATUS_BAR_HIDDEN_DESCRIPTION("app_chrome.status_bar.hidden.description"),
    APP_CHROME_STATUS_BAR_TRANSPARENT("app_chrome.status_bar.transparent"),
    APP_CHROME_STATUS_BAR_TRANSPARENT_DESCRIPTION("app_chrome.status_bar.transparent.description"),
    APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR("app_chrome.status_bar.custom"),
    APP_CHROME_USE_CUSTOM_STATUS_BAR_COLOR_DESCRIPTION("app_chrome.status_bar.custom.description"),
    APP_CHROME_STATUS_BAR_COLOR("app_chrome.status_bar.color"),
    APP_CHROME_TOOLBAR("app_chrome.toolbar"),
    APP_CHROME_TOOLBAR_TRANSPARENT("app_chrome.toolbar.transparent"),
    APP_CHROME_TOOLBAR_TRANSPARENT_DESCRIPTION("app_chrome.toolbar.transparent.description"),
    APP_CHROME_APP_BAR_COLOR("app_chrome.app_bar.color"),
    APP_CHROME_USE_CUSTOM_APP_BAR_COLOR("app_chrome.app_bar.custom"),
    APP_CHROME_USE_CUSTOM_APP_BAR_COLOR_DESCRIPTION("app_chrome.app_bar.custom.description"),
    APP_CHROME_NAVIGATION_DRAWER("app_chrome.navigation_drawer"),
    APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS("app_chrome.navigation_drawer.water_glass"),
    APP_CHROME_NAVIGATION_DRAWER_WATER_GLASS_DESCRIPTION(
        "app_chrome.navigation_drawer.water_glass.description"
    ),
    APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS(
        "app_chrome.navigation_drawer.button_liquid_glass"
    ),
    APP_CHROME_NAVIGATION_DRAWER_BUTTON_LIQUID_GLASS_DESCRIPTION(
        "app_chrome.navigation_drawer.button_liquid_glass.description"
    ),
    APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR(
        "app_chrome.navigation_drawer.custom_background"
    ),
    APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_BACKGROUND_COLOR_DESCRIPTION(
        "app_chrome.navigation_drawer.custom_background.description"
    ),
    APP_CHROME_NAVIGATION_DRAWER_BACKGROUND_COLOR("app_chrome.navigation_drawer.background_color"),
    APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR(
        "app_chrome.navigation_drawer.custom_accent"
    ),
    APP_CHROME_USE_CUSTOM_NAVIGATION_DRAWER_ACCENT_COLOR_DESCRIPTION(
        "app_chrome.navigation_drawer.custom_accent.description"
    ),
    APP_CHROME_NAVIGATION_DRAWER_ACCENT_COLOR("app_chrome.navigation_drawer.accent_color"),
    APP_CHROME_CHAT_HEADER("app_chrome.chat_header"),
    APP_CHROME_CHAT_HEADER_TRANSPARENT("app_chrome.chat_header.transparent"),
    APP_CHROME_CHAT_HEADER_TRANSPARENT_DESCRIPTION("app_chrome.chat_header.transparent.description"),
    APP_CHROME_CHAT_HEADER_OVERLAY_MODE("app_chrome.chat_header.overlay"),
    APP_CHROME_CHAT_HEADER_OVERLAY_MODE_DESCRIPTION("app_chrome.chat_header.overlay.description"),
    APP_CHROME_APP_BAR_CONTENT_COLOR("app_chrome.app_bar_content_color"),
    APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR("app_chrome.force_app_bar_content_color"),
    APP_CHROME_FORCE_APP_BAR_CONTENT_COLOR_DESCRIPTION(
        "app_chrome.force_app_bar_content_color.description"
    ),
    APP_CHROME_APP_BAR_CONTENT_COLOR_MODE("app_chrome.app_bar_content_color.mode"),
    APP_CHROME_APP_BAR_CONTENT_COLOR_LIGHT("app_chrome.app_bar_content_color.light"),
    APP_CHROME_APP_BAR_CONTENT_COLOR_DARK("app_chrome.app_bar_content_color.dark"),
    APP_CHROME_CHAT_HEADER_ICONS("app_chrome.chat_header.icons"),
    APP_CHROME_CHAT_HEADER_HISTORY_ICON_COLOR("app_chrome.chat_header.history_icon_color"),
    APP_CHROME_CHAT_HEADER_PIP_ICON_COLOR("app_chrome.chat_header.pip_icon_color"),
    TYPOGRAPHY("typography"),
    FONT_FAMILY("typography.family"),
    USE_CUSTOM_FONT("typography.use_custom_font"),
    USE_CUSTOM_FONT_DESCRIPTION("typography.use_custom_font.description"),
    FONT_SOURCE("typography.source"),
    FONT_SOURCE_SYSTEM("typography.source.system"),
    FONT_SOURCE_FILE("typography.source.file"),
    SYSTEM_FONT_NAME("typography.system_font"),
    SYSTEM_FONT_DEFAULT("typography.system_font.default"),
    SYSTEM_FONT_SERIF("typography.system_font.serif"),
    SYSTEM_FONT_SANS_SERIF("typography.system_font.sans_serif"),
    SYSTEM_FONT_MONOSPACE("typography.system_font.monospace"),
    SYSTEM_FONT_CURSIVE("typography.system_font.cursive"),
    FONT_FILE("typography.file"),
    FONT_FILE_DESCRIPTION("typography.file.description"),
    SELECT_FONT_FILE("typography.file.select"),
    CLEAR_FONT_FILE("typography.file.clear"),
    CURRENT_FONT_FILE("typography.file.current"),
    FONT_SCALE("typography.scale"),
    BACKGROUND("background"),
    BACKGROUND_MEDIA("background.media"),
    USE_BACKGROUND_MEDIA("background.media.use"),
    USE_BACKGROUND_MEDIA_DESCRIPTION("background.media.use.description"),
    MEDIA_TYPE("background.media.type"),
    MEDIA_TYPE_IMAGE("background.media.type.image"),
    MEDIA_TYPE_VIDEO("background.media.type.video"),
    BACKGROUND_ASSET("background.media.asset"),
    BACKGROUND_ASSET_DESCRIPTION("background.media.asset.description"),
    SELECT_BACKGROUND_IMAGE("background.media.asset.select_image"),
    SELECT_BACKGROUND_VIDEO("background.media.asset.select_video"),
    BACKGROUND_OPACITY("background.opacity"),
    BACKGROUND_BLUR("background.blur"),
    BACKGROUND_BLUR_DESCRIPTION("background.blur.description"),
    BACKGROUND_BLUR_RADIUS("background.blur.radius"),
    VIDEO_MUTED("background.video.muted"),
    VIDEO_LOOP("background.video.loop"),
    RECROP_BACKGROUND("background.media.asset.recrop"),
    MESSAGE_DETAILS_AND_MOTION("message_details_and_motion"),
    MESSAGE_REASONING("message.reasoning"),
    SHOW_THINKING_PROCESS("message.reasoning.show_thinking"),
    SHOW_THINKING_PROCESS_DESCRIPTION("message.reasoning.show_thinking.description"),
    SHOW_STATUS_TAGS("message.reasoning.show_status_tags"),
    SHOW_STATUS_TAGS_DESCRIPTION("message.reasoning.show_status_tags.description"),
    MESSAGE_IDENTITY("message.identity"),
    SHOW_ROLE_NAME("message.identity.show_role_name"),
    SHOW_ROLE_NAME_DESCRIPTION("message.identity.show_role_name.description"),
    SHOW_USER_NAME("message.identity.show_user_name"),
    SHOW_USER_NAME_DESCRIPTION("message.identity.show_user_name.description"),
    SHOW_MESSAGE_TIMESTAMP("message.identity.show_timestamp"),
    SHOW_MESSAGE_TIMESTAMP_DESCRIPTION("message.identity.show_timestamp.description"),
    MESSAGE_DIAGNOSTICS("message.diagnostics"),
    SHOW_MODEL_PROVIDER("message.diagnostics.show_provider"),
    SHOW_MODEL_PROVIDER_DESCRIPTION("message.diagnostics.show_provider.description"),
    SHOW_MODEL_NAME("message.diagnostics.show_name"),
    SHOW_MODEL_NAME_DESCRIPTION("message.diagnostics.show_name.description"),
    SHOW_MESSAGE_TOKEN_STATS("message.diagnostics.show_token_stats"),
    SHOW_MESSAGE_TOKEN_STATS_DESCRIPTION("message.diagnostics.show_token_stats.description"),
    SHOW_MESSAGE_TIMING_STATS("message.diagnostics.show_timing_stats"),
    SHOW_MESSAGE_TIMING_STATS_DESCRIPTION("message.diagnostics.show_timing_stats.description"),
    MESSAGE_ACTIVITY("message.activity"),
    SHOW_INPUT_PROCESSING_STATUS("message.activity.show_processing"),
    SHOW_INPUT_PROCESSING_STATUS_DESCRIPTION("message.activity.show_processing.description"),
    SHOW_CHAT_FLOATING_DOTS("message.activity.show_dots"),
    SHOW_CHAT_FLOATING_DOTS_DESCRIPTION("message.activity.show_dots.description"),
}

internal enum class NativeThemeColorTargetV1(
    val field: NativeThemeIntField,
    val pickerTitle: NativeThemeEditorTextKey,
) {
    PRIMARY(
        NativeThemePreferenceSchemaV1.customPrimaryColor,
        NativeThemeEditorTextKey.PICK_PRIMARY_COLOR,
    ),
    SECONDARY(
        NativeThemePreferenceSchemaV1.customSecondaryColor,
        NativeThemeEditorTextKey.PICK_SECONDARY_COLOR,
    ),
    STATUS_BAR(
        NativeThemePreferenceSchemaV1.customStatusBarColor,
        NativeThemeEditorTextKey.COLOR_STATUS_BAR,
    ),
    APP_BAR(
        NativeThemePreferenceSchemaV1.customAppBarColor,
        NativeThemeEditorTextKey.APP_CHROME_APP_BAR_COLOR,
    ),
    NAVIGATION_DRAWER_BACKGROUND(
        NativeThemePreferenceSchemaV1.customNavigationDrawerBackgroundColor,
        NativeThemeEditorTextKey.COLOR_NAVIGATION_DRAWER_BACKGROUND,
    ),
    NAVIGATION_DRAWER_ACCENT(
        NativeThemePreferenceSchemaV1.customNavigationDrawerAccentColor,
        NativeThemeEditorTextKey.COLOR_NAVIGATION_DRAWER_ACCENT,
    ),
    HISTORY_ICON(
        NativeThemePreferenceSchemaV1.chatHeaderHistoryIconColor,
        NativeThemeEditorTextKey.COLOR_HISTORY_ICON,
    ),
    PIP_ICON(
        NativeThemePreferenceSchemaV1.chatHeaderPipIconColor,
        NativeThemeEditorTextKey.COLOR_PIP_ICON,
    ),
    CURSOR_USER_BUBBLE(
        NativeThemePreferenceSchemaV1.cursorUserBubbleColor,
        NativeThemeEditorTextKey.COLOR_CURSOR_USER_BUBBLE,
    ),
    BUBBLE_USER_BUBBLE(
        NativeThemePreferenceSchemaV1.bubbleUserBubbleColor,
        NativeThemeEditorTextKey.COLOR_BUBBLE_USER_BUBBLE,
    ),
    BUBBLE_AI_BUBBLE(
        NativeThemePreferenceSchemaV1.bubbleAiBubbleColor,
        NativeThemeEditorTextKey.COLOR_BUBBLE_AI_BUBBLE,
    ),
    BUBBLE_USER_TEXT(
        NativeThemePreferenceSchemaV1.bubbleUserTextColor,
        NativeThemeEditorTextKey.COLOR_BUBBLE_USER_TEXT,
    ),
    BUBBLE_AI_TEXT(
        NativeThemePreferenceSchemaV1.bubbleAiTextColor,
        NativeThemeEditorTextKey.COLOR_BUBBLE_AI_TEXT,
    ),
}

internal sealed interface NativeThemeEditorPredicateV1 {
    fun matches(values: ThemePreferenceValues): Boolean

    data object Always : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean = true
    }

    data class BooleanEquals(
        val field: NativeThemeBooleanField,
        val expected: Boolean,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean =
            values.requiredBoolean(field) == expected
    }

    data class StringEquals(
        val field: NativeThemeStringField,
        val expected: String,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean =
            values.requiredString(field) == expected
    }

    data class StringPresent(
        val field: NativeThemeStringField,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean =
            values.string(field)?.isNotEmpty() == true
    }

    data class Not(
        val predicate: NativeThemeEditorPredicateV1,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean = !predicate.matches(values)
    }

    data class AllOf(
        val predicates: List<NativeThemeEditorPredicateV1>,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean =
            predicates.all { predicate -> predicate.matches(values) }
    }

    data class AnyOf(
        val predicates: List<NativeThemeEditorPredicateV1>,
    ) : NativeThemeEditorPredicateV1 {
        override fun matches(values: ThemePreferenceValues): Boolean =
            predicates.any { predicate -> predicate.matches(values) }
    }
}

internal enum class NativeThemeChoicePresentation {
    SEGMENTED,
    RADIO,
}

internal data class NativeThemeStringOptionDefinitionV1(
    val value: String,
    val title: NativeThemeEditorTextKey,
)

internal sealed interface NativeThemeEditorItemDefinitionV1 {
    val id: NativeThemeEditorItemId
    val title: NativeThemeEditorTextKey
    val description: NativeThemeEditorTextKey?
    val displayTitle: Boolean
    val visibleWhen: NativeThemeEditorPredicateV1
    val advanced: Boolean

    fun isVisible(values: ThemePreferenceValues): Boolean = visibleWhen.matches(values)

    fun isCustomized(values: ThemePreferenceValues): Boolean
}

internal sealed interface NativeThemeEditorValueChangeV1 {
    data class BooleanChanged(
        val definition: NativeThemeBooleanControlDefinitionV1,
        val value: Boolean,
    ) : NativeThemeEditorValueChangeV1

    data class StringChanged(
        val definition: NativeThemeStringChoiceDefinitionV1,
        val value: String,
    ) : NativeThemeEditorValueChangeV1

    data class FloatChanged(
        val definition: NativeThemeFloatControlDefinitionV1,
        val value: Float,
        val finished: Boolean,
    ) : NativeThemeEditorValueChangeV1
}

internal data class NativeThemeEditorValueOverridesV1(
    val strings: Map<String, String> = emptyMap(),
    val clearedStrings: Set<String> = emptySet(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
) {
    fun string(field: NativeThemeStringField): String? = strings[field.name]

    fun boolean(field: NativeThemeBooleanField): Boolean? = booleans[field.name]

    fun float(field: NativeThemeFloatField): Float? = floats[field.name]

    fun applyTo(values: ThemePreferenceValues): ThemePreferenceValues =
        values.copy(
            strings = (values.strings - clearedStrings) + strings,
            booleans = values.booleans + booleans,
            floats = values.floats + floats,
        )
}

internal data class NativeThemeStringChoiceDefinitionV1(
    override val id: NativeThemeEditorItemId,
    override val title: NativeThemeEditorTextKey,
    override val description: NativeThemeEditorTextKey?,
    val field: NativeThemeStringField,
    val options: List<NativeThemeStringOptionDefinitionV1>,
    val presentation: NativeThemeChoicePresentation,
    override val displayTitle: Boolean = true,
    override val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
    override val advanced: Boolean = false,
) : NativeThemeEditorItemDefinitionV1 {
    override fun isCustomized(values: ThemePreferenceValues): Boolean =
        values.string(field) != field.defaultValue
}

internal data class NativeThemeBooleanControlDefinitionV1(
    override val id: NativeThemeEditorItemId,
    override val title: NativeThemeEditorTextKey,
    override val description: NativeThemeEditorTextKey?,
    val field: NativeThemeBooleanField,
    val enabledIntValues: List<NativeThemeIntValueV1> = emptyList(),
    override val displayTitle: Boolean = true,
    override val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
    override val advanced: Boolean = false,
) : NativeThemeEditorItemDefinitionV1 {
    override fun isCustomized(values: ThemePreferenceValues): Boolean =
        values.requiredBoolean(field) != field.defaultValue
}

internal data class NativeThemeIntValueV1(
    val field: NativeThemeIntField,
    val value: Int,
)

internal fun applyNativeThemeBooleanControlV1(
    values: ThemePreferenceValues,
    definition: NativeThemeBooleanControlDefinitionV1,
    checked: Boolean,
): ThemePreferenceValues {
    var updated =
        NativeThemePreferenceRulesV1.applyBooleanChange(values, definition.field, checked)
    if (checked) {
        definition.enabledIntValues.forEach { intValue ->
            if (updated.int(intValue.field) == null) {
                updated = updated.withInt(intValue.field, intValue.value)
            }
        }
    }
    return updated
}

internal data class NativeThemeColorControlDefinitionV1(
    override val id: NativeThemeEditorItemId,
    override val title: NativeThemeEditorTextKey,
    override val description: NativeThemeEditorTextKey?,
    val target: NativeThemeColorTargetV1,
    val displayDefault: Int,
    override val displayTitle: Boolean = true,
    override val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
    override val advanced: Boolean = false,
) : NativeThemeEditorItemDefinitionV1 {
    override fun isCustomized(values: ThemePreferenceValues): Boolean =
        values.int(target.field) != target.field.defaultValue
}

internal enum class NativeThemeFloatFormatV1 {
    DECIMAL_ONE,
    DECIMAL_UP_TO_TWO,
    INTEGER,
    PERCENT_INTEGER,
}

internal enum class NativeThemeFloatCommitPolicyV1 {
    IMMEDIATE,
    ON_VALUE_CHANGE_FINISHED,
}

internal data class NativeThemeFloatControlDefinitionV1(
    override val id: NativeThemeEditorItemId,
    override val title: NativeThemeEditorTextKey,
    override val description: NativeThemeEditorTextKey?,
    val field: NativeThemeFloatField,
    val minimum: Float,
    val maximum: Float,
    val steps: Int,
    val format: NativeThemeFloatFormatV1,
    val commitPolicy: NativeThemeFloatCommitPolicyV1 =
        NativeThemeFloatCommitPolicyV1.IMMEDIATE,
    override val displayTitle: Boolean = true,
    override val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
    override val advanced: Boolean = false,
) : NativeThemeEditorItemDefinitionV1 {
    override fun isCustomized(values: ThemePreferenceValues): Boolean =
        values.requiredFloat(field) != field.defaultValue
}

internal enum class NativeThemeAssetActionV1(
    val field: NativeThemeStringField,
) {
    APP_FONT(NativeThemePreferenceSchemaV1.customFontPath),
    BACKGROUND_MEDIA(NativeThemePreferenceSchemaV1.backgroundImageUri),
}

internal data class NativeThemeAssetControlDefinitionV1(
    override val id: NativeThemeEditorItemId,
    override val title: NativeThemeEditorTextKey,
    override val description: NativeThemeEditorTextKey?,
    val action: NativeThemeAssetActionV1,
    val selectLabel: NativeThemeEditorTextKey,
    val clearLabel: NativeThemeEditorTextKey? = null,
    val currentValueLabel: NativeThemeEditorTextKey? = null,
    val selectionField: NativeThemeStringField? = null,
    val selectLabelsByStringValue: Map<String, NativeThemeEditorTextKey> = emptyMap(),
    override val displayTitle: Boolean = true,
    override val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
    override val advanced: Boolean = false,
) : NativeThemeEditorItemDefinitionV1 {
    val field: NativeThemeStringField
        get() = action.field

    override fun isCustomized(values: ThemePreferenceValues): Boolean =
        values.string(field) != field.defaultValue

    fun selectLabel(values: ThemePreferenceValues): NativeThemeEditorTextKey =
        selectionField?.let { field -> values.string(field) }
            ?.let { value -> selectLabelsByStringValue[value] }
            ?: selectLabel
}

internal data class NativeThemeEditorGroupDefinitionV1(
    val id: NativeThemeEditorGroupId,
    val title: NativeThemeEditorTextKey,
    val description: NativeThemeEditorTextKey? = null,
    val items: List<NativeThemeEditorItemDefinitionV1>,
    val visibleWhen: NativeThemeEditorPredicateV1 = NativeThemeEditorPredicateV1.Always,
) {
    fun isVisible(values: ThemePreferenceValues): Boolean = visibleWhen.matches(values)

    fun visibleItems(values: ThemePreferenceValues): List<NativeThemeEditorItemDefinitionV1> =
        items.filter { item -> item.isVisible(values) }
}

internal data class NativeThemeEditorSectionDefinitionV1(
    val id: NativeThemeEditorSectionId,
    val title: NativeThemeEditorTextKey,
    val groups: List<NativeThemeEditorGroupDefinitionV1>,
)
