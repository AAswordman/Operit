package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceFileLinkTest {
    @Test
    fun devicePathsKeepChineseSpacesAndEncodedCharacters() {
        assertEquals(WorkspaceFileLink("/sdcard/Download/中文 文档.md", "android"),
            parseWorkspaceFileLink("/sdcard/Download/中文 文档.md"))
        assertEquals(WorkspaceFileLink("/storage/emulated/0/a+b c.txt", "android"),
            parseWorkspaceFileLink("file:///storage/emulated/0/a+b%20c.txt"))
    }

    @Test
    fun linuxPathsKeepTheirEnvironment() {
        assertEquals(WorkspaceFileLink("/tmp/report.md", "linux"), parseWorkspaceFileLink("/tmp/report.md"))
        assertEquals(WorkspaceFileLink("/home/demo/a.md", "linux"), parseWorkspaceFileLink("linux:///home/demo/a.md"))
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux"), parseWorkspaceFileLink("linux://tmp/a.md"))
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux"), parseWorkspaceFileLink("file://linux/tmp/a.md"))
    }

    @Test
    fun explicitEnvironmentOverridesThePathNamespace() {
        assertEquals(WorkspaceFileLink("/etc/example.txt", "android"),
            parseWorkspaceFileLink("android:///etc/example.txt"))
        assertEquals(WorkspaceFileLink("/sdcard/example.txt", "linux"),
            parseWorkspaceFileLink("file:///sdcard/example.txt?environment=linux"))
    }

    @Test
    fun repositoryPathsPreserveTheRepositoryName() {
        assertEquals(WorkspaceFileLink("/folder/a.md", "repo:文档仓库"),
            parseWorkspaceFileLink("repo://文档仓库/folder/a.md"))
        assertEquals(WorkspaceFileLink("/folder/a.md", "repo:文档仓库"),
            parseWorkspaceFileLink("repo:文档仓库:/folder/a.md"))
    }

    @Test
    fun lineLocationsAreSeparateFromTheFilePath() {
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt#L12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt:12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("linux:///tmp/a.kt?line=12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12), parseWorkspaceFileLink("/tmp/a.kt#L12-L15"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux"), parseWorkspaceFileLink("/tmp/a.kt#L0"))
    }

    @Test
    fun explicitLineLocationsOverrideSuffixAndAlwaysStripItsPathSyntax() {
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 12),
            parseWorkspaceFileLink("/tmp/a.kt:34?line=9#L12"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 9),
            parseWorkspaceFileLink("/tmp/a.kt:34?line=9"))
        assertEquals(WorkspaceFileLink("/tmp/a.kt", "linux", 34),
            parseWorkspaceFileLink("/tmp/a.kt:34"))
    }

    @Test
    fun androidMountRootsDoNotCaptureSimilarlyNamedLinuxDirectories() {
        for (path in listOf("/mnt", "/mnt/media_rw/USB/中文 文档.txt", "/mnt/runtime/default/a.md")) {
            assertEquals(WorkspaceFileLink(path, "android"), parseWorkspaceFileLink(path))
            assertEquals(WorkspaceFileLink(path, "android"), parseWorkspaceFileLink("file://$path"))
        }
        assertEquals(WorkspaceFileLink("/mnt-other/a.txt", "linux"), parseWorkspaceFileLink("/mnt-other/a.txt"))
    }

    @Test
    fun explicitLinuxEnvironmentStillWinsForMountPaths() {
        assertEquals(WorkspaceFileLink("/mnt/a.txt", "linux"), parseWorkspaceFileLink("linux:///mnt/a.txt"))
        assertEquals(WorkspaceFileLink("/mnt/a.txt", "linux"), parseWorkspaceFileLink("file://linux/mnt/a.txt"))
        assertEquals(WorkspaceFileLink("/mnt/a.txt", "linux"), parseWorkspaceFileLink("/mnt/a.txt?environment=linux"))
    }

    @Test
    fun directoryAndArchiveLinksUseTheSameFileEntry() {
        assertEquals(WorkspaceFileLink("/sdcard/Download/", "android"), parseWorkspaceFileLink("/sdcard/Download/"))
        assertEquals(WorkspaceFileLink("/tmp/test.zip", "linux"), parseWorkspaceFileLink("/tmp/test.zip"))
    }

    @Test
    fun webAndApplicationLinksRemainExternal() {
        for (url in listOf("https://example.com/tmp/a.md", "http://example.com/a", "mailto:a@example.com", "content://files/1", "tel:123")) {
            assertNull(parseWorkspaceFileLink(url))
        }
    }

    @Test
    fun malformedAndRelativeLinksDoNotBecomeFileRequests() {
        for (url in listOf("", "a.md", "repo:///a.md", "file://example.com/a.md", "linux:///tmp/a.md?environment=invalid", "/tmp/%invalid")) {
            assertNull(parseWorkspaceFileLink(url))
        }
    }

    @Test
    fun headingAnchorsAndEncodedColonsKeepTheirMeaning() {
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux", anchor = "页脚"),
            parseWorkspaceFileLink("file:///tmp/a.md#页脚"))
        assertEquals(WorkspaceFileLink("/tmp/a.md:12", "linux", 9),
            parseWorkspaceFileLink("file:///tmp/a.md%3A12#L9"))
        assertEquals(WorkspaceFileLink("/tmp/a.md", "linux", 12),
            parseWorkspaceFileLink("file:///tmp/a.md?line=%31%32"))
    }

    @Test
    fun malformedEncodingAndControlCharactersAreRejected() {
        for (url in listOf("/tmp/a%00.md", "/tmp/a.md#%00", "/tmp/a.md?environment=repo%3A%00",
            "/tmp/a.md?environment=%invalid", "//example.com/a.md")) {
            assertNull(parseWorkspaceFileLink(url))
        }
    }

    @Test
    fun encodedCharactersAndRepositoryNamesPreserveTheActualFile() {
        assertEquals(
            WorkspaceFileLink("/mnt/中文 文件(1)#?%.kt", "linux", 12),
            parseWorkspaceFileLink(
                "file:///mnt/中文%20文件%281%29%23%3F%25.kt?environment=linux#L12"
            )
        )
        assertEquals(
            WorkspaceFileLink("/src/main.kt", "repo:文档 & 示例+仓库", 12),
            parseWorkspaceFileLink(
                "file:///src/main.kt?environment=repo%3A文档%20%26%20示例%2B仓库#L12"
            )
        )
    }
}
