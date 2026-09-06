package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShellCommandSafetyTest {

    private fun assertSafe(command: String) {
        assertNull("Expected safe but rejected: $command", ShellCommandSafety.validate(command))
    }

    private fun assertDangerous(command: String) {
        assertNotNull("Expected rejection but accepted: $command", ShellCommandSafety.validate(command))
    }

    // --- Regression: innocent `format` usage must pass ---

    @Test
    fun `argument containing format is allowed`() {
        assertSafe("ffprobe -v error -show_format -show_streams /sdcard/test.mp4")
    }

    @Test
    fun `echo format is allowed`() {
        assertSafe("echo format")
    }

    @Test
    fun `text after format argument is allowed`() {
        assertSafe("grep -o format /sdcard/log.txt")
    }

    @Test
    fun `quoted text containing format is allowed`() {
        assertSafe("echo \"output format is json\"")
    }

    // --- Dangerous commands at command position must be rejected ---

    @Test
    fun `format as command is rejected`() {
        assertDangerous("format /dev/block/mmcblk0")
    }

    @Test
    fun `format with busybox prefix is rejected`() {
        assertDangerous("busybox format /dev/block/mmcblk0")
    }

    @Test
    fun `rm with recursive force is rejected`() {
        assertDangerous("rm -rf /sdcard/data")
    }

    @Test
    fun `rm with separated flags is rejected`() {
        assertDangerous("rm -r -f /sdcard/data")
    }

    @Test
    fun `rm with long flags is rejected`() {
        assertDangerous("rm --recursive --force /sdcard/data")
    }

    @Test
    fun `rm without force is allowed`() {
        assertSafe("rm -r /sdcard/cache")
        assertSafe("rm /sdcard/tmp/file.txt")
    }

    // --- Pipelines and sequences ---

    @Test
    fun `dangerous command in pipeline segment is rejected`() {
        assertDangerous("cat file.txt | format /dev/block/mmcblk0")
    }

    @Test
    fun `dangerous command after separator is rejected`() {
        assertDangerous("ls && rm -rf /tmp")
    }

    @Test
    fun `format inside quotes after pipe is allowed`() {
        assertSafe("echo format | wc -l")
    }

    // --- Embedded commands ---

    @Test
    fun `sh -c with dangerous embedded command is rejected`() {
        assertDangerous("sh -c \"rm -rf /tmp\"")
    }

    @Test
    fun `sh -c with innocent embedded command is allowed`() {
        assertSafe("sh -c \"ffprobe -show_format /sdcard/a.mp4\"")
    }

    // --- Edge cases ---

    @Test
    fun `variable assignment before dangerous command is rejected`() {
        assertDangerous("LC_ALL=C rm -rf /tmp")
    }

    @Test
    fun `wrapper prefix does not bypass checks`() {
        assertDangerous("env rm -rf /tmp")
        assertDangerous("adb shell rm -rf /data")
        assertSafe("timeout 5 ffprobe -show_format /sdcard/a.mp4")
    }

    @Test
    fun `blank command is safe`() {
        assertSafe("")
        assertSafe("   ")
    }

    @Test
    fun `command word case does not bypass checks`() {
        assertDangerous("FORMAT /dev/block/mmcblk0")
        assertDangerous("RM -RF /tmp")
    }
}