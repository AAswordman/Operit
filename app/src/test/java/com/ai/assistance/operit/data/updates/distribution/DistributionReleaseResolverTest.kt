package com.ai.assistance.operit.data.updates.distribution

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import com.ai.assistance.operit.data.updates.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionReleaseResolverTest {
    @Test
    fun sameBaseReleaseWithBothAssetsOffersPatchAlongsideFullApk() {
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(release("1.12.2+2", patch = true)))
        assertTrue(status is UpdateStatus.PatchAvailable)
        status as UpdateStatus.PatchAvailable
        assertEquals("1.12.2+2", status.newVersion)
        assertTrue(status.patchUrl.contains("3316891527/Operit-Kong-patch/releases/"))
        assertTrue(requireNotNull(status.fullApkUrl).endsWith("app-kong.apk"))
    }

    @Test
    fun higherBaseVersionOffersFullApkEvenWhenPatchAssetsExist() {
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(release("1.12.3+1", patch = true)))
        assertTrue(status is UpdateStatus.Available)
        assertTrue((status as UpdateStatus.Available).downloadUrl.endsWith("app-kong.apk"))
    }

    @Test
    fun fullApkReleaseWithoutPatchIsSupported() {
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(release("1.12.2+2")))
        assertTrue(status is UpdateStatus.Available)
    }

    @Test
    fun newestNumericVersionWinsOverApiOrderAndOlderPatches() {
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(
            release("1.12.2+9", patch = true), release("1.12.2+10"), release("1.12.2+2", patch = true)
        ))
        assertEquals("1.12.2+10", (status as UpdateStatus.Available).newVersion)
    }

    @Test
    fun draftPrereleaseOtherPackageAndInvalidTagsAreIgnored() {
        val valid = release("1.12.2+2")
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(
            release("1.12.2+3").copy(draft = true),
            release("1.12.2+4").copy(prerelease = true),
            release("1.12.2+5").copy(assets = listOf(asset("app-release.apk"))),
            release("1.12.2-personal.6"), valid
        ))
        assertEquals("1.12.2+2", (status as UpdateStatus.Available).newVersion)
    }

    @Test
    fun sameOlderAndEmptyReleasesDoNotOfferUpdates() {
        for (releases in listOf(emptyList(), listOf(release("1.12.2+1")), listOf(release("1.12.2")))) {
            assertSame(UpdateStatus.UpToDate, DistributionReleaseResolver.resolve("1.12.2+1", true, releases))
        }
    }

    @Test
    fun structuredReleaseBodyShowsOnlyReleaseNotes() {
        val source = release("1.12.2+2", patch = true).copy(body = """{"format":"apkraw-1","releaseNotes":"修复设置入口"}""")
        val status = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(source))
        assertEquals("修复设置入口", (status as UpdateStatus.PatchAvailable).releaseNotes)
    }

    @Test
    fun incompletePatchPairDoesNotOfferPatchInstallation() {
        val source = release("1.12.2+2", patch = true)
        val withoutMeta = source.copy(assets = source.assets.filterNot { it.name.endsWith(".json") })
        assertTrue(DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(withoutMeta)) is UpdateStatus.Available)
    }

    @Test
    fun betaDisabledIgnoresSameBasePatchRelease() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.2",
            betaPlanEnabled = false,
            releases = listOf(release("1.12.2+5", patch = true))
        )
        assertSame(UpdateStatus.UpToDate, status)
    }

    @Test
    fun betaDisabledIgnoresHigherBasePlusReleaseWithoutPatchAssets() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.2",
            betaPlanEnabled = false,
            releases = listOf(release("1.12.3+1"))
        )
        assertSame(UpdateStatus.UpToDate, status)
    }

    @Test
    fun betaDisabledSelectsNewestStableBeforeComparingVersions() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.1",
            betaPlanEnabled = false,
            releases = listOf(
                release("1.12.4+1"), release("1.12.3"),
                release("1.13.0+2", patch = true), release("1.12.2")
            )
        )
        assertEquals("1.12.3", (status as UpdateStatus.Available).newVersion)
    }

    @Test
    fun leavingBetaDoesNotDowngradeInstalledPatchVersion() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.2+4",
            betaPlanEnabled = false,
            releases = listOf(release("1.12.2"), release("1.12.2+5", patch = true))
        )
        assertSame(UpdateStatus.UpToDate, status)
    }

    @Test
    fun installedPatchVersionCanUpdateToNewerStableAfterLeavingBeta() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.2+4",
            betaPlanEnabled = false,
            releases = listOf(release("1.12.3"), release("1.12.3+1", patch = true))
        )
        assertEquals("1.12.3", (status as UpdateStatus.Available).newVersion)
    }

    @Test
    fun betaEnabledStillOffersStableRelease() {
        val status = DistributionReleaseResolver.resolve(
            currentVersion = "1.12.2+4",
            betaPlanEnabled = true,
            releases = listOf(release("1.12.3"))
        )
        assertEquals("1.12.3", (status as UpdateStatus.Available).newVersion)
    }

    @Test
    fun repositoryIsIndependentDistributionFork() {
        assertEquals("3316891527", DistributionReleaseResolver.OWNER)
        assertEquals("Operit-Kong-patch", DistributionReleaseResolver.REPOSITORY)
    }

    @Test
    fun fullApkAndPatchPathsCarryTheirOwnPublishedDigestsAndSizes() {
        val digests = mapOf("app-kong.apk" to "a".repeat(64),
            "apkrawpatch_old_to_new.zip" to "b".repeat(64), "patch_old_to_new.json" to "c".repeat(64))
        fun withDigests(version: String) = release(version, patch = true).let { release ->
            release.copy(assets = release.assets.map { it.copy(digest = "sha256:${digests.getValue(it.name)}") })
        }
        val patch = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(withDigests("1.12.2+2"))) as UpdateStatus.PatchAvailable
        assertEquals("a".repeat(64), patch.fullApkSha256)
        assertEquals(1024L, patch.fullApkSize)
        assertEquals("b".repeat(64), patch.patchSha256)
        assertEquals("c".repeat(64), patch.metaSha256)
        val full = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(withDigests("1.12.3+1"))) as UpdateStatus.Available
        assertEquals("a".repeat(64), full.downloadSha256)
        assertEquals(1024L, full.downloadSize)
        assertTrue(full.downloadUrl.contains("3316891527/Operit-Kong-patch/"))
    }

    @Test
    fun legacyAssetsWithoutDigestStillCarryTheirPublishedSize() {
        val full = DistributionReleaseResolver.resolve("1.12.2+1", true, listOf(release("1.12.3"))) as UpdateStatus.Available
        assertEquals(null, full.downloadSha256)
        assertEquals(1024L, full.downloadSize)
    }

    private fun release(version: String, patch: Boolean = false): GitHubRelease = GitHubRelease(
        id = 1L,
        tag_name = "v$version",
        name = version,
        body = "版本说明",
        html_url = "https://github.com/3316891527/Operit-Kong-patch/releases/tag/v$version",
        created_at = "2026-10-15T00:00:00Z",
        assets = buildList {
            add(asset("app-kong.apk"))
            if (patch) {
                add(asset("apkrawpatch_old_to_new.zip"))
                add(asset("patch_old_to_new.json"))
            }
        }
    )

    private fun asset(name: String): GitHubReleaseAsset = GitHubReleaseAsset(
        id = 1L, name = name,
        browser_download_url = "https://github.com/3316891527/Operit-Kong-patch/releases/download/v1.12.2+2/$name",
        size = 1024L, download_count = 0, content_type = "application/octet-stream"
    )
}
