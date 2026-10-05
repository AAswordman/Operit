package com.ai.assistance.operit.core.tools.permissions

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PermissionOperationRouterTest {
    private data class Result(val success: Boolean, val backend: String)
    private val granted = PermissionCapabilities(AndroidPermissionLevel.ADMIN, true, true)

    @Test fun missingShizukuUsesStandardForReadsAndWrites() = runBlocking {
        for (readOnly in listOf(true, false)) {
            var privilegedCalled = false
            val result = PermissionOperationRouter.execute(
                granted.copy(shizukuGranted = false), readOnly,
                { privilegedCalled = true; Result(true, "privileged") },
                { Result(true, "standard") }, { it.success }
            )
            assertFalse(privilegedCalled)
            assertEquals("standard", result.backend)
        }
    }

    @Test fun successfulPrivilegedReadDoesNotUseStandard() = runBlocking {
        var standardCalled = false
        val result = PermissionOperationRouter.execute(
            granted, true, { Result(true, "privileged") },
            { standardCalled = true; Result(true, "standard") }, { it.success }
        )
        assertEquals("privileged", result.backend)
        assertFalse(standardCalled)
    }

    @Test fun failedReadCanUseStandard() = runBlocking {
        val calls = mutableListOf<String>()
        val result = PermissionOperationRouter.execute(
            granted, true,
            { calls += "privileged"; Result(false, "privileged") },
            { calls += "standard"; Result(true, "standard") }, { it.success }
        )
        assertEquals(listOf("privileged", "standard"), calls)
        assertEquals("standard", result.backend)
    }

    @Test fun failedMutationIsNeverReplayed() = runBlocking {
        var standardCalled = false
        var privilegedCalls = 0
        val result = PermissionOperationRouter.execute(
            granted, false,
            { privilegedCalls++; Result(false, "privileged") },
            { standardCalled = true; Result(true, "standard") }, { it.success }
        )
        assertFalse(result.success)
        assertEquals(1, privilegedCalls)
        assertFalse(standardCalled)
    }

    @Test fun originalFailureIsPreservedIfBothReadBackendsFail() = runBlocking {
        val original = Result(false, "privileged")
        assertEquals(original, PermissionOperationRouter.execute(
            granted, true, { original }, { Result(false, "standard") }, { it.success }
        ))
    }

    @Test fun revocationAndReauthorizationAffectTheNextOperation() = runBlocking {
        val backends = mutableListOf<String>()
        for (capabilities in listOf(granted, granted.copy(shizukuRunning = false), granted)) {
            backends += PermissionOperationRouter.execute(
                capabilities, false, { Result(true, "privileged") },
                { Result(true, "standard") }, { it.success }
            ).backend
        }
        assertEquals(listOf("privileged", "standard", "privileged"), backends)
    }

    @Test(expected = CancellationException::class) fun cancellationDoesNotReplayTheOperation() = runBlocking {
        PermissionOperationRouter.execute(
            granted, true, { throw CancellationException("cancelled") },
            { error("Standard operation must not run after cancellation") }, { _: Result -> true }
        )
        Unit
    }
}
