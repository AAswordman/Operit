package com.ai.assistance.operit.data.updates.distribution

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.updates.GithubReleaseUtil
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import org.json.JSONObject

/** 独立发行版使用自身发布源，带 + 的补丁版本由 Beta 计划开关控制。 */
internal object DistributionReleaseResolver {
    const val OWNER = "3316891527"
    const val REPOSITORY = "Operit-Kong-patch"
    const val APK_NAME = "app-kong.apk"
    private val versionPattern = Regex("""^\d+\.\d+\.\d+(?:\+\d+)?$""")

    fun resolve(
        currentVersion: String,
        betaPlanEnabled: Boolean,
        releases: List<GitHubRelease>
    ): UpdateStatus {
        require(versionPattern.matches(currentVersion)) { "Invalid installed version: $currentVersion" }
        val latest = releases
            .filter { release ->
                val version = release.tag_name.removePrefix("v")
                // 在选择最新版本前过滤渠道，避免补丁版遮住可安装的正式版。
                !release.draft && !release.prerelease &&
                    versionPattern.matches(version) &&
                    (betaPlanEnabled || '+' !in version) &&
                    release.assets.any { it.name == APK_NAME }
            }
            .maxWithOrNull { left, right ->
                UpdateManager.compareVersions(left.tag_name, right.tag_name)
            } ?: return UpdateStatus.UpToDate
        val version = latest.tag_name.removePrefix("v")
        if (UpdateManager.compareVersions(version, currentVersion) <= 0) {
            return UpdateStatus.UpToDate
        }

        val body = latest.body.orEmpty().trim()
        // 已发行旧版本将说明嵌入 JSON；新发行版正文直接使用 Markdown，元数据保存为附件。
        val notes = if (body.startsWith("{")) JSONObject(body).getString("releaseNotes") else body
        val patch = latest.assets.firstOrNull {
            it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip")
        }
        val meta = latest.assets.firstOrNull {
            it.name.startsWith("patch_") && it.name.endsWith(".json")
        }
        val apk = latest.assets.single { it.name == APK_NAME }
        // 仅同一基础版本使用已有补丁协议，跨基础版本明确提供完整 APK 更新。
        if (version.substringBefore('+') == currentVersion.substringBefore('+') && patch != null && meta != null) {
            return UpdateStatus.PatchAvailable(
                newVersion = version,
                updateUrl = latest.html_url,
                releaseNotes = notes,
                patchUrl = patch.browser_download_url,
                metaUrl = meta.browser_download_url,
                fullApkUrl = apk.browser_download_url,
                metaSha256 = GithubReleaseUtil.parseAssetDigest(meta.digest),
                patchSha256 = GithubReleaseUtil.parseAssetDigest(patch.digest),
                fullApkSha256 = GithubReleaseUtil.parseAssetDigest(apk.digest),
                fullApkSize = apk.size.takeIf { it > 0L },
            )
        }
        return UpdateStatus.Available(
            newVersion = version,
            updateUrl = latest.html_url,
            releaseNotes = notes,
            downloadUrl = apk.browser_download_url,
            downloadSha256 = GithubReleaseUtil.parseAssetDigest(apk.digest),
            downloadSize = apk.size.takeIf { it > 0L },
        )
    }
}
