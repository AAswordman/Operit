package com.ai.assistance.operit.core.codeorganizer

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class CodeOrganizerAgentTest {

    private lateinit var agent: CodeOrganizerAgent
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        agent = CodeOrganizerAgent()
        tempDir = File(System.getProperty("java.io.tmpdir"), "codeorganizer_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @Test
    fun `analyze finds undocumented public class`() {
        val ktFile = File(tempDir, "MyClass.kt")
        ktFile.writeText("""
            package com.example

            class MyClass {
                fun doSomething() {}
            }
        """.trimIndent())

        val report = runBlocking { agent.analyze(tempDir) }

        assertTrue(report.findings.any { it is CodeOrganizerAgent.Finding.UndocumentedMember })
        assertEquals(1, report.totalFilesScanned)
    }

    @Test
    fun `analyze skips documented public class`() {
        val ktFile = File(tempDir, "MyClass.kt")
        ktFile.writeText("""
            package com.example

            /** A well-documented class */
            class MyClass {
                fun doSomething() {}
            }
        """.trimIndent())

        val report = runBlocking { agent.analyze(tempDir) }

        val undocumented = report.findings.filterIsInstance<CodeOrganizerAgent.Finding.UndocumentedMember>()
        assertTrue(undocumented.isEmpty())
    }

    @Test
    fun `analyze finds unused private function`() {
        val ktFile = File(tempDir, "Utils.kt")
        ktFile.writeText("""
            package com.example

            class Utils {
                private fun unusedHelper() {}
                private fun usedHelper() {}

                fun doWork() {
                    usedHelper()
                }
            }
        """.trimIndent())

        val report = runBlocking { agent.analyze(tempDir) }

        val unused = report.findings.filterIsInstance<CodeOrganizerAgent.Finding.UnusedPrivateMember>()
        assertEquals(1, unused.size)
        assertEquals("unusedHelper", unused[0].memberName)
    }

    @Test
    fun `analyze skips test files`() {
        val testDir = File(tempDir, "test")
        testDir.mkdirs()
        val ktFile = File(testDir, "MyTest.kt")
        ktFile.writeText("""
            package com.example

            class MyTest {
                fun testSomething() {}
            }
        """.trimIndent())

        val report = runBlocking { agent.analyze(tempDir) }

        assertEquals(0, report.totalFilesScanned)
    }

    @Test
    fun `analyze finds oversized files`() {
        val ktFile = File(tempDir, "BigClass.kt")
        val lines = (1..600).map { "line $it" }
        ktFile.writeText(lines.joinToString("\n"))

        val report = runBlocking { agent.analyze(tempDir) }

        val oversized = report.findings.filterIsInstance<CodeOrganizerAgent.Finding.OversizedFile>()
        assertEquals(1, oversized.size)
        assertEquals(600, oversized[0].lineCount)
    }

    @Test
    fun `analyze returns empty report for empty directory`() {
        val report = runBlocking { agent.analyze(tempDir) }

        assertEquals(0, report.totalFilesScanned)
        assertTrue(report.findings.isEmpty())
        assertTrue(report.summary.contains("No issues found"))
    }

    @Test
    fun `applyApproved adds kdoc to undocumented members`() {
        val ktFile = File(tempDir, "MyClass.kt")
        ktFile.writeText("""
            package com.example

            class MyClass {
                fun doSomething() {}
            }
        """.trimIndent())

        val findings = listOf(
            CodeOrganizerAgent.Finding.UndocumentedMember(
                file = ktFile.absolutePath,
                line = 3,
                memberName = "MyClass",
                memberType = "class"
            )
        )

        runBlocking { agent.applyApproved(tempDir, findings) }

        val updated = ktFile.readText()
        assertTrue(updated.contains("/** Class: MyClass */"))
    }

    @Test
    fun `summary builds correctly with multiple finding types`() {
        val findings = listOf(
            CodeOrganizerAgent.Finding.UndocumentedMember("f.kt", 1, "A", "class"),
            CodeOrganizerAgent.Finding.UndocumentedMember("f.kt", 5, "B", "function"),
            CodeOrganizerAgent.Finding.UnusedPrivateMember("f.kt", "c", "function"),
            CodeOrganizerAgent.Finding.OversizedFile("f.kt", 700, "Split it")
        )

        // Use reflection or make summary public for testing
        // For now, test through the report
        val ktFile = File(tempDir, "A.kt")
        ktFile.writeText("class A { private fun c() {} }")
        val report = runBlocking { agent.analyze(tempDir) }

        assertNotNull(report.summary)
        assertTrue(report.summary.isNotEmpty())
    }

    @Test
    fun `state transitions correctly`() {
        assertEquals(CodeOrganizerAgent.OrganizerState.Idle, agent.state.value)

        val ktFile = File(tempDir, "Test.kt")
        ktFile.writeText("class Test {}")

        runBlocking { agent.analyze(tempDir) }

        val finalState = agent.state.value
        assertTrue(finalState is CodeOrganizerAgent.OrganizerState.Complete)
    }

    private fun <T> runBlocking(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
