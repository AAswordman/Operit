package com.ai.assistance.operit.core.tools.javascript

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubTerminalFailureRuntimeTest {
    @Test fun packageFailureWrapperPreservesNativeTerminalResults() {
        val repository = File(requireNotNull(System.getProperty("operit.repositoryRoot")))
        val suite = File(repository, "tools/github/tests/terminal-failure.test.cjs")
        val output = File.createTempFile("github-terminal-failure-", ".log")
        try {
            val process = ProcessBuilder("node", "--test", suite.absolutePath)
                .directory(repository).redirectErrorStream(true).redirectOutput(output).start()
            try {
                assertTrue("GitHub 终端失败回归超时", process.waitFor(45, TimeUnit.SECONDS))
                assertEquals(output.readText(), 0, process.exitValue())
            } finally { if (process.isAlive) process.destroyForcibly() }
        } finally { output.delete() }
    }
}
