package com.ai.assistance.operit.data.updates

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.GithubReleaseUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

// 更新状态 - 移除下载相关状态
sealed class UpdateStatus {
    object Initial : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val downloadUrl: String = "", // 保留下载URL字段用于浏览器打开
            // GitHub 公布的 APK 资产 sha256 与大小，用于下载后校验完整性
            val downloadSha256: String? = null,
            val downloadSize: Long? = null
    ) : UpdateStatus()
    data class PatchAvailable(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val patchUrl: String,
            val metaUrl: String,
            // GitHub 公布的目标 meta / patch 资产 sha256，用于校验下载内容
            val metaSha256: String? = null,
            val patchSha256: String? = null
    ) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/** UpdateManager - 处理应用更新的核心类 负责检查更新 */
class UpdateManager private constructor(private val context: Context) {
    private val TAG = "UpdateManager"

    // 更新状态LiveData，可从UI中观察
    private val _updateStatus = MutableLiveData<UpdateStatus>(UpdateStatus.Initial)
    val updateStatus: LiveData<UpdateStatus> = _updateStatus

    init {
        AppLogger.d(TAG, "UpdateManager initialized")
    }

    companion object {
        @Volatile private var INSTANCE: UpdateManager? = null

        fun getInstance(context: Context): UpdateManager {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance = UpdateManager(context.applicationContext)
                        INSTANCE = instance
                        instance
                    }
        }

        /**
         * 比较两个版本号
         * @return -1 如果v1 < v2, 0 如果 v1 == v2, 1 如果 v1 > v2
         */
        private data class ParsedVersion(val major: Int, val minor: Int, val patch: Int, val patchIndex: Int)

        private fun baseVersionOf(v: String): String {
            val s = v.trim().removePrefix("v")
            val plusIdx = s.indexOf('+')
            return if (plusIdx >= 0) s.substring(0, plusIdx) else s
        }

        private fun parseVersion(v: String): ParsedVersion {
            val s = v.trim().removePrefix("v")
            val plusIdx = s.indexOf('+')
            val base = if (plusIdx >= 0) s.substring(0, plusIdx) else s
            val patchIndex =
                if (plusIdx >= 0) s.substring(plusIdx + 1).toIntOrNull() ?: 0 else 0

            val parts = base.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return ParsedVersion(major = major, minor = minor, patch = patch, patchIndex = patchIndex)
        }

        fun compareVersions(v1: String, v2: String): Int {
            val p1 = parseVersion(v1)
            val p2 = parseVersion(v2)

            if (p1.major != p2.major) return p1.major.compareTo(p2.major)
            if (p1.minor != p2.minor) return p1.minor.compareTo(p2.minor)
            if (p1.patch != p2.patch) return p1.patch.compareTo(p2.patch)
            return p1.patchIndex.compareTo(p2.patchIndex)
        }

        /** 检查更新，返回更新状态 用于从MainActivity直接检查更新 */
        suspend fun checkForUpdates(context: Context, currentVersion: String): UpdateStatus {
            val manager = getInstance(context)
            return manager.checkForUpdatesInternal(currentVersion)
        }
    }

    suspend fun checkForUpdatesSilently(currentVersion: String) {
        AppLogger.d(TAG, "checkForUpdatesSilently() start: currentVersion=$currentVersion")
        try {
            val result = checkForUpdatesInternal(currentVersion)
            AppLogger.d(TAG, "checkForUpdatesSilently() done: status=${result::class.java.simpleName}")
            if (result is UpdateStatus.Available || result is UpdateStatus.PatchAvailable) {
                _updateStatus.postValue(result)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "checkForUpdatesSilently() failed", e)
        }
    }

    /** 开始更新检查流程 */
    suspend fun checkForUpdates(currentVersion: String) {
        AppLogger.d(TAG, "checkForUpdates() start: currentVersion=$currentVersion")
        _updateStatus.postValue(UpdateStatus.Checking)

        try {
            val result = checkForUpdatesInternal(currentVersion)
            AppLogger.d(TAG, "checkForUpdates() done: status=${result::class.java.simpleName}")
            _updateStatus.postValue(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Update check failed", e)
            _updateStatus.postValue(UpdateStatus.Error(context.getString(R.string.update_check_failed, e.message)))
        }
    }

    /** 检查更新的内部实现 */
    private suspend fun checkForUpdatesInternal(currentVersion: String): UpdateStatus {
        return withContext(Dispatchers.IO) {
            try {
                val betaEnabled = try {
                    UserPreferencesManager.getInstance(context).isBetaPlanEnabled()
                } catch (_: Exception) {
                    false
                }

                AppLogger.d(TAG, "checkForUpdatesInternal(): currentVersion=$currentVersion betaEnabled=$betaEnabled")

                val patchUpdate: UpdateStatus? =
                    if (betaEnabled) {
                        AppLogger.d(TAG, "beta enabled, trying patch update releases...")
                        val patch = tryFetchLatestPatchUpdate(currentVersion)
                        if (patch != null) {
                            val p = patch as? UpdateStatus.PatchAvailable
                            AppLogger.i(
                                TAG,
                                "patch update found: newVersion=${p?.newVersion} patchUrl=${p?.patchUrl} metaUrl=${p?.metaUrl}"
                            )
                        } else {
                            AppLogger.d(TAG, "no patch update found")
                        }
                        patch
                    } else {
                        null
                    }

                // 从字符串资源中获取GitHub仓库信息
                val aboutWebsite = context.getString(R.string.about_website)

                // 解析GitHub仓库链接 - 处理HTML格式
                val htmlContent = aboutWebsite.replace("&lt;", "<").replace("&gt;", ">")
                val githubUrlPattern = "https://github.com/([^/\"<>]+)/([^/\"<>]+)".toRegex()
                val matchResult = githubUrlPattern.find(htmlContent)

                val (repoOwner, repoName) =
                        if (matchResult != null) {
                            Pair(matchResult.groupValues[1], matchResult.groupValues[2])
                        } else {
                            Pair("AAswordman", "Operit") // 默认值
                        }

                val githubReleaseUtil = GithubReleaseUtil(context)
                val releaseInfo = githubReleaseUtil.fetchLatestReleaseInfo(repoOwner, repoName)

                AppLogger.d(
                    TAG,
                    "normal release check: repo=$repoOwner/$repoName releaseInfo=${releaseInfo?.version}"
                )

                if (releaseInfo != null) {
                    val normalUpdate: UpdateStatus =
                        if (compareVersions(releaseInfo.version, currentVersion) > 0) {
                            UpdateStatus.Available(
                            newVersion = releaseInfo.version,
                            updateUrl = releaseInfo.releasePageUrl,
                            releaseNotes = releaseInfo.releaseNotes,
                            downloadUrl = releaseInfo.downloadUrl,
                            downloadSha256 = releaseInfo.downloadSha256,
                            downloadSize = releaseInfo.downloadSize
                            )
                        } else {
                            UpdateStatus.UpToDate
                        }

                    val patch = patchUpdate as? UpdateStatus.PatchAvailable
                    val normal = normalUpdate as? UpdateStatus.Available
                    if (patch != null && normal != null) {
                        val finalStatus = if (compareVersions(normal.newVersion, patch.newVersion) >= 0) {
                            normalUpdate
                        } else {
                            patchUpdate
                        }

                        return@withContext finalStatus
                    }

                    val finalStatus = patchUpdate ?: normalUpdate
                    finalStatus
                } else {
                    val finalStatus = patchUpdate ?: UpdateStatus.Error(context.getString(R.string.update_cannot_fetch_info))
                    finalStatus
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking for updates", e)
                return@withContext UpdateStatus.Error(context.getString(R.string.update_check_failed, e.message))
            }
        }
    }

    private suspend fun tryFetchLatestPatchUpdate(currentVersion: String): UpdateStatus? {
        val api = GitHubApiService(context)

        val owner = "AAswordman"
        val repo = "OperitNightlyRelease"

        AppLogger.d(TAG, "tryFetchLatestPatchUpdate(): currentVersion=$currentVersion repo=$owner/$repo")
        val result = api.getRepositoryReleases(owner = owner, repo = repo, page = 1, perPage = 20)

        result.exceptionOrNull()?.let { e ->
            AppLogger.w(TAG, "tryFetchLatestPatchUpdate(): api getRepositoryReleases failed", e)
        }

        val releases = result.getOrNull() ?: return null

        AppLogger.d(TAG, "tryFetchLatestPatchUpdate(): fetched releases=${releases.size}")
        val currentBase = baseVersionOf(currentVersion)

        var matchedBase = 0
        var newerThanCurrent = 0
        var withAssets = 0
        var best: UpdateStatus.PatchAvailable? = null
        var bestVersion = ""

        for (r in releases) {
            if (r.draft) continue

            val tag = r.tag_name
            val version = tag.removePrefix("v")

            // Patch updates are only valid within the same base version (x.y.z).
            // This prevents cases like 1.7.0+1 being offered 1.7.1+3 (should take 1.7.1 full APK first).
            if (baseVersionOf(version) != currentBase) {
                continue
            }

            matchedBase += 1

            if (compareVersions(version, currentVersion) <= 0) {
                continue
            }

            newerThanCurrent += 1

            val metaAsset =
                r.assets.firstOrNull { it.name.startsWith("patch_") && it.name.endsWith(".json") }
                    ?: r.assets.firstOrNull { it.name.endsWith(".json") }
            val patchAsset =
                r.assets.firstOrNull { it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip") }
                    ?: r.assets.firstOrNull { it.name.endsWith(".zip") }
            if (metaAsset == null || patchAsset == null) {
                AppLogger.d(
                    TAG,
                    "patch skip: tag=$tag version=$version hasPatch=${patchAsset != null} hasMeta=${metaAsset != null}"
                )
                continue
            }

            withAssets += 1

            AppLogger.d(
                TAG,
                "patch candidate: tag=$tag version=$version patch=${patchAsset.name} meta=${metaAsset.name}"
            )

            if (best == null || compareVersions(version, bestVersion) > 0) {
                bestVersion = version
                best = UpdateStatus.PatchAvailable(
                    newVersion = version,
                    updateUrl = r.html_url,
                    releaseNotes = r.body ?: "",
                    patchUrl = patchAsset.browser_download_url,
                    metaUrl = metaAsset.browser_download_url,
                    metaSha256 = GithubReleaseUtil.parseAssetDigest(metaAsset.digest),
                    patchSha256 = GithubReleaseUtil.parseAssetDigest(patchAsset.digest)
                )
            }
        }

        AppLogger.d(
            TAG,
            "tryFetchLatestPatchUpdate(): scan done matchedBase=$matchedBase newerThanCurrent=$newerThanCurrent withAssets=$withAssets best=$bestVersion"
        )

        if (best == null) {
            AppLogger.d(TAG, "tryFetchLatestPatchUpdate(): no valid patch candidates")
        }
        return best
    }
}
