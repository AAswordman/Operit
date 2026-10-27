package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The published asset digest is the only integrity anchor the updater has: the patch meta
 * carries `patchSha256` / `targetSha256`, so if the meta itself is accepted on trust those
 * hashes prove nothing. These tests pin parsing of the GitHub `digest` field and the source
 * list both update paths walk.
 */
class GithubReleaseDigestTest {

    @Test
    fun `parses a sha256 digest to bare lowercase hex`() {
        val hex = "A04D283464B72D5734AF22BFF77A025C2B56B5679A95CBE5D57B8C31B41C0CB0"

        assertEquals(hex.lowercase(), GithubReleaseUtil.parseAssetDigest("sha256:$hex"))
    }

    @Test
    fun `parses a digest regardless of prefix casing`() {
        val hex = "a".repeat(64)

        assertEquals(hex, GithubReleaseUtil.parseAssetDigest("SHA256:$hex"))
    }

    @Test
    fun `rejects a missing digest`() {
        assertNull(GithubReleaseUtil.parseAssetDigest(null))
        assertNull(GithubReleaseUtil.parseAssetDigest(""))
        assertNull(GithubReleaseUtil.parseAssetDigest("   "))
    }

    @Test
    fun `rejects digests that are not sha256`() {
        assertNull(GithubReleaseUtil.parseAssetDigest("md5:d41d8cd98f00b204e9800998ecf8427e"))
        assertNull(GithubReleaseUtil.parseAssetDigest("sha512:" + "a".repeat(128)))
    }

    @Test
    fun `rejects a malformed sha256 digest`() {
        assertNull(GithubReleaseUtil.parseAssetDigest("sha256:not-hex"))
        // Correct alphabet but wrong length.
        assertNull(GithubReleaseUtil.parseAssetDigest("sha256:" + "a".repeat(63)))
        assertNull(GithubReleaseUtil.parseAssetDigest("sha256:" + "a".repeat(65)))
    }

    @Test
    fun `source list always offers the official github asset first`() {
        val sources = GithubReleaseUtil.getDownloadSources(
            "https://github.com/AAswordman/Operit/releases/download/v1.12.2/app-release.apk"
        )

        assertEquals(
            "https://github.com/AAswordman/Operit/releases/download/v1.12.2/app-release.apk",
            sources.getValue(GithubReleaseUtil.GITHUB_SOURCE_KEY)
        )
        assertEquals(GithubReleaseUtil.GITHUB_SOURCE_KEY, sources.keys.first())
    }

    @Test
    fun `every mirror points at the same release asset`() {
        val asset =
            "https://github.com/AAswordman/Operit/releases/download/v1.12.2/app-release.apk"
        val sources = GithubReleaseUtil.getDownloadSources(asset)

        val mirrors = sources.filterKeys { it != GithubReleaseUtil.GITHUB_SOURCE_KEY }
        assertTrue(mirrors.isNotEmpty())
        assertTrue(mirrors.values.all { it.endsWith(asset) })
    }
}
