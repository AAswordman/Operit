package com.ai.assistance.operit.ui.common.markdown.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CancellationException

/** 外链失败属于可提示的操作结果，不让异常离开 Markdown 的点击回调。 */
internal fun openMarkdownLink(context: Context, url: String): Boolean {
    try {
        if (url.isBlank() || url.any { it.code < 32 }) return showMarkdownLinkFailure(context)
        val uri = Uri.parse(URI(url.trim().replace(" ", "%20")).toString())
        if (uri.scheme.isNullOrEmpty()) return showMarkdownLinkFailure(context)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (error: ActivityNotFoundException) {
        AppLogger.w("MarkdownLink", "没有应用可打开 Markdown 链接：$url", error)
    } catch (error: SecurityException) {
        AppLogger.w("MarkdownLink", "Markdown 链接访问被拒绝：$url", error)
    } catch (error: URISyntaxException) {
        AppLogger.w("MarkdownLink", "Markdown 链接格式无效：$url", error)
    } catch (error: IllegalArgumentException) {
        AppLogger.w("MarkdownLink", "Markdown 链接参数无效：$url", error)
    } catch (error: Exception) {
        AppLogger.w("MarkdownLink", "打开 Markdown 链接失败：$url", error)
    }
    return showMarkdownLinkFailure(context)
}

private fun showMarkdownLinkFailure(context: Context): Boolean {
    Toast.makeText(context, R.string.markdown_link_not_openable, Toast.LENGTH_SHORT).show()
    return false
}