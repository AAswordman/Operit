package com.ai.nonoassistance.tools.permission

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RootExecutionGuardTest {

    private lateinit var guard: RootExecutionGuard

    @Before
    fun setup() {
        guard = RootExecutionGuard()
    }

    @Test
    fun `non-root commands are always allowed`() {
        val result = guard.checkRootCommand("ls -la", role = "worker")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `sudo commands are detected as root commands`() {
        assertTrue(guard.isRootCommand("sudo apt update"))
        assertTrue(guard.isRootCommand("sudo rm -rf /tmp/test"))
    }

    @Test
    fun `worker role cannot execute root commands`() {
        val result = guard.checkRootCommand("sudo apt update", role = "worker")
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `leader role can execute root commands with user approval`() {
        val result = guard.checkRootCommand("sudo apt update", role = "leader", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `leader role needs confirmation for dangerous commands`() {
        val result = guard.checkRootCommand("sudo rm -rf /data", role = "leader", userApproved = false)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.RequiresConfirmation)
    }

    @Test
    fun `always blocked commands are rejected regardless of role`() {
        val result = guard.checkRootCommand("rm -rf /", role = "leader", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `fork bomb is always blocked`() {
        val result = guard.checkRootCommand(":(){ :|:& };:", role = "leader", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Blocked)
    }

    @Test
    fun `sanitizeCommand removes force recursive flag`() {
        val sanitized = guard.sanitizeCommand("rm -rf /tmp/test")
        assertEquals("rm -r /tmp/test", sanitized)
    }

    @Test
    fun `user role can execute root commands with approval`() {
        val result = guard.checkRootCommand("sudo ls", role = "user", userApproved = true)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.Allowed)
    }

    @Test
    fun `user role needs confirmation without approval`() {
        val result = guard.checkRootCommand("sudo chmod 777 /data", role = "user", userApproved = false)
        assertTrue(result is RootExecutionGuard.RootPermissionDecision.RequiresConfirmation)
    }
}
