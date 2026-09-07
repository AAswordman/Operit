package com.ai.assistance.operit.ui.common.icons

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private data class CachedLogo(
    val bytes: ByteArray,
    val contentType: String?
)

object RemoteLogoLoader {
    private const val TAG = "RemoteLogoLoader"
    private const val MAX_LOGO_BYTES = 512 * 1024L
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024

    private val cache =
        object : LruCache<String, CachedLogo>(MAX_CACHE_BYTES) {
            override fun sizeOf(key: String, value: CachedLogo): Int {
                return value.bytes.size
            }
        }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .build()
    }

    fun load(url: String, sizePx: Int): Bitmap? {
        val parsedUrl = url.trim().toHttpUrlOrNull() ?: return null
        if (parsedUrl.scheme != "https") return null

        return try {
            val cached = synchronized(cache) { cache.get(parsedUrl.toString()) }
            val logo =
                cached ?: fetch(parsedUrl.toString()).also { value ->
                    if (value != null) {
                        synchronized(cache) { cache.put(parsedUrl.toString(), value) }
                    }
                }
            logo?.let { value ->
                LogoBitmapLoader.load(
                    bytes = value.bytes,
                    mimeType = value.contentType,
                    fileName = parsedUrl.encodedPath,
                    sizePx = sizePx
                )
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to load remote logo host=${parsedUrl.host}", error)
            null
        }
    }

    private fun fetch(url: String): CachedLogo? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_LOGO_BYTES) return null
            val bytes =
                body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_LOGO_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            return CachedLogo(
                bytes = bytes,
                contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
            )
        }
    }
}

@Composable
fun rememberRemoteLogoPainter(
    logoUrl: String?,
    size: Dp = 24.dp
): Painter? {
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap by
        produceState<ImageBitmap?>(initialValue = null, logoUrl, sizePx) {
            value =
                logoUrl
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { url ->
                        withContext(Dispatchers.IO) {
                            RemoteLogoLoader.load(url, sizePx)?.asImageBitmap()
                        }
                    }
        }
    return bitmap?.let { BitmapPainter(it) }
}
