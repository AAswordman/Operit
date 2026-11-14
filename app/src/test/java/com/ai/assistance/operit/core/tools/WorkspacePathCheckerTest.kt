package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WorkspacePathChecker 工作区判定与危险路径硬底线的纯 JVM 单测 */
class WorkspacePathCheckerTest {

    private fun tool(vararg params: Pair<String, String>): AITool =
        AITool(name = "write_file", parameters = params.map { ToolParameter(it.first, it.second) })

    @Test
    fun `chat without workspace binding treats every path as outside`() {
        // 需求确认项：未绑定工作区时不存在可信区，所有路径按区外
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = null,
                chatWorkspaceEnv = null,
                tool = tool("path" to "/data/ws/inner/file.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, zone)
    }

    @Test
    fun `android path inside workspace root is inside`() {
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/user/0/app/files/workspace/chat-1",
                chatWorkspaceEnv = null,
                tool = tool("path" to "/data/user/0/app/files/workspace/chat-1/sub/file.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.INSIDE, zone)
    }

    @Test
    fun `android path outside workspace root is outside`() {
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/user/0/app/files/workspace/chat-1",
                chatWorkspaceEnv = null,
                tool = tool("path" to "/data/user/0/app/files/workspace/chat-2/file.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, zone)
    }

    @Test
    fun `sibling prefix that merely shares a path prefix is outside`() {
        // /ws2 与 /ws 共享字符串前缀但不是目录子路径，必须判区外
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = null,
                tool = tool("path" to "/data/ws2/file.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, zone)
    }

    @Test
    fun `linux tool environment matching workspace env is inside`() {
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = "linux",
                tool = tool("path" to "/home/user/x.txt", "environment" to "linux"),
            )
        assertEquals(WorkspacePathChecker.Zone.INSIDE, zone)
    }

    @Test
    fun `linux tool environment with android workspace env is outside`() {
        val zone =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = null,
                tool = tool("path" to "/home/user/x.txt", "environment" to "linux"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, zone)
    }

    @Test
    fun `same repo bookmark is inside and different bookmark is outside`() {
        val same =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = "repo:docs",
                tool = tool("path" to "/docs/a.txt", "environment" to "repo:docs"),
            )
        assertEquals(WorkspacePathChecker.Zone.INSIDE, same)

        val different =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = "repo:docs",
                tool = tool("path" to "/other/a.txt", "environment" to "repo:other"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, different)
    }

    @Test
    fun `multi path call is inside only when every path is inside`() {
        val bothInside =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = null,
                tool = tool("source" to "/data/ws/a.txt", "destination" to "/data/ws/b.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.INSIDE, bothInside)

        val mixed =
            WorkspacePathChecker.resolveZone(
                chatWorkspaceRoot = "/data/ws",
                chatWorkspaceEnv = null,
                tool = tool("source" to "/data/ws/a.txt", "destination" to "/data/other/b.txt"),
            )
        assertEquals(WorkspacePathChecker.Zone.OUTSIDE, mixed)
    }

    @Test
    fun `qualified package tool names share the protected tool family`() {
        assertTrue(WorkspacePathChecker.isWorkspaceProtectedTool("copy_file"))
        assertTrue(WorkspacePathChecker.isWorkspaceProtectedTool("extended_file_tools:copy_file"))
        assertFalse(WorkspacePathChecker.isWorkspaceProtectedTool("github:get_file_content"))
        assertFalse(WorkspacePathChecker.isWorkspaceProtectedTool("package_proxy"))
    }

    @Test
    fun `dangerous recursive delete roots are refused and normal paths are not`() {
        listOf(
            "/",
            "/storage",
            "/storage/emulated/0",
            "/sdcard",
            "/data",
            "/home",
        ).forEach { path ->
            assertTrue("root=$path", WorkspacePathChecker.isDangerousRecursiveDeleteTarget(path))
        }

        listOf(
            "/data/ws/file.txt",
            "/storage/emulated/0/Download/a.zip",
            "/home/user/project",
            "/data/ws",
        ).forEach { path ->
            assertFalse("path=$path", WorkspacePathChecker.isDangerousRecursiveDeleteTarget(path))
        }
    }

    @Test
    fun `dangerous root spelling variants are refused`() {
        // 评审 P1：这些变体与根集合的精确匹配不符，但指向同一目录，
        // deleteRecursively 会真的清空——词法规范化必须全部命中。
        listOf(
            "/sdcard/.",
            "/sdcard//",
            "//sdcard",
            "/storage/emulated/0/.",
            "/sdcard/..",
            "/data/../data",
            "/sdcard/sub/..",
            "/storage/emulated/0/../storage/emulated/0",
            "/etc/../etc",
        ).forEach { path ->
            assertTrue("variant=$path", WorkspacePathChecker.isDangerousRecursiveDeleteTarget(path))
        }
    }

    @Test
    fun `lexical normalization collapses dot and duplicate segments`() {
        assertEquals("/", WorkspacePathChecker.lexicallyNormalizePath("/"))
        assertEquals("/sdcard", WorkspacePathChecker.lexicallyNormalizePath("/sdcard/."))
        assertEquals("/sdcard", WorkspacePathChecker.lexicallyNormalizePath("//sdcard//"))
        assertEquals("/", WorkspacePathChecker.lexicallyNormalizePath("/sdcard/.."))
        assertEquals("/data", WorkspacePathChecker.lexicallyNormalizePath("/data/../data"))
        assertEquals("/data/ws", WorkspacePathChecker.lexicallyNormalizePath("/data/ws/./x/.."))
        assertEquals("", WorkspacePathChecker.lexicallyNormalizePath("   "))
        // 相对路径在根之外的 ".." 保留词法形态，不逃逸到上级目录
        assertEquals("../..", WorkspacePathChecker.lexicallyNormalizePath("a/../../a/../.."))
    }
}
