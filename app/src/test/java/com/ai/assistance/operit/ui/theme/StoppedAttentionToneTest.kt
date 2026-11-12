package com.ai.assistance.operit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoppedAttentionToneTest {
    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** What a translucent container actually paints: its own colour laid over the page behind it. */
    private fun over(container: Color, page: Color, alpha: Float): Color =
        Color(
            red = container.red * alpha + page.red * (1f - alpha),
            green = container.green * alpha + page.green * (1f - alpha),
            blue = container.blue * alpha + page.blue * (1f - alpha),
            alpha = 1f,
        )

    @Test
    fun aStoppedRunNeverWearsTheToneOfAFailedOne() {
        val light = androidx.compose.material3.lightColorScheme()
        val dark = androidx.compose.material3.darkColorScheme()
        assertNotEquals(light.error, light.stoppedAttention)
        assertNotEquals(light.tertiary, light.stoppedAttention)
        assertNotEquals(dark.error, dark.stoppedAttention)
    }

    @Test
    fun theStoppedToneStaysReadableOnEverySurfaceItIsDrawnOn() {
        for (scheme in listOf(androidx.compose.material3.lightColorScheme(), androidx.compose.material3.darkColorScheme())) {
            // The page itself, the conversation's agent card, and a selected row of the Subagent
            // list - the darkest of the three, and the one a custom tone is easiest to get wrong on.
            val surfaces =
                listOf(
                    "page" to scheme.background,
                    "conversation card" to over(scheme.surfaceVariant, scheme.background, 0.35f),
                    "selected list row" to over(scheme.primaryContainer, scheme.background, 0.45f),
                )
            surfaces.forEach { (name, surface) ->
                val contrast = contrastRatio(scheme.stoppedAttention, surface)
                assertTrue("on the $name contrast was $contrast", contrast >= 4.5f)
            }
        }
    }
}
