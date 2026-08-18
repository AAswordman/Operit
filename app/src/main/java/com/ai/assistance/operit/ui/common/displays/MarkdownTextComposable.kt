package com.ai.assistance.operit.ui.common.displays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.stream.stream
import androidx.compose.material3.LocalContentColor
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import androidx.compose.ui.platform.LocalContext
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger

/** 新一代流式Markdown+LaTeX渲染器，完全替换原有实现。 兼容原有API，支持所有Markdown和LaTeX混排。 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true, // 保留参数，暂不处理
        onLinkClicked: ((String) -> Unit)? = null,
        enableDialogs: Boolean = true
) {
        // 直接使用新的、基于字符串的渲染器，以获得更好的性能
        val context = LocalContext.current
        StreamMarkdownRenderer(
                content = text,
                modifier = modifier,
                textColor = textColor,
                fontSize = fontSize,
                enableDialogs = enableDialogs,
                onLinkClick = { url -> openMarkdownLink(context, url) }
        )
}

private const val MARKDOWN_LINK_TAG = "MarkdownLink"

// A markdown link target is not necessarily something the system can open. Market package
// descriptions are rendered straight from a repository README, so they carry references that are
// relative to that document, such as the "English" link of a bilingual README pointing at
// README.md, or an in-document anchor pointing at #usage. Those have no URI scheme, no activity
// can ever match them, and handing one to ACTION_VIEW makes startActivity throw
// ActivityNotFoundException and take the app down. Dispatch absolute URIs only, and tell the user
// why a document-relative reference goes nowhere.
//
// An absolute URI is not a guarantee either: startActivity documents that it throws when nothing
// on the device declares a matching intent filter (mailto: with no mail client, an app scheme
// whose app is not installed) and it can also fail with SecurityException when the matching
// activity is not exported to us. Both leave the user exactly where a relative reference does, so
// they report the same way instead of taking the process down.
private fun openMarkdownLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme.isNullOrEmpty()) {
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        AppLogger.w(MARKDOWN_LINK_TAG, "no activity can open markdown link: $url", e)
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        AppLogger.w(MARKDOWN_LINK_TAG, "not allowed to open markdown link: $url", e)
        Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
    }
}
