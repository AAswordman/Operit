package com.ai.assistance.operit.data.preferences

data class ThemePreferenceValues(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
) {
    fun string(name: String): String? = strings[name]

    fun boolean(name: String): Boolean? = booleans[name]

    fun int(name: String): Int? = ints[name]

    fun float(name: String): Float? = floats[name]

    internal fun string(field: NativeThemeStringField): String? = string(field.name)

    internal fun boolean(field: NativeThemeBooleanField): Boolean? = boolean(field.name)

    internal fun int(field: NativeThemeIntField): Int? = int(field.name)

    internal fun float(field: NativeThemeFloatField): Float? = float(field.name)

    fun requiredString(name: String): String = requireNotNull(string(name))

    fun requiredBoolean(name: String): Boolean = requireNotNull(boolean(name))

    fun requiredFloat(name: String): Float = requireNotNull(float(name))

    internal fun requiredString(field: NativeThemeStringField): String =
        requireNotNull(string(field))

    internal fun requiredBoolean(field: NativeThemeBooleanField): Boolean =
        requireNotNull(boolean(field))

    internal fun requiredFloat(field: NativeThemeFloatField): Float =
        requireNotNull(float(field))

    fun withString(name: String, value: String?): ThemePreferenceValues {
        val updated = strings.toMutableMap()
        if (value == null) {
            updated.remove(name)
        } else {
            updated[name] = value
        }
        return copy(strings = updated)
    }

    internal fun withString(
        field: NativeThemeStringField,
        value: String?,
    ): ThemePreferenceValues = withString(field.name, value)

    fun withBoolean(name: String, value: Boolean): ThemePreferenceValues =
        copy(booleans = booleans + (name to value))

    internal fun withBoolean(
        field: NativeThemeBooleanField,
        value: Boolean,
    ): ThemePreferenceValues = withBoolean(field.name, value)

    fun withInt(name: String, value: Int?): ThemePreferenceValues {
        val updated = ints.toMutableMap()
        if (value == null) {
            updated.remove(name)
        } else {
            updated[name] = value
        }
        return copy(ints = updated)
    }

    internal fun withInt(
        field: NativeThemeIntField,
        value: Int?,
    ): ThemePreferenceValues = withInt(field.name, value)

    fun withFloat(name: String, value: Float): ThemePreferenceValues =
        copy(floats = floats + (name to value))

    internal fun withFloat(
        field: NativeThemeFloatField,
        value: Float,
    ): ThemePreferenceValues = withFloat(field.name, value)

    companion object {
        fun defaultVisual(): ThemePreferenceValues =
            ThemePreferenceValues(
                strings = NativeThemePreferenceSchemaV1.defaultStrings.toMap(),
                booleans = NativeThemePreferenceSchemaV1.defaultBooleans.toMap(),
                ints = NativeThemePreferenceSchemaV1.defaultInts.toMap(),
                floats = NativeThemePreferenceSchemaV1.defaultFloats.toMap(),
            )
    }
}

data class ThemePreferenceSnapshot(
    val source: String,
    val sourceId: String? = null,
    val values: ThemePreferenceValues,
) {
    val themeMode: String get() = values.requiredString(schema.themeMode)
    val useSystemTheme: Boolean get() = values.requiredBoolean(schema.useSystemTheme)
    val useCustomColors: Boolean get() = values.requiredBoolean(schema.useCustomColors)
    val customPrimaryColor: Int? get() = values.int(schema.customPrimaryColor)
    val customSecondaryColor: Int? get() = values.int(schema.customSecondaryColor)
    val onColorMode: String get() = values.requiredString(schema.onColorMode)
    val useBackgroundImage: Boolean get() = values.requiredBoolean(schema.useBackgroundImage)
    val backgroundImageUri: String? get() = values.string(schema.backgroundImageUri)
    val backgroundMediaType: String get() = values.requiredString(schema.backgroundMediaType)
    val backgroundImageOpacity: Float get() = values.requiredFloat(schema.backgroundImageOpacity)
    val videoBackgroundMuted: Boolean get() = values.requiredBoolean(schema.videoBackgroundMuted)
    val videoBackgroundLoop: Boolean get() = values.requiredBoolean(schema.videoBackgroundLoop)
    val toolbarTransparent: Boolean get() = values.requiredBoolean(schema.toolbarTransparent)
    val navigationDrawerWaterGlass: Boolean
        get() = values.requiredBoolean(schema.navigationDrawerWaterGlass)
    val navigationDrawerButtonLiquidGlass: Boolean
        get() = values.requiredBoolean(schema.navigationDrawerButtonLiquidGlass)
    val useCustomNavigationDrawerBackgroundColor: Boolean
        get() = values.requiredBoolean(schema.useCustomNavigationDrawerBackgroundColor)
    val customNavigationDrawerBackgroundColor: Int?
        get() = values.int(schema.customNavigationDrawerBackgroundColor)
    val useCustomNavigationDrawerAccentColor: Boolean
        get() = values.requiredBoolean(schema.useCustomNavigationDrawerAccentColor)
    val customNavigationDrawerAccentColor: Int?
        get() = values.int(schema.customNavigationDrawerAccentColor)
    val useCustomAppBarColor: Boolean get() = values.requiredBoolean(schema.useCustomAppBarColor)
    val customAppBarColor: Int? get() = values.int(schema.customAppBarColor)
    val useCustomStatusBarColor: Boolean
        get() = values.requiredBoolean(schema.useCustomStatusBarColor)
    val customStatusBarColor: Int? get() = values.int(schema.customStatusBarColor)
    val statusBarTransparent: Boolean get() = values.requiredBoolean(schema.statusBarTransparent)
    val statusBarHidden: Boolean get() = values.requiredBoolean(schema.statusBarHidden)
    val chatHeaderTransparent: Boolean get() = values.requiredBoolean(schema.chatHeaderTransparent)
    val chatHeaderOverlayMode: Boolean get() = values.requiredBoolean(schema.chatHeaderOverlayMode)
    val chatHeaderHistoryIconColor: Int? get() = values.int(schema.chatHeaderHistoryIconColor)
    val chatHeaderPipIconColor: Int? get() = values.int(schema.chatHeaderPipIconColor)
    val chatInputTransparent: Boolean get() = values.requiredBoolean(schema.chatInputTransparent)
    val chatInputFloating: Boolean get() = values.requiredBoolean(schema.chatInputFloating)
    val chatInputLiquidGlass: Boolean get() = values.requiredBoolean(schema.chatInputLiquidGlass)
    val chatInputWaterGlass: Boolean get() = values.requiredBoolean(schema.chatInputWaterGlass)
    val forceAppBarContentColor: Boolean
        get() = values.requiredBoolean(schema.forceAppBarContentColorEnabled)
    val appBarContentColorMode: String get() = values.requiredString(schema.appBarContentColorMode)
    val useBackgroundBlur: Boolean get() = values.requiredBoolean(schema.useBackgroundBlur)
    val backgroundBlurRadius: Float get() = values.requiredFloat(schema.backgroundBlurRadius)
    val chatStyle: String get() = values.requiredString(schema.chatStyle)
    val inputStyle: String get() = values.requiredString(schema.inputStyle)
    val bubbleShowAvatar: Boolean get() = values.requiredBoolean(schema.bubbleShowAvatar)
    val bubbleWideLayoutEnabled: Boolean get() = values.requiredBoolean(schema.bubbleWideLayoutEnabled)
    val cursorUserBubbleFollowTheme: Boolean
        get() = values.requiredBoolean(schema.cursorUserBubbleFollowTheme)
    val cursorUserBubbleColor: Int? get() = values.int(schema.cursorUserBubbleColor)
    val bubbleUserBubbleColor: Int? get() = values.int(schema.bubbleUserBubbleColor)
    val bubbleAiBubbleColor: Int? get() = values.int(schema.bubbleAiBubbleColor)
    val bubbleUserTextColor: Int? get() = values.int(schema.bubbleUserTextColor)
    val bubbleAiTextColor: Int? get() = values.int(schema.bubbleAiTextColor)
    val bubbleUserUseImage: Boolean get() = values.requiredBoolean(schema.bubbleUserUseImage)
    val bubbleAiUseImage: Boolean get() = values.requiredBoolean(schema.bubbleAiUseImage)
    val bubbleUserImageUri: String? get() = values.string(schema.bubbleUserImageUri)
    val bubbleAiImageUri: String? get() = values.string(schema.bubbleAiImageUri)
    val bubbleUserImageCropLeft: Float get() = values.requiredFloat(schema.bubbleUserImageCropLeft)
    val bubbleUserImageCropTop: Float get() = values.requiredFloat(schema.bubbleUserImageCropTop)
    val bubbleUserImageCropRight: Float get() = values.requiredFloat(schema.bubbleUserImageCropRight)
    val bubbleUserImageCropBottom: Float get() = values.requiredFloat(schema.bubbleUserImageCropBottom)
    val bubbleUserImageRepeatStart: Float
        get() = values.requiredFloat(schema.bubbleUserImageRepeatStart)
    val bubbleUserImageRepeatEnd: Float get() = values.requiredFloat(schema.bubbleUserImageRepeatEnd)
    val bubbleUserImageRepeatYStart: Float
        get() = values.requiredFloat(schema.bubbleUserImageRepeatYStart)
    val bubbleUserImageRepeatYEnd: Float
        get() = values.requiredFloat(schema.bubbleUserImageRepeatYEnd)
    val bubbleUserImageScale: Float get() = values.requiredFloat(schema.bubbleUserImageScale)
    val bubbleAiImageCropLeft: Float get() = values.requiredFloat(schema.bubbleAiImageCropLeft)
    val bubbleAiImageCropTop: Float get() = values.requiredFloat(schema.bubbleAiImageCropTop)
    val bubbleAiImageCropRight: Float get() = values.requiredFloat(schema.bubbleAiImageCropRight)
    val bubbleAiImageCropBottom: Float get() = values.requiredFloat(schema.bubbleAiImageCropBottom)
    val bubbleAiImageRepeatStart: Float get() = values.requiredFloat(schema.bubbleAiImageRepeatStart)
    val bubbleAiImageRepeatEnd: Float get() = values.requiredFloat(schema.bubbleAiImageRepeatEnd)
    val bubbleAiImageRepeatYStart: Float
        get() = values.requiredFloat(schema.bubbleAiImageRepeatYStart)
    val bubbleAiImageRepeatYEnd: Float get() = values.requiredFloat(schema.bubbleAiImageRepeatYEnd)
    val bubbleAiImageScale: Float get() = values.requiredFloat(schema.bubbleAiImageScale)
    val bubbleImageRenderMode: String get() = values.requiredString(schema.bubbleImageRenderMode)
    val bubbleUserRoundedCornersEnabled: Boolean
        get() = values.requiredBoolean(schema.bubbleUserRoundedCornersEnabled)
    val bubbleAiRoundedCornersEnabled: Boolean
        get() = values.requiredBoolean(schema.bubbleAiRoundedCornersEnabled)
    val bubbleUserContentPaddingLeft: Float
        get() = values.requiredFloat(schema.bubbleUserContentPaddingLeft)
    val bubbleUserContentPaddingRight: Float
        get() = values.requiredFloat(schema.bubbleUserContentPaddingRight)
    val bubbleAiContentPaddingLeft: Float
        get() = values.requiredFloat(schema.bubbleAiContentPaddingLeft)
    val bubbleAiContentPaddingRight: Float
        get() = values.requiredFloat(schema.bubbleAiContentPaddingRight)
    val customUserAvatarUri: String? get() = values.string(schema.customUserAvatarUri)
    val customAiAvatarUri: String? get() = values.string(schema.customAiAvatarUri)
    val avatarShape: String get() = values.requiredString(schema.avatarShape)
    val avatarCornerRadius: Float get() = values.requiredFloat(schema.avatarCornerRadius)
    val fontType: String get() = values.requiredString(schema.fontType)
    val systemFontName: String get() = values.requiredString(schema.systemFontName)
    val customFontPath: String? get() = values.string(schema.customFontPath)
    val fontScale: Float get() = values.requiredFloat(schema.fontScale)
    val showThinkingProcess: Boolean get() = values.requiredBoolean(schema.showThinkingProcess)
    val showStatusTags: Boolean get() = values.requiredBoolean(schema.showStatusTags)
    val showModelProvider: Boolean get() = values.requiredBoolean(schema.showModelProvider)
    val showModelName: Boolean get() = values.requiredBoolean(schema.showModelName)
    val showRoleName: Boolean get() = values.requiredBoolean(schema.showRoleName)
    val showUserName: Boolean get() = values.requiredBoolean(schema.showUserName)
    val showMessageTokenStats: Boolean get() = values.requiredBoolean(schema.showMessageTokenStats)
    val showMessageTimingStats: Boolean get() = values.requiredBoolean(schema.showMessageTimingStats)
    val showMessageTimestamp: Boolean get() = values.requiredBoolean(schema.showMessageTimestamp)
    val showInputProcessingStatus: Boolean
        get() = values.requiredBoolean(schema.showInputProcessingStatus)
    val useCustomFont: Boolean get() = values.requiredBoolean(schema.useCustomFont)
    val bubbleUserUseCustomFont: Boolean
        get() = values.requiredBoolean(schema.bubbleUserUseCustomFont)
    val bubbleUserFontType: String get() = values.requiredString(schema.bubbleUserFontType)
    val bubbleUserSystemFontName: String
        get() = values.requiredString(schema.bubbleUserSystemFontName)
    val bubbleUserCustomFontPath: String? get() = values.string(schema.bubbleUserCustomFontPath)
    val bubbleAiUseCustomFont: Boolean
        get() = values.requiredBoolean(schema.bubbleAiUseCustomFont)
    val bubbleAiFontType: String get() = values.requiredString(schema.bubbleAiFontType)
    val bubbleAiSystemFontName: String
        get() = values.requiredString(schema.bubbleAiSystemFontName)
    val bubbleAiCustomFontPath: String? get() = values.string(schema.bubbleAiCustomFontPath)
    val cursorUserBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean(schema.cursorUserBubbleLiquidGlass)
    val cursorUserBubbleWaterGlass: Boolean
        get() = values.requiredBoolean(schema.cursorUserBubbleWaterGlass)
    val bubbleUserBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean(schema.bubbleUserBubbleLiquidGlass)
    val bubbleUserBubbleWaterGlass: Boolean
        get() = values.requiredBoolean(schema.bubbleUserBubbleWaterGlass)
    val bubbleAiBubbleLiquidGlass: Boolean
        get() = values.requiredBoolean(schema.bubbleAiBubbleLiquidGlass)
    val bubbleAiBubbleWaterGlass: Boolean
        get() = values.requiredBoolean(schema.bubbleAiBubbleWaterGlass)
    val customChatTitle: String? get() = values.string(schema.customChatTitle)
    val showChatFloatingDotsAnimation: Boolean
        get() = values.requiredBoolean(schema.showChatFloatingDotsAnimation)

    private companion object {
        val schema = NativeThemePreferenceSchemaV1
    }
}
