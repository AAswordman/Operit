package com.ai.assistance.operit.core.tools.permission

import com.ai.nonoassistance.tools.permission.RootExecutionGuard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for RootExecutionGuard integration with the tool execution pipeline.
 *
 * Verifies that:
 * - Non-root commands pass through unaffected
 * - Root/sudo commands are properly detected
 * - Worker role is always blocked from root commands
 * - Leader role requires confirmation for dangerous commands
 * - Always-blocked commands are rejected regardless of role
 * - Command sanitization works correctly
 */
class RootExecutionGuardIntegrationTest {

    private lateinit var guard: RootExecutionGuard

    @Before
    fun setUp() {
        guard = RootExecutionGuard()
    }

    // --- Non-root commands ---

    @Test
    fun `non-root command is always allowed`() {
        val result = guard.checkRootCommand("ls -la /data/data")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `non-root command with pipes is allowed`() {
        val result = guard.checkRootCommand("cat file.txt | grep pattern")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `non-root command with semicolons is allowed`() {
        val result = guard.checkRootCommand("echo hello; echo world")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    // --- Root command detection ---

    @Test
    fun `sudo command is detected as root`() {
        assertTrue(guard.isRootCommand("sudo rm -rf /tmp/test"))
    }

    @Test
    fun `su command is detected as root`() {
        assertTrue(guard.isRootCommand("su -c 'ls /data'"))
    }

    @Test
    fun `system bin command is detected as root`() {
        assertTrue(guard.isRootCommand("/system/bin/pm install app.apk"))
    }

    @Test
    fun `vendor bin command is detected as root`() {
        assertTrue(guard.isRootCommand("/vendor/bin/test"))
    }

    @Test
    fun `normal command is not detected as root`() {
        assertFalse(guard.isRootCommand("ls -la"))
    }

    // --- Always-blocked commands ---

    @Test
    fun `rm -rf root is always blocked`() {
        val result = guard.checkRootCommand("sudo rm -rf /")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `fork bomb is always blocked`() {
        val result = guard.checkRootCommand("sudo :(){ :|:& };:")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `mkfs is always blocked`() {
        val result = guard.checkRootCommand("sudo mkfs.ext4 /dev/sda1")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `chmod 777 root is always blocked`() {
        val result = guard.checkRootCommand("sudo chmod -R 777 /")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    // --- Worker role restrictions ---

    @Test
    fun `worker role is blocked from root commands`() {
        val result = guard.checkRootCommand("sudo ls /data", role = "worker")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `worker role is blocked from system bin commands`() {
        val result = guard.checkRootCommand("/system/bin/pm list packages", role = "worker")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `worker role non-root command is allowed`() {
        val result = guard.checkRootCommand("ls -la", role = "worker")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    // --- Leader role ---

    @Test
    fun `leader role with user approval allows root command`() {
        val result = guard.checkRootCommand("sudo ls /data", role = "leader", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `leader role without approval blocks dangerous command`() {
        val result = guard.checkRootCommand("sudo rm -rf /tmp/test", role = "leader", userApproved = false)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.RequiresConfirmation)
    }

    @Test
    fun `leader role without approval allows safe root command`() {
        val result = guard.checkRootCommand("sudo ls /data", role = "leader", userApproved = false)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    // --- Command sanitization ---

    @Test
    fun `sanitize removes recursive force flag`() {
        val sanitized = guard.sanitizeCommand("rm -rf /tmp/test")
        assertEquals("rm -r /tmp/test", sanitized)
    }

    @Test
    fun `sanitize removes no-preserve-root flag`() {
        val sanitized = guard.sanitizeCommand("rm -r --no-preserve-root /tmp/test")
        assertEquals("rm -r  /tmp/test", sanitized)
    }

    @Test
    fun `sanitize does not modify non-dangerous command`() {
        val sanitized = guard.sanitizeCommand("ls -la /tmp")
        assertEquals("ls -la /tmp", sanitized)
    }

    // --- Default role ---

    @Test
    fun `default role (user) with approval allows root command`() {
        val result = guard.checkRootCommand("sudo ls /data", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `default role (user) without approval blocks dangerous command`() {
        val result = guard.checkRootCommand("sudo chmod 777 /tmp", userApproved = false)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.RequiresConfirmation)
    }

    // --- Edge cases ---

    @Test
    fun `empty command is allowed`() {
        val result = guard.checkRootCommand("")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `whitespace-only command is allowed`() {
        val result = guard.checkRootCommand("   ")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `command with only sudo and no action is allowed`() {
        val result = guard.checkRootCommand("sudo ")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }
}
