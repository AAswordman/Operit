package com.ai.assistance.operit.core.tools.javascript

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPkgUiRegistrationRuntimeTest {
    @Test
    fun legacyConfigurationRegistrationUsesTheCurrentUiRuntime() {
        val repository = File(requireNotNull(System.getProperty("operit.repositoryRoot")))
        val suite = File(repository, "tools/toolpkg/tests/ui-registration.test.mjs")
        val output = File.createTempFile("toolpkg-ui-registration-", ".log")
        try {
            // 仓库 JVM 工作流使用 Node 执行真实注册桥，覆盖已有插件的配置声明。
            val process = ProcessBuilder("node", "--test", suite.absolutePath)
                .directory(repository)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()
            try {
                assertTrue("插件配置注册回归超时", process.waitFor(45, TimeUnit.SECONDS))
                assertEquals(output.readText(), 0, process.exitValue())
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            output.delete()
        }
    }
}
