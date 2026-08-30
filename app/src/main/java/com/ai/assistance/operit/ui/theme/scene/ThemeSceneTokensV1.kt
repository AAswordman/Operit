package com.ai.assistance.operit.ui.theme.scene

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val TOKEN_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*$")

/**
 * Token pool owned by one theme package. Scene nodes reference tokens by ID; the resolver
 * fails fast on unknown IDs or type mismatches instead of substituting defaults.
 */
@Serializable
internal data class ThemeSceneTokenSetV1(
    val tokens: Map<String, ThemeSceneTokenValueV1> = emptyMap(),
) {
    init {
        require(tokens.keys.all(TOKEN_ID_PATTERN::matches)) {
            "Theme scene token keys must be valid token IDs: ${tokens.keys}"
        }
    }
}

@Serializable
internal sealed interface ThemeSceneTokenValueV1 {
    @Serializable
    @SerialName("color")
    data class ColorToken(
        val lightArgb: Long,
        val darkArgb: Long,
    ) : ThemeSceneTokenValueV1 {
        init {
            require(lightArgb in 0..0xFFFFFFFFL && darkArgb in 0..0xFFFFFFFFL) {
                "Color token values must be ARGB within 0..0xffffffff: $lightArgb / $darkArgb"
            }
        }

        fun resolve(darkTheme: Boolean): Color = Color(if (darkTheme) darkArgb else lightArgb)
    }

    @Serializable
    @SerialName("dimension")
    data class DimensionToken(
        val dp: Float,
    ) : ThemeSceneTokenValueV1 {
        init {
            require(dp >= 0f) { "Dimension token must not be negative: $dp" }
        }
    }

    @Serializable
    @SerialName("text_style")
    data class TextStyleToken(
        val fontSizeSp: Float,
        val lineHeightSp: Float? = null,
        val fontWeight: Int = 400,
        val letterSpacingEm: Float = 0f,
        val color: ThemeSceneTokenIdV1,
        val fontAsset: ThemeSceneAssetIdV1? = null,
    ) : ThemeSceneTokenValueV1 {
        init {
            require(fontSizeSp > 0f) { "Text style font size must be positive: $fontSizeSp" }
            lineHeightSp?.let {
                require(it >= fontSizeSp) { "Text style line height must not be below font size" }
            }
            require(fontWeight in 100..1000) { "Text style font weight must be within 100..1000" }
        }
    }
}

internal data class ResolvedThemeSceneTextStyleV1(
    val fontSizeSp: Float,
    val lineHeightSp: Float?,
    val fontWeight: Int,
    val letterSpacingEm: Float,
    val color: Color,
    val fontAsset: ThemeSceneAssetIdV1?,
)

internal class ThemeSceneTokenResolverV1(
    private val tokenSet: ThemeSceneTokenSetV1,
) {
    fun color(tokenId: ThemeSceneTokenIdV1, darkTheme: Boolean): Color =
        colorToken(tokenId).resolve(darkTheme)

    fun dimension(tokenId: ThemeSceneTokenIdV1): Float =
        tokenOrNull<ThemeSceneTokenValueV1.DimensionToken>(tokenId)?.dp
            ?: error("Theme scene token ${tokenId.value} is missing.")

    fun textStyle(tokenId: ThemeSceneTokenIdV1, darkTheme: Boolean): ResolvedThemeSceneTextStyleV1 {
        val token =
            tokenOrNull<ThemeSceneTokenValueV1.TextStyleToken>(tokenId)
                ?: error("Theme scene token ${tokenId.value} is missing or is not a text style.")
        return ResolvedThemeSceneTextStyleV1(
            fontSizeSp = token.fontSizeSp,
            lineHeightSp = token.lineHeightSp,
            fontWeight = token.fontWeight,
            letterSpacingEm = token.letterSpacingEm,
            color = color(token.color, darkTheme),
            fontAsset = token.fontAsset,
        )
    }

    private fun colorToken(tokenId: ThemeSceneTokenIdV1): ThemeSceneTokenValueV1.ColorToken =
        tokenOrNull<ThemeSceneTokenValueV1.ColorToken>(tokenId)
            ?: error("Theme scene token ${tokenId.value} is missing or is not a color.")

    private inline fun <reified T : ThemeSceneTokenValueV1> tokenOrNull(
        tokenId: ThemeSceneTokenIdV1,
    ): T? = tokenSet.tokens[tokenId.value] as? T
}

/** Structured validation for a token pool: key format and cross-token references. */
internal fun validateThemeSceneTokenSetV1(
    tokenSet: ThemeSceneTokenSetV1,
): List<ThemeSceneIssueV1> {
    val issues = mutableListOf<ThemeSceneIssueV1>()
    tokenSet.tokens.forEach { (key, value) ->
        if (value is ThemeSceneTokenValueV1.TextStyleToken) {
            val referenced = tokenSet.tokens[value.color.value]
            when {
                referenced == null ->
                    issues +=
                        ThemeSceneIssueV1(
                            code = ThemeSceneIssueCodeV1.UNKNOWN_TOKEN,
                            path = "tokens/$key",
                            message = "Text style token references unknown token: ${value.color.value}",
                        )

                referenced !is ThemeSceneTokenValueV1.ColorToken ->
                    issues +=
                        ThemeSceneIssueV1(
                            code = ThemeSceneIssueCodeV1.INVALID_TOKEN_REFERENCE,
                            path = "tokens/$key",
                            message =
                                "Text style token must reference a color token: ${value.color.value}",
                        )
            }
        }
    }
    return issues
}
