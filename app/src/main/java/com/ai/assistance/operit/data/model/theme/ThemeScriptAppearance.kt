package com.ai.assistance.operit.data.model.theme

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ThemeScriptAppearance(
    val apiVersion: Int,
    val surfaces: Map<String, ThemeScriptSurface> = emptyMap(),
    val icons: Map<String, ThemeAppearanceIcon> = emptyMap(),
    val palette: ThemeAppearancePalette = ThemeAppearancePalette(),
    val typography: ThemeAppearanceText = ThemeAppearanceText(),
    val modes: Map<String, ThemeAppearanceMode> = emptyMap(),
)

@Serializable
data class ThemeScriptSurface(
    val padding: ThemeScriptInsets = ThemeScriptInsets(),
    val layers: List<ThemeScriptLayer> = emptyList(),
    val shape: ThemeAppearanceShape? = null,
    val fillColor: String? = null,
    val contentColor: String? = null,
    val strokeColor: String? = null,
    val strokeWidth: Float = 0f,
    val elevation: Float? = null,
    val minHeight: Float = 0f,
    val size: Float? = null,
    val messageWidth: Float = 1f,
    val textStyle: ThemeAppearanceText = ThemeAppearanceText(),
    val states: Map<String, ThemeAppearanceState> = emptyMap(),
    val opacity: Float = 1f,
    // 零偏移不写入导出结果，未使用此扩展的主题保持原有字段集合。
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val popupOffset: ThemeScriptPopupOffset = ThemeScriptPopupOffset(),
)

/** 弹起菜单的位置偏移，单位 dp；只提供上、下、左三个物理方向。 */
@Serializable
data class ThemeScriptPopupOffset(
    val up: Float = 0f,
    val down: Float = 0f,
    val left: Float = 0f,
)

@Serializable
data class ThemeScriptInsets(
    val start: Float = 0f,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
)

@Serializable
data class ThemeScriptBounds(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 1f,
    val height: Float = 1f,
)

@Serializable
data class ThemeScriptLayer(
    val type: String,
    val id: String? = null,
    val slices: ThemeScriptInsets = ThemeScriptInsets(),
    val bounds: ThemeScriptBounds = ThemeScriptBounds(),
    val color: String? = null,
    val strokeColor: String? = null,
    val strokeWidth: Float = 0f,
    val radius: Float = 0f,
    val opacity: Float = 1f,
    val asset: String? = null,
    val fit: String = "fill",
    val placement: String = "background",
    val state: String = "always",
    val sampling: String = "smooth",
    val alignment: String = "center",
    val blur: Float = 0f,
)
