package com.ai.assistance.operit.ui.features.chat.webview.workspace.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceMarkdownLinkTest {
    private fun resolve(url: String, environment: String = "linux") =
        resolveWorkspaceMarkdownLink(url, "/work/docs/main.md", environment)

    private fun file(path: String, environment: String = "linux", line: Int? = null, anchor: String? = null) =
        WorkspaceMarkdownLink.File(WorkspaceFileLink(path, environment, line, anchor))

    @Test
    fun relativeFilesUseTheDocumentDirectory() {
        for (url in listOf("target.md", "./target.md", ".\\target.md")) {
            assertEquals(file("/work/docs/target.md"), resolve(url))
        }
        assertEquals(file("/work/docs/sub/nested.md"), resolve("sub/nested.md"))
        assertEquals(file("/work/README.md"), resolve("../README.md"))
        assertEquals(file("/work/docs/target"), resolve("target"))
    }

    @Test
    fun implicitPathsPreserveTheDocumentEnvironment() {
        for (environment in listOf("android", "linux", "repo:文档仓库")) {
            assertEquals(file("/work/docs/target.md", environment), resolve("target.md", environment))
            assertEquals(file("/mnt/target.md", environment), resolve("/mnt/target.md", environment))
            assertEquals(file("/mnt/target.md", environment), resolve("file:///mnt/target.md", environment))
        }
    }

    @Test
    fun explicitSourcesOverrideTheDocumentEnvironment() {
        assertEquals(file("/mnt/a.md", "android"), resolve("android:///mnt/a.md"))
        assertEquals(file("/mnt/a.md", "linux"), resolve("file://linux/mnt/a.md", "android"))
        assertEquals(file("/src/a.md", "repo:demo"), resolve("repo://demo/src/a.md"))
        assertEquals(file("/src/a.md", "repo:demo"), resolve("repo:demo:/src/a.md"))
        assertEquals(file("/work/docs/a.md", "android"), resolve("a.md?environment=android"))
    }

    @Test
    fun encodedNamesAndDotSegmentsKeepTheActualFile() {
        assertEquals(file("/work/docs/目标 文件.md"), resolve("目标%20文件.md"))
        assertEquals(file("/work/docs/link test.md"), resolve("<link test.md>"))
        assertEquals(file("/work/docs/a+b (1)#?%.md"), resolve("a+b%20%281%29%23%3F%25.md"))
        assertEquals(file("/work/docs/a.md"), resolve("sub/.././a.md"))
        assertEquals(file("/README.md"), resolve("../../../README.md"))
    }

    @Test
    fun queryValuesDoNotBecomePartOfThePath() {
        assertEquals(file("/work/docs/a.md"), resolve("a.md?x=1"))
        assertEquals(file("/work/docs/a.md", "repo:文档 & 示例+仓库"),
            resolve("a.md?x=1&environment=repo%3A文档%20%26%20示例%2B仓库"))
    }

    @Test
    fun relativeLineLocationsRespectTheExistingPriority() {
        assertEquals(file("/work/docs/a.md", line = 12), resolve("a.md#L12"))
        assertEquals(file("/work/docs/a.md", line = 12), resolve("a.md:12"))
        assertEquals(file("/work/docs/a.md", line = 12), resolve("a.md?line=12"))
        assertEquals(file("/work/docs/a.md", line = 12), resolve("a.md:34?line=9#L12"))
        assertEquals(file("/work/docs/a.md", line = 9), resolve("a.md:34?line=9"))
    }

    @Test
    fun currentDocumentFragmentsKeepItsPathAndLocation() {
        assertEquals(file("/work/docs/main.md", line = 12), resolve("#L12"))
        assertEquals(file("/work/docs/main.md", line = 12), resolve("?line=12"))
        assertEquals(file("/work/docs/main.md", line = 1), resolve("#"))
        assertEquals(file("/work/docs/main.md", anchor = "页脚"), resolve("#页脚"))
    }

    @Test
    fun crossFileHeadingFragmentsArePreservedForTheLoader() {
        assertEquals(file("/work/docs/target.md", anchor = "附录"), resolve("target.md#附录"))
        assertEquals(file("/work/docs/target.md", anchor = "页脚"), resolve("target.md#%E9%A1%B5%E8%84%9A"))
    }

    @Test
    fun invalidLinksBecomeAnOperationResult() {
        for (url in listOf("", " ", "<>", "%invalid", "a.md?environment=invalid", "file://remote/a.md",
            "a%00.md", "a.md#%00", "a.md?environment=repo%3A%00", "a.md#L0", "#L999999999999999999999", "foo://bad\nvalue")) {
            assertEquals(url, WorkspaceMarkdownLink.Invalid, resolve(url))
        }
        assertEquals(WorkspaceMarkdownLink.Invalid, resolve("a.md", "invalid"))
    }

    @Test
    fun externalSchemesKeepTheirOriginalAddresses() {
        for (url in listOf("https://example.com/a#top", "http://example.com", "mailto:test@example.com",
            "tel:10086", "sms:10086", "geo:39.9,116.4", "foo://bar", "spotify://track/123",
            "javascript:void(0)", "data:text/plain,hello", "content://media/external/images")) {
            assertEquals(WorkspaceMarkdownLink.External(url), resolve(url))
        }
    }

    @Test
    fun protocolRelativeWebAddressesUseAnExternalUrl() {
        assertEquals(WorkspaceMarkdownLink.External("https://example.com/a"), resolve("//example.com/a"))
    }

    @Test
    fun encodedColonStaysPartOfTheFileName() {
        assertEquals(file("/work/docs/a.md:12"), resolve("a.md%3A12"))
        assertEquals(file("/work/docs/a.md:12", line = 9), resolve("a.md%3A12#L9"))
    }

    @Test
    fun atxAnchorsSupportChineseAndClosingMarkers() {
        val content = "# 标题\n\n正文\n\n## 页脚 ###\n\n###### 最后\n#缺空格"
        assertEquals(5, findWorkspaceMarkdownAnchorLine(content, "页脚"))
        assertEquals(7, findWorkspaceMarkdownAnchorLine(content, "最后"))
        assertEquals(5, findWorkspaceMarkdownAnchorLine(content, "user-content-页脚"))
        assertNull(findWorkspaceMarkdownAnchorLine(content, "缺空格"))
        assertNull(findWorkspaceMarkdownAnchorLine(content, "不存在"))
        assertEquals(1, findWorkspaceMarkdownAnchorLine(content, ""))
    }

    @Test
    fun setextAnchorsUseTheHeadingLineAndSkipIndentedCode() {
        val content = "标题\n===\n\n    缩进代码\n    ---\n\n小节\n---"
        assertEquals(1, findWorkspaceMarkdownAnchorLine(content, "标题"))
        assertEquals(7, findWorkspaceMarkdownAnchorLine(content, "小节"))
        assertNull(findWorkspaceMarkdownAnchorLine(content, "缩进代码"))
    }

    @Test
    fun fencedCodeDoesNotSupplyAnchors() {
        val content = "```md\n# hidden\n```\n\n~~~\n## hidden-tilde\n~~~\n\n## 页脚"
        assertNull(findWorkspaceMarkdownAnchorLine(content, "hidden"))
        assertNull(findWorkspaceMarkdownAnchorLine(content, "hidden-tilde"))
        assertEquals(9, findWorkspaceMarkdownAnchorLine(content, "页脚"))
    }

    @Test
    fun githubStyleSlugsKeepHeadingTextAndDisambiguateDuplicates() {
        val content = "# *Hello* `World`!\n\n# Hello World!\n\n# 中文 标题\n\n# [API](https://example.com)"
        assertEquals(1, findWorkspaceMarkdownAnchorLine(content, "hello-world"))
        assertEquals(3, findWorkspaceMarkdownAnchorLine(content, "hello-world-1"))
        assertEquals(5, findWorkspaceMarkdownAnchorLine(content, "中文-标题"))
        assertEquals(7, findWorkspaceMarkdownAnchorLine(content, "api"))
    }
}