package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Structured text only: it cannot recurse into tool markup, HTML or another A2UI surface. */
@Composable
internal fun CardRichText(runs: List<*>, modifier: Modifier, onOpenUrl: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val backdrop = LocalA2uiBackdrop.current
    val openUrl by rememberUpdatedState(onOpenUrl)
    val annotated = remember(runs, colors, typography, backdrop) {
        buildAnnotatedString {
            runs.take(64).forEach { item ->
                val run = item as? Map<*, *> ?: return@forEach
                val text = (run["text"] as? String).orEmpty().take(8192)
                val url = (run["url"] as? String)?.takeIf(A2uiProtocol::safeUrl)
                val highlight = run["highlight"] == true
                val base = if (url != null) colors.primary
                    else cardToneInk(run["tone"] as? String, colors, backdrop) ?: colors.onSurface
                // Colors are tuned for the card backdrop; a highlight wash moves it, so re-check there.
                val tone = if (highlight) ensureReadableOn(base, colors.secondaryContainer, 3.0) else base
                if (url != null) pushLink(LinkAnnotation.Url(url, linkInteractionListener = { openUrl(url) }))
                withStyle(SpanStyle(
                    color = tone,
                    fontSize = when (run["size"]) {
                        "title" -> typography.titleMedium.fontSize
                        "caption" -> typography.bodySmall.fontSize
                        else -> typography.bodyMedium.fontSize
                    },
                    fontWeight = if (run["bold"] == true) FontWeight.SemiBold else FontWeight.Normal,
                    fontStyle = if (run["italic"] == true) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = TextDecoration.combine(buildList {
                        if (url != null || run["underline"] == true) add(TextDecoration.Underline)
                        if (run["strike"] == true) add(TextDecoration.LineThrough)
                    }),
                    background = if (highlight) colors.secondaryContainer else androidx.compose.ui.graphics.Color.Unspecified,
                    fontFeatureSettings = if (run["tabular"] == true) "tnum" else null,
                )) { append(text) }
                if (url != null) pop()
            }
        }
    }
    Text(annotated, modifier, style = typography.bodyMedium, color = colors.onSurface)
}
