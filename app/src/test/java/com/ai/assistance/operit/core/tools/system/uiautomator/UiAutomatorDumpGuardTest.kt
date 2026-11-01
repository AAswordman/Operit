package com.ai.assistance.operit.core.tools.system.uiautomator

import com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiAutomatorDumpGuardTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun restoresTheOriginalRotationLockAndDirection() {
        val result = runDump()
        assertEquals(0, result.exitCode)
        assertEquals("0", result.setting("accelerometer_rotation"))
        assertEquals("2", result.setting("user_rotation"))
        assertTrue(result.output.contains("dump completed"))
    }

    @Test
    fun preservesAutomaticRotationWhenItWasEnabled() {
        val result = runDump(beforeAuto = "1", afterAuto = "0")
        assertEquals(0, result.exitCode)
        assertEquals("1", result.setting("accelerometer_rotation"))
    }

    @Test
    fun restoresMissingSettingsWithoutInventingDefaults() {
        val result = runDump(beforeAuto = null, beforeRotation = null)
        assertEquals(0, result.exitCode)
        assertFalse(File(result.state, "accelerometer_rotation").exists())
        assertFalse(File(result.state, "user_rotation").exists())
    }

    @Test
    fun failedDumpStillRestoresSettingsAndKeepsItsExitCode() {
        val result = runDump(dumpExitCode = 23)
        assertEquals(23, result.exitCode)
        assertEquals("0", result.setting("accelerometer_rotation"))
        assertEquals("2", result.setting("user_rotation"))
    }

    @Test
    fun failedDumpRestoresSettingsUnderAnErrexitParentShell() {
        val result = runDump(dumpExitCode = 23, parentErrexit = true)
        assertEquals(23, result.exitCode)
        assertEquals("0", result.setting("accelerometer_rotation"))
        assertEquals("2", result.setting("user_rotation"))
    }

    @Test
    fun identityLauncherReceivesAnExecutableAndTheWholeQuotedScript() {
        val result = runDump(useIdentityLauncher = true)
        assertEquals(0, result.exitCode)
        assertEquals("0", result.setting("accelerometer_rotation"))
        assertEquals("2", result.setting("user_rotation"))
        assertTrue(result.output.contains("dump completed"))
    }

    @Test
    fun invalidRotationSnapshotStopsBeforeStartingUiAutomator() {
        val result = runDump(beforeRotation = "invalid")
        assertEquals(1, result.exitCode)
        assertFalse(File(result.state, "dump-called").exists())
        assertTrue(result.output.contains("invalid user_rotation snapshot"))
    }

    @Test
    fun snapshotReadFailureStopsBeforeStartingUiAutomator() {
        val result = runDump(readFailure = "accelerometer_rotation")
        assertEquals(31, result.exitCode)
        assertFalse(File(result.state, "dump-called").exists())
        assertEquals("0", result.setting("accelerometer_rotation"))
    }

    @Test
    fun restoreWriteFailureIsReportedAsFailure() {
        val result = runDump(writeFailure = "accelerometer_rotation")
        assertEquals(32, result.exitCode)
        assertTrue(result.output.contains("write denied"))
        assertEquals("2", result.setting("user_rotation"))
    }

    @Test
    fun directionRestoreFailureDoesNotSkipRestoringTheRotationSwitch() {
        val result = runDump(writeFailure = "user_rotation")
        assertEquals(32, result.exitCode)
        assertEquals("0", result.setting("accelerometer_rotation"))
        assertTrue(result.output.contains("write denied"))
    }

    @Test
    fun successfulWriteExitCodeDoesNotHideAnUnchangedSetting() {
        val result = runDump(ignoredWrite = "accelerometer_rotation")
        assertEquals(1, result.exitCode)
        assertTrue(result.output.contains("failed to restore accelerometer_rotation"))
    }

    @Test
    fun concurrentDumpsDoNotOverlapTheirRotationTransactions() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        List(3) {
            async(Dispatchers.Default) {
                UiAutomatorDumpGuard.execute("uiautomator dump /unused.xml") {
                    val count = active.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, count) }
                    try {
                        delay(10)
                        AndroidShellExecutor.CommandResult(success = true, stdout = "")
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
        }.awaitAll()
        assertEquals(1, peak.get())
        assertEquals(0, active.get())
    }

    private data class DumpResult(val exitCode: Int, val output: String, val state: File) {
        fun setting(name: String): String = File(state, name).readText().trim()
    }

    private fun runDump(
        beforeAuto: String? = "0",
        beforeRotation: String? = "2",
        afterAuto: String = "1",
        dumpExitCode: Int = 0,
        readFailure: String = "",
        writeFailure: String = "",
        ignoredWrite: String = "",
        parentErrexit: Boolean = false,
        useIdentityLauncher: Boolean = false,
    ): DumpResult {
        val directory = temporaryFolder.newFolder()
        val state = File(directory, "rotation state").apply { check(mkdir()) }
        val bin = File(directory, "bin").apply { check(mkdir()) }
        if (beforeAuto != null) File(state, "accelerometer_rotation").writeText(beforeAuto)
        if (beforeRotation != null) File(state, "user_rotation").writeText(beforeRotation)
        // 只在 CI 临时目录模拟 Android 命令，不访问设备或主机系统设置。
        writeExecutable(File(bin, "settings"), """
            #!/bin/sh
            [ "${'$'}1" = --user ] && [ "${'$'}2" = current ] && [ "${'$'}4" = system ] || exit 40
            operit_fixture_setting="${'$'}OPERIT_ROTATION_STATE/${'$'}5"
            case "${'$'}3" in
                get)
                    [ "${'$'}OPERIT_ROTATION_READ_FAILURE" = "${'$'}5" ] && exit 31
                    if [ -f "${'$'}operit_fixture_setting" ]; then
                        cat "${'$'}operit_fixture_setting"
                        printf '\n'
                    else
                        printf 'null\n'
                    fi
                    ;;
                put|delete)
                    if [ "${'$'}OPERIT_ROTATION_WRITE_FAILURE" = "${'$'}5" ]; then
                        echo 'write denied' >&2
                        exit 32
                    fi
                    [ "${'$'}OPERIT_ROTATION_IGNORED_WRITE" = "${'$'}5" ] && exit 0
                    if [ "${'$'}3" = put ]; then
                        printf '%s\n' "${'$'}6" > "${'$'}operit_fixture_setting"
                    else
                        rm -f "${'$'}operit_fixture_setting"
                    fi
                    ;;
                *) exit 41 ;;
            esac
        """)
        writeExecutable(File(bin, "uiautomator"), """
            #!/bin/sh
            printf 'called\n' > "${'$'}OPERIT_ROTATION_STATE/dump-called"
            printf '%s\n' "${'$'}OPERIT_ROTATION_AFTER_AUTO" > "${'$'}OPERIT_ROTATION_STATE/accelerometer_rotation"
            printf '3\n' > "${'$'}OPERIT_ROTATION_STATE/user_rotation"
            printf 'dump completed\n'
            exit "${'$'}OPERIT_ROTATION_DUMP_EXIT"
        """)
        val wrappedCommand = UiAutomatorDumpGuard.wrapCommand("uiautomator dump /unused.xml")
        val command = if (useIdentityLauncher) {
            // 模拟原生启动器直接转交 argv，检查完整脚本和引号能否穿过启动器。
            val launcher = File(bin, "identity launcher")
            writeExecutable(launcher, """
                #!/bin/sh
                exec "${'$'}@"
            """)
            "\"${launcher.absolutePath}\" $wrappedCommand"
        } else {
            wrappedCommand
        }
        val arguments = mutableListOf("sh")
        if (parentErrexit) arguments.add("-e")
        arguments.addAll(listOf("-c", command))
        val builder = ProcessBuilder(arguments).redirectErrorStream(true)
        builder.environment().apply {
            put("PATH", bin.absolutePath + File.pathSeparator + requireNotNull(System.getenv("PATH")))
            put("OPERIT_ROTATION_STATE", state.absolutePath)
            put("OPERIT_ROTATION_AFTER_AUTO", afterAuto)
            put("OPERIT_ROTATION_DUMP_EXIT", dumpExitCode.toString())
            put("OPERIT_ROTATION_READ_FAILURE", readFailure)
            put("OPERIT_ROTATION_WRITE_FAILURE", writeFailure)
            put("OPERIT_ROTATION_IGNORED_WRITE", ignoredWrite)
        }
        val process = builder.start()
        try {
            assertTrue("模拟 UIAutomator 命令未按时结束", process.waitFor(5, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            return DumpResult(process.exitValue(), output, state)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun writeExecutable(file: File, source: String) {
        file.writeText(source.trimIndent() + "\n")
        check(file.setExecutable(true))
    }
}
