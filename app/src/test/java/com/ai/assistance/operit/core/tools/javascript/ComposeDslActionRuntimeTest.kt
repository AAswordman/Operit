package com.ai.assistance.operit.core.tools.javascript

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeDslActionRuntimeTest {
    @Test
    fun `render callback retention passes runtime regressions`() {
        val repository = File(requireNotNull(System.getProperty("operit.repositoryRoot")))
        val suite = File(repository, "tools/compose_dsl/tests/action-retention.test.mjs")
        assertTrue("找不到 Compose DSL 运行时回归文件：$suite", suite.isFile)
        val output = File.createTempFile("compose-action-retention-", ".log")
        try {
            // CI 使用预装 Node 执行真实宿主脚本，不执行插件、网络请求或 Android UI。
            val process = ProcessBuilder("node", "--test", suite.absolutePath)
                .directory(repository)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            try {
                assertTrue("Compose DSL 运行时回归超时", process.waitFor(45, TimeUnit.SECONDS))
                assertEquals(output.readText(), 0, process.exitValue())
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            output.delete()
        }
    }
}