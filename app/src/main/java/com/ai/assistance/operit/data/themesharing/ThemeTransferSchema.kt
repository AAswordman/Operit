package com.ai.assistance.operit.data.themesharing

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.layout.ChatLayoutSettings
import kotlinx.serialization.json.*
import com.ai.assistance.operit.data.model.theme.ThemeScriptContract
import com.ai.assistance.operit.data.model.theme.ThemeScriptManifest

/** 分享采用固定字段白名单，新格式用独立数字校验结构，并显式读取已发布格式。 */
internal object ThemeTransferSchema {
    const val PARAMS_FORMAT = "oktheme-params"
    const val PACKAGE_FORMAT = "oktheme"
    private const val SCHEMA = 1
    private const val LEGACY_PARAMS_FORMAT = "operit-theme-params-v1"
    private const val LEGACY_PACKAGE_FORMAT = "operit-theme-v1"
    const val MAX_JSON_BYTES = 65536
    const val MAX_PACKAGE_JSON_BYTES = 196608
    val assetKeys =
        setOf(
            "background_image_uri",
            "custom_user_avatar_uri",
            "custom_ai_avatar_uri",
            "bubble_user_image_uri",
            "bubble_ai_image_uri",
            "custom_font_path",
            "bubble_user_custom_font_path",
            "bubble_ai_custom_font_path",
        )
    val stringKeys =
        setOf(
            "avatar_shape",
            "background_image_uri",
            "background_media_type",
            "bubble_ai_custom_font_path",
            "bubble_ai_font_type",
            "bubble_ai_image_uri",
            "bubble_ai_system_font_name",
            "bubble_image_render_mode",
            "bubble_user_custom_font_path",
            "bubble_user_font_type",
            "bubble_user_image_uri",
            "bubble_user_system_font_name",
            "chat_style",
            "user_chat_style",
            "ai_chat_style",
            "custom_ai_avatar_uri",
            "custom_font_path",
            "custom_user_avatar_uri",
            "font_type",
            "input_style",
            "on_color_mode",
            "system_font_name",
            "theme_mode",
        )
    val booleanKeys =
        setOf(
            "bubble_ai_bubble_liquid_glass",
            "bubble_ai_bubble_water_glass",
            "bubble_ai_rounded_corners_enabled",
            "bubble_ai_use_custom_font",
            "bubble_ai_use_image",
            "bubble_rounded_corners_enabled",
            "bubble_show_avatar",
            "bubble_user_bubble_liquid_glass",
            "bubble_user_bubble_water_glass",
            "bubble_user_use_custom_font",
            "bubble_user_use_image",
            "bubble_wide_layout_enabled",
            "chat_header_overlay_mode",
            "chat_header_transparent",
            "chat_input_floating",
            "chat_input_liquid_glass",
            "chat_input_transparent",
            "chat_input_water_glass",
            "cursor_user_bubble_follow_theme",
            "cursor_user_bubble_liquid_glass",
            "cursor_user_bubble_water_glass",
            "glass_ai_message_custom_parameters",
            "glass_conversation_list",
            "glass_conversation_region_custom_parameters",
            "glass_input_custom_parameters",
            "glass_package_cards",
            "glass_package_region_custom_parameters",
            "glass_settings_cards",
            "glass_settings_region_custom_parameters",
            "glass_sidebar_buttons_custom_parameters",
            "glass_sidebar_custom_parameters",
            "glass_user_message_custom_parameters",
            "navigation_drawer_button_liquid_glass",
            "navigation_drawer_water_glass",
            "show_chat_floating_dots_animation",
            "show_input_processing_status",
            "show_message_timestamp",
            "show_message_timing_stats",
            "show_message_token_speed",
            "show_message_token_stats",
            "show_model_name",
            "show_model_provider",
            "show_role_name",
            "show_status_tags",
            "show_thinking_process",
            "show_user_name",
            "use_background_blur",
            "use_background_image",
            "use_custom_colors",
            "use_custom_font",
            "use_custom_navigation_drawer_accent_color",
            "use_custom_navigation_drawer_background_color",
            "use_system_theme",
            "video_background_loop",
            "video_background_muted",
        )
    val intKeys =
        setOf(
            "bubble_ai_bubble_color",
            "bubble_ai_text_color",
            "bubble_user_bubble_color",
            "bubble_user_text_color",
            "cursor_user_bubble_color",
            "custom_navigation_drawer_accent_color",
            "custom_navigation_drawer_background_color",
            "custom_primary_color",
            "custom_secondary_color",
            "package_add_button_color",
            "package_market_button_color",
            "thinking_particle_color",
        )
    val floatKeys =
        setOf(
            "avatar_corner_radius",
            "background_blur_radius",
            "background_image_opacity",
            "bubble_ai_bubble_opacity",
            "bubble_ai_content_padding_left",
            "bubble_ai_content_padding_right",
            "bubble_ai_image_crop_bottom",
            "bubble_ai_image_crop_left",
            "bubble_ai_image_crop_right",
            "bubble_ai_image_crop_top",
            "bubble_ai_image_repeat_end",
            "bubble_ai_image_repeat_start",
            "bubble_ai_image_repeat_y_end",
            "bubble_ai_image_repeat_y_start",
            "bubble_ai_image_scale",
            "bubble_content_padding_left",
            "bubble_content_padding_right",
            "bubble_user_bubble_opacity",
            "bubble_user_image_crop_bottom",
            "bubble_user_image_crop_left",
            "bubble_user_image_crop_right",
            "bubble_user_image_crop_top",
            "bubble_user_image_repeat_end",
            "bubble_user_image_repeat_start",
            "bubble_user_image_repeat_y_end",
            "bubble_user_image_repeat_y_start",
            "bubble_user_image_scale",
            "chat_input_opacity",
            "cursor_ai_bubble_opacity",
            "cursor_user_bubble_opacity",
            "font_scale",
            "glass_ai_message_blur_radius",
            "glass_ai_message_edge_width",
            "glass_ai_message_highlight_alpha",
            "glass_ai_message_shadow_radius",
            "glass_ai_message_tint_opacity",
            "glass_blur_radius",
            "glass_conversation_region_blur_radius",
            "glass_conversation_region_edge_width",
            "glass_conversation_region_highlight_alpha",
            "glass_conversation_region_shadow_radius",
            "glass_conversation_region_tint_opacity",
            "glass_edge_width",
            "glass_highlight_alpha",
            "glass_input_blur_radius",
            "glass_input_edge_width",
            "glass_input_highlight_alpha",
            "glass_input_shadow_radius",
            "glass_input_tint_opacity",
            "glass_package_region_blur_radius",
            "glass_package_region_edge_width",
            "glass_package_region_highlight_alpha",
            "glass_package_region_shadow_radius",
            "glass_package_region_tint_opacity",
            "glass_settings_region_blur_radius",
            "glass_settings_region_edge_width",
            "glass_settings_region_highlight_alpha",
            "glass_settings_region_shadow_radius",
            "glass_settings_region_tint_opacity",
            "glass_shadow_radius",
            "glass_sidebar_blur_radius",
            "glass_sidebar_buttons_blur_radius",
            "glass_sidebar_buttons_edge_width",
            "glass_sidebar_buttons_highlight_alpha",
            "glass_sidebar_buttons_shadow_radius",
            "glass_sidebar_buttons_tint_opacity",
            "glass_sidebar_edge_width",
            "glass_sidebar_highlight_alpha",
            "glass_sidebar_shadow_radius",
            "glass_sidebar_tint_opacity",
            "glass_tint_opacity",
            "glass_user_message_blur_radius",
            "glass_user_message_edge_width",
            "glass_user_message_highlight_alpha",
            "glass_user_message_shadow_radius",
            "glass_user_message_tint_opacity",
        )
    private val mediaKeys =
        setOf(
            "background_blur_radius",
            "background_image_opacity",
            "background_image_uri",
            "background_media_type",
            "bubble_ai_custom_font_path",
            "bubble_ai_image_crop_bottom",
            "bubble_ai_image_crop_left",
            "bubble_ai_image_crop_right",
            "bubble_ai_image_crop_top",
            "bubble_ai_image_repeat_end",
            "bubble_ai_image_repeat_start",
            "bubble_ai_image_repeat_y_end",
            "bubble_ai_image_repeat_y_start",
            "bubble_ai_image_scale",
            "bubble_ai_image_uri",
            "bubble_ai_use_image",
            "bubble_image_render_mode",
            "bubble_user_custom_font_path",
            "bubble_user_image_crop_bottom",
            "bubble_user_image_crop_left",
            "bubble_user_image_crop_right",
            "bubble_user_image_crop_top",
            "bubble_user_image_repeat_end",
            "bubble_user_image_repeat_start",
            "bubble_user_image_repeat_y_end",
            "bubble_user_image_repeat_y_start",
            "bubble_user_image_scale",
            "bubble_user_image_uri",
            "bubble_user_use_image",
            "custom_ai_avatar_uri",
            "custom_font_path",
            "custom_user_avatar_uri",
            "use_background_blur",
            "use_background_image",
            "video_background_loop",
            "video_background_muted",
        )
    val allKeys = stringKeys + booleanKeys + intKeys + floatKeys
    val ordinaryKeys = allKeys - mediaKeys
    private val fontPrefixes = listOf("", "bubble_user_", "bubble_ai_")
    private val layoutKeys =
        setOf(
            "chat_settings_button_end_padding",
            "chat_area_horizontal_padding",
            "global_text_line_height_multiplier",
            "global_text_letter_spacing",
            "ai_markdown_paragraph_spacing",
        )

    private fun fontKeys(prefix: String) =
        setOf(prefix + "font_type", prefix + "system_font_name", prefix + "use_custom_font")

    fun create(
        name: String,
        author: String,
        values: ThemePreferenceValues,
        layout: ChatLayoutSettings,
        full: Boolean,
        script: ThemeScriptManifest? = null,
    ): ThemeTransferDocument {
        checkValue(script == null || full)
        script?.let(ThemeScriptContract::validate)
        var fields = if (full) allKeys else ordinaryKeys
        if (!full) {
            // 文件字体的选择属于媒体设置，分享码不携带它的启用状态。
            fontPrefixes
                .filter { values.string(it + "font_type") == "file" }
                .forEach { fields = fields - fontKeys(it) }
        }
        val document =
            ThemeTransferDocument(name.trim(), author.trim(), values, fields, layout, full, script)
        return decode(encode(document), full)
    }

    fun encode(document: ThemeTransferDocument): ByteArray {
        val values = document.values
        val config =
            JsonObject(
                document.fields.sorted().associateWith { key ->
                    when (key) {
                        in stringKeys -> values.string(key)?.let(::JsonPrimitive) ?: JsonNull
                        in booleanKeys -> values.boolean(key)?.let(::JsonPrimitive) ?: JsonNull
                        in intKeys -> values.int(key)?.let(::JsonPrimitive) ?: JsonNull
                        in floatKeys -> values.float(key)?.let(::JsonPrimitive) ?: JsonNull
                        else -> throw ThemeTransferException(ThemeTransferError.INVALID_CONFIG)
                    }
                }
            )
        val layout = document.layout
        return buildJsonObject {
                put("format", if (document.full) PACKAGE_FORMAT else PARAMS_FORMAT)
                put("schema", SCHEMA)
                document.script?.let {
                    checkValue(document.full)
                    ThemeScriptContract.validate(it)
                    put("script", ThemeScriptContract.json.encodeToJsonElement(ThemeScriptManifest.serializer(), it))
                }
                put("name", document.name)
                put("author", document.author)
                put("config", config)
                put(
                    "layout",
                    buildJsonObject {
                        put("chat_settings_button_end_padding", layout.chatSettingsButtonEndPadding)
                        put("chat_area_horizontal_padding", layout.chatAreaHorizontalPadding)
                        put(
                            "global_text_line_height_multiplier",
                            layout.aiMarkdownLineHeightMultiplier,
                        )
                        put("global_text_letter_spacing", layout.aiMarkdownLetterSpacing)
                        put("ai_markdown_paragraph_spacing", layout.aiMarkdownParagraphSpacing)
                    },
                )
            }
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, full: Boolean): ThemeTransferDocument {
        val limit = if (full) MAX_PACKAGE_JSON_BYTES else MAX_JSON_BYTES
        if (bytes.size > limit) throw ThemeTransferException(ThemeTransferError.TOO_LARGE)
        try {
            checkJsonDepth(bytes)
            val root =
                Json.parseToJsonElement(
                    Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                ) as? JsonObject ?: invalid()
            val format = text(root.getValue("format"))
            val current = format == if (full) PACKAGE_FORMAT else PARAMS_FORMAT
            val legacy = format == if (full) LEGACY_PACKAGE_FORMAT else LEGACY_PARAMS_FORMAT
            if (!current && !legacy) throw ThemeTransferException(ThemeTransferError.UNSUPPORTED_VERSION)
            if (current) {
                val schema = root["schema"] as? JsonPrimitive ?: invalid()
                checkValue(!schema.isString)
                if (schema.intOrNull != SCHEMA) throw ThemeTransferException(ThemeTransferError.UNSUPPORTED_VERSION)
            }
            val hasScript = current && full && "script" in root
            val required = setOf("format", "name", "author", "config", "layout") +
                (if (current) setOf("schema") else emptySet()) + (if (hasScript) setOf("script") else emptySet())
            checkValue(root.keys == required)
            val script = if (hasScript) {
                ThemeScriptContract.json.decodeFromJsonElement(ThemeScriptManifest.serializer(), root.getValue("script"))
                    .also(ThemeScriptContract::validate)
            } else null
            val name = text(root.getValue("name"))
            val author = text(root.getValue("author"))
            checkValue(name.isNotBlank() && name.length <= 80 && author.length <= 80)
            checkValue((name + author).none { it.isISOControl() })
            val config = root.getValue("config") as? JsonObject ?: invalid()
            val allowed = if (full) allKeys else ordinaryKeys
            checkValue(config.keys.all { it in allowed })
            val fontFields = fontPrefixes.flatMap { fontKeys(it) }.toSet()
            // 旧版分享仍只有 chat_style；新增分侧字段在导入时从旧值明确迁移。
            val legacyRequired = allowed - com.ai.assistance.operit.data.preferences.messagestyle.MessageStylePreferences.sideKeys
            checkValue(config.keys.containsAll(if (full) legacyRequired else legacyRequired - fontFields))
            val strings = mutableMapOf<String, String>()
            val booleans = mutableMapOf<String, Boolean>()
            val ints = mutableMapOf<String, Int>()
            val floats = mutableMapOf<String, Float>()
            config.forEach { (key, element) ->
                if (element == JsonNull) {
                    checkValue(
                        key in assetKeys ||
                            key in intKeys ||
                            key.endsWith("_opacity") &&
                                key !in mediaKeys &&
                                !key.startsWith("glass_")
                    )
                } else {
                    val primitive = element as? JsonPrimitive ?: invalid()
                    when (key) {
                        in stringKeys -> {
                            val value = text(primitive)
                            validateString(key, value, full)
                            strings[key] = value
                        }
                        in booleanKeys -> {
                            checkValue(!primitive.isString)
                            booleans[key] = primitive.booleanOrNull ?: invalid()
                        }
                        in intKeys -> {
                            checkValue(!primitive.isString)
                            ints[key] = primitive.intOrNull ?: invalid()
                        }
                        in floatKeys -> {
                            checkValue(!primitive.isString)
                            val value = primitive.floatOrNull ?: invalid()
                            checkValue(value.isFinite() && value in range(key))
                            floats[key] = value
                        }
                    }
                }
            }
            val values = ThemePreferenceValues(
                com.ai.assistance.operit.data.preferences.messagestyle.MessageStylePreferences.expandLegacy(strings),
                booleans, ints, floats,
            )
            fontPrefixes.forEach { prefix ->
                val fields = fontKeys(prefix)
                checkValue(
                    full || config.keys.intersect(fields).let { it.isEmpty() || it == fields }
                )
            }
            if (full) {
                listOf("bubble_user_", "bubble_ai_").forEach { prefix ->
                    checkValue(
                        floats.getValue(prefix + "image_repeat_start") <
                            floats.getValue(prefix + "image_repeat_end")
                    )
                    checkValue(
                        floats.getValue(prefix + "image_repeat_y_start") <
                            floats.getValue(prefix + "image_repeat_y_end")
                    )
                }
            }
            val layout = root.getValue("layout") as? JsonObject ?: invalid()
            checkValue(layout.keys == layoutKeys)
            fun layoutValue(key: String): Float {
                val value = layout.getValue(key) as? JsonPrimitive ?: invalid()
                checkValue(!value.isString)
                return value.floatOrNull ?: invalid()
            }
            val settings =
                ChatLayoutSettings(
                    layoutValue("chat_settings_button_end_padding"),
                    layoutValue("chat_area_horizontal_padding"),
                    layoutValue("global_text_line_height_multiplier"),
                    layoutValue("global_text_letter_spacing"),
                    layoutValue("ai_markdown_paragraph_spacing"),
                )
            settings.validate()
            return ThemeTransferDocument(
                name, author, values,
                config.keys + com.ai.assistance.operit.data.preferences.messagestyle.MessageStylePreferences.sideKeys,
                settings, full, script,
            )
        } catch (e: ThemeTransferException) {
            throw e
        } catch (e: Exception) {
            throw ThemeTransferException(ThemeTransferError.INVALID_CONFIG, e)
        }
    }

    fun merge(
        current: ThemePreferenceValues,
        document: ThemeTransferDocument,
    ): ThemePreferenceValues {
        if (document.full)
            return document.values.withString(
                "custom_chat_title",
                current.string("custom_chat_title"),
            )
        var fields = document.fields
        fontPrefixes
            .filter { current.string(it + "font_type") == "file" }
            .forEach { fields = fields - fontKeys(it) }
        val incoming = document.values
        return ThemePreferenceValues(
            current.strings.filterKeys { it !in fields } +
                incoming.strings.filterKeys { it in fields },
            current.booleans.filterKeys { it !in fields } +
                incoming.booleans.filterKeys { it in fields },
            current.ints.filterKeys { it !in fields } + incoming.ints.filterKeys { it in fields },
            current.floats.filterKeys { it !in fields } +
                incoming.floats.filterKeys { it in fields },
        )
    }

    fun isDefaultVisualTheme(values: ThemePreferenceValues): Boolean {
        val ignored = setOf("custom_ai_avatar_uri", "custom_chat_title")
        val defaults = ThemePreferenceValues.defaultVisual()
        return values.strings.filterKeys { it !in ignored } == defaults.strings &&
            values.booleans == defaults.booleans &&
            values.ints == defaults.ints &&
            values.floats.filterKeys { it !in opacityKeys } == defaults.floats &&
            opacityKeys.all { key ->
                val opacity = values.float(key)
                opacity == null || opacity == defaultOpacity(key, values)
            }
    }

    private val opacityKeys =
        setOf(
            "chat_input_opacity",
            "cursor_user_bubble_opacity",
            "cursor_ai_bubble_opacity",
            "bubble_user_bubble_opacity",
            "bubble_ai_bubble_opacity",
        )

    private fun defaultOpacity(key: String, values: ThemePreferenceValues): Float =
        when (key) {
            "chat_input_opacity" -> if (values.boolean("chat_input_transparent") == true) 0f else 1f
            "cursor_ai_bubble_opacity" -> 0f
            else -> 1f
        }

    private fun validateString(key: String, value: String, full: Boolean) {
        val allowed =
            when (key) {
                "theme_mode" -> setOf("light", "dark")
                "chat_style", "user_chat_style", "ai_chat_style" -> setOf("cursor", "bubble")
                "avatar_shape" -> setOf("circle", "square")
                "on_color_mode" -> setOf("auto", "light", "dark")
                "input_style" -> setOf("classic", "agent")
                "background_media_type" -> setOf("image", "video")
                "bubble_image_render_mode" -> setOf("tiled_nine_slice", "nine_patch")
                else ->
                    when {
                        key in assetKeys -> {
                            checkValue(full && ThemeArchive.isAssetPath(value))
                            return
                        }
                        key.endsWith("font_type") ->
                            if (full) setOf("system", "file") else setOf("system")
                        key.endsWith("system_font_name") ->
                            setOf("default", "serif", "sans-serif", "monospace", "cursive")
                        else -> invalid()
                    }
            }
        checkValue(value in allowed)
    }

    private fun range(key: String): ClosedFloatingPointRange<Float> =
        when {
            key == "font_scale" -> 0.8f..1.5f
            key == "avatar_corner_radius" -> 0f..16f
            key == "background_image_opacity" || key in opacityKeys -> 0f..1f
            key == "background_blur_radius" -> 0f..30f
            key.endsWith("image_scale") -> 0.2f..3f
            "image_crop_" in key -> 0f..0.45f
            "image_repeat_" in key -> 0f..1f
            "content_padding_" in key -> 0f..96f
            key.endsWith("blur_radius") -> 0f..40f
            key.endsWith("edge_width") -> 0f..2f
            key.endsWith("highlight_alpha") -> 0f..0.8f
            key.endsWith("shadow_radius") -> 0f..20f
            key.endsWith("tint_opacity") -> 0f..1f
            else -> invalid()
        }

    // 协议只有两层对象，限制嵌套深度以防止损坏配置耗尽解析栈。
    private fun checkJsonDepth(bytes: ByteArray) {
        var depth = 0
        var quoted = false
        var escaped = false
        bytes.forEach { byte ->
            val character = byte.toInt().toChar()
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character.code == 92 -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{',
                    '[' -> {
                        depth++
                        checkValue(depth <= 8)
                    }
                    '}',
                    ']' -> {
                        depth--
                        checkValue(depth >= 0)
                    }
                }
            }
        }
    }

    private fun text(element: JsonElement): String {
        val primitive = element as? JsonPrimitive ?: invalid()
        checkValue(primitive.isString)
        return primitive.content
    }

    private fun checkValue(valid: Boolean) {
        if (!valid) invalid()
    }

    private fun invalid(): Nothing = throw ThemeTransferException(ThemeTransferError.INVALID_CONFIG)
}
