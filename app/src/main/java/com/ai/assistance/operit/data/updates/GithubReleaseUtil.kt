package com.ai.assistance.operit.data.updates

import android.content.Context
import com.ai.assistance.operit.data.updates.patch.requirePatchByteRange
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.api.GitHubApiService
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub Release 工具类
 * 使用统一的 GitHubApiService 来获取 Release 信息
 * 支持自动添加认证头以提高 API 配额
 */
class GithubReleaseUtil(private val context: Context) {
    private val TAG = "GithubReleaseUtil"
    private val githubApiService = GitHubApiService(context)

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releasePageUrl: String,
        val downloadSha256: String? = null,
        val downloadSize: Long? = null,
    )

    data class ProbeResult(
        val ok: Boolean,
        val latencyMs: Long?,
        val bytesPerSec: Long? = null,
        val sampledBytes: Long? = null,
        val httpStatus: Int? = null,
        val contentType: String? = null,
        val error: String? = null
    )

    companion object {
        /** Source key for the official GitHub asset URL, i.e. with no mirror in front. */
        const val GITHUB_SOURCE_KEY = "GitHub"

        /**
         * Normalise the GitHub asset `digest` field (`sha256:<hex>`) to a bare lowercase
         * hex string. Returns null when the asset carries no usable SHA-256 digest,
         * which happens for assets uploaded before GitHub started recording them.
         */
        fun parseAssetDigest(digest: String?): String? {
            val value = digest?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (!value.startsWith("sha256:", ignoreCase = true)) return null
            val hex = value.substring("sha256:".length).trim()
            val isValidHex = hex.length == 64 && hex.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
            }
            return if (isValidHex) hex.lowercase() else null
        }

        private val GITHUB_MIRRORS = linkedMapOf(
            "Ghfast" to "https://ghfast.top/",
            "GhProxy" to "https://ghproxy.com/",
            "GhProxyNet" to "https://ghproxy.net/",
            "GhProxyMirror" to "https://mirror.ghproxy.com/",
            "Gh-Proxy" to "https://gh-proxy.com/",
            "GitMirror" to "https://hub.gitmirror.com/",
            "Moeyy" to "https://github.moeyy.xyz/",
            "Workers" to "https://github.abskoop.workers.dev/",
            "H233" to "https://gh.h233.eu.org/",
            "Gh1888866" to "https://ghproxy.1888866.xyz/",
            "GhProxyCfd" to "https://ghproxy.cfd/",
            "BokiMoe" to "https://github.boki.moe/",
            "GhProxyNetHyphen" to "https://gh-proxy.net/",
            "JasonZeng" to "https://gh.jasonzeng.dev/",
            "Monlor" to "https://gh.monlor.com/",
            "FastGitCc" to "https://fastgit.cc/",
            "Tbedu" to "https://github.tbedu.top/",
            "FirewallLxstd" to "https://firewall.lxstd.org/",
            "Ednovas" to "https://github.ednovas.xyz/",
            "GeekerTao" to "https://ghfile.geekertao.top/",
            "Chjina" to "https://gh.chjina.com/",
            "Hwinzniej" to "https://ghpxy.hwinzniej.top/",
            "CrashMc" to "https://cdn.crashmc.com/",
            "Yylx" to "https://git.yylx.win/",
            "Mrhjx" to "https://gitproxy.mrhjx.cn/",
            "Cxkpro" to "https://ghproxy.cxkpro.top/",
            "Xxooo" to "https://gh.xxooo.cf/",
            "Limoruirui" to "https://github.limoruirui.com/",
            "Llkk" to "https://gh.llkk.cc/",
            "Npee" to "https://down.npee.cn/?",
            "Nxnow" to "https://gh.nxnow.top/",
            "Zwy" to "https://gh.zwy.one/",
            "Monkeyray" to "https://ghproxy.monkeyray.net/",
            "Xx9527" to "https://gh.xx9527.cn/"
        )

        /**
         * 获取镜像加速 URL
         * 用于加速 GitHub 下载
         */
        fun getMirroredUrls(originalUrl: String): Map<String, String> {
            if (!originalUrl.contains("github.com") || !originalUrl.contains("/releases/download/")) {
                return emptyMap()
            }

            return GITHUB_MIRRORS.mapValues { entry ->
                "${entry.value}$originalUrl"
            }
        }

        fun getDownloadSources(originalUrl: String): Map<String, String> =
            linkedMapOf(GITHUB_SOURCE_KEY to originalUrl) + getMirroredUrls(originalUrl)

        suspend fun probeMirrorUrls(
            urls: Map<String, String>,
            timeoutMs: Int = 2500,
            requireByteRange: Boolean = false,
        ): Map<String, ProbeResult> {
            return withContext(Dispatchers.IO) {
                coroutineScope {
                    urls.entries
                        .map { (name, url) ->
                            async { name to probeOneUrl(url, timeoutMs, requireByteRange) }
                        }
                        .awaitAll()
                        .toMap()
                }
            }
        }

        private const val SPEED_TEST_BYTES = 256 * 1024L

        private fun probeOneUrl(url: String, timeoutMs: Int, requireByteRange: Boolean): ProbeResult {
            val startNs = System.nanoTime()
            return try {
                var conn: HttpURLConnection? = null
                try {
                    conn = URL(url).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Operit")
                    conn.setRequestProperty("Range", "bytes=0-${SPEED_TEST_BYTES - 1}")
                    if (requireByteRange) conn.setRequestProperty("Accept-Encoding", "identity")
                    conn.connect()

                    val code = conn.responseCode
                    val contentType = conn.contentType
                    if ((requireByteRange && code != HttpURLConnection.HTTP_PARTIAL) || code !in 200..299) {
                        return ProbeResult(
                            ok = false,
                            latencyMs = null,
                            bytesPerSec = null,
                            sampledBytes = null,
                            httpStatus = code,
                            contentType = contentType,
                            error = if (requireByteRange && code == HttpURLConnection.HTTP_OK) {
                                "HTTP 200 (no Range support)"
                            } else "HTTP $code"
                        )
                    }

                    if (contentType != null && (
                            contentType.startsWith("text/html", ignoreCase = true) ||
                            contentType.startsWith("application/xhtml+xml", ignoreCase = true)
                        )) {
                        return ProbeResult(false, null, httpStatus = code, contentType = contentType,
                            error = "HTTP $code ($contentType)")
                    }
                    val expectedRange = if (requireByteRange) {
                        val header = conn.getHeaderField("Content-Range")
                        val total = header?.substringAfterLast('/')?.toLongOrNull()
                            ?: throw java.io.IOException("Missing byte range total")
                        requirePatchByteRange(code, header, 0L, minOf(SPEED_TEST_BYTES, total) - 1L)
                    } else null
                    var sampled = 0L
                    val buf = ByteArray(16 * 1024)
                    conn.inputStream.use { ins ->
                        while (sampled < SPEED_TEST_BYTES) {
                            val toRead =
                                minOf(buf.size.toLong(), SPEED_TEST_BYTES - sampled).toInt()
                            val n = ins.read(buf, 0, toRead)
                            if (n <= 0) break
                            sampled += n.toLong()
                        }
                    }

                    if (expectedRange != null && sampled != expectedRange.length) {
                        throw java.io.IOException("Patch mirror returned an incomplete byte range")
                    }
                    val costMs = (System.nanoTime() - startNs) / 1_000_000
                    val safeMs = if (costMs <= 0L) 1L else costMs
                    val speed = if (sampled > 0L) (sampled * 1000L) / safeMs else null

                    ProbeResult(
                        ok = sampled > 0L,
                        latencyMs = costMs,
                        bytesPerSec = speed,
                        sampledBytes = sampled,
                        httpStatus = code,
                        contentType = contentType,
                        error = if (sampled > 0L) null else "EMPTY_BODY"
                    )
                } finally {
                    try {
                        conn?.disconnect()
                    } catch (_: Exception) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProbeResult(
                    ok = false,
                    latencyMs = null,
                    bytesPerSec = null,
                    sampledBytes = null,
                    error = e.javaClass.simpleName
                )
            }
        }
    }

    /**
     * 获取最新的 Release 信息
     * 如果用户已登录，会自动带上认证头以提高 API 配额
     */
    suspend fun fetchLatestReleaseInfo(repoOwner: String, repoName: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val result = githubApiService.getRepositoryReleases(
                owner = repoOwner,
                repo = repoName,
                page = 1,
                perPage = 1
            )

            result.fold(
                onSuccess = { releases ->
                    if (releases.isEmpty()) {
                        AppLogger.e(TAG, "No releases found for $repoOwner/$repoName")
                        return@withContext null
                    }

                    val latestRelease = releases.first()
                    val tagName = latestRelease.tag_name
                    val version = tagName.removePrefix("v")

                    // 查找 APK 资源
                    val apkAsset = latestRelease.assets.find { it.name.endsWith(".apk") }
                    val downloadUrl = apkAsset?.browser_download_url ?: latestRelease.html_url

                    ReleaseInfo(
                        version = version,
                        downloadUrl = downloadUrl,
                        releaseNotes = latestRelease.body ?: "",
                        releasePageUrl = latestRelease.html_url,
                        downloadSha256 = parseAssetDigest(apkAsset?.digest),
                        downloadSize = apkAsset?.size?.takeIf { it > 0L },
                    )
                },
                onFailure = { exception ->
                    AppLogger.e(TAG, "Failed to get release info for $repoOwner/$repoName", exception)
                    null
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching latest release info for $repoOwner/$repoName", e)
            null
        }
    }
} 
