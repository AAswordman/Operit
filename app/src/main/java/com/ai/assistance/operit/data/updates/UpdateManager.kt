package com.ai.assistance.operit.data.updates

import android.content.Context
import com.ai.assistance.operit.util.logging.AppLogger
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.updates.distribution.DistributionReleaseResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// 更新状态 - 移除下载相关状态
sealed class UpdateStatus {
    object Initial : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val downloadUrl: String = "",
            val downloadSha256: String? = null,
            val downloadSize: Long? = null,
    ) : UpdateStatus()
    data class PatchAvailable(
            val newVersion: String,
            val updateUrl: String,
            val releaseNotes: String,
            val patchUrl: String,
            val metaUrl: String,
            val fullApkUrl: String? = null,
            val metaSha256: String? = null,
            val patchSha256: String? = null,
            val fullApkSha256: String? = null,
            val fullApkSize: Long? = null,
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

    fun resetUpdateStatus() {
        _updateStatus.value = UpdateStatus.Initial
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

    /** 独立发行版只从自身仓库读取完整包和增量包。 */
    private suspend fun checkForUpdatesInternal(currentVersion: String): UpdateStatus {
        return withContext(Dispatchers.IO) {
            try {
                val releases = GitHubApiService(context).getRepositoryReleases(
                    owner = DistributionReleaseResolver.OWNER,
                    repo = DistributionReleaseResolver.REPOSITORY,
                    page = 1,
                    perPage = 100
                ).getOrThrow()
                // 每次检查都读取当前开关，自动检查和手动检查使用相同的渠道规则。
                val betaPlanEnabled = UserPreferencesManager.getInstance(context).betaPlanEnabled.first()
                DistributionReleaseResolver.resolve(
                    currentVersion = currentVersion,
                    betaPlanEnabled = betaPlanEnabled,
                    releases = releases
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking distribution updates", e)
                UpdateStatus.Error(context.getString(R.string.update_check_failed, e.message))
            }
        }
    }
}
