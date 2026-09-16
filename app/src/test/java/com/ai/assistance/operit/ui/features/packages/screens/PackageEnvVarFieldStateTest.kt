package com.ai.assistance.operit.ui.features.packages.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageEnvVarFieldStateTest {
    @Test
    fun `required variable without a value is the only error state`() {
        listOf(null, "", "   ", "\t\n").forEach { value ->
            assertEquals(
                PackageEnvVarFieldState.REQUIRED_EMPTY,
                packageEnvVarFieldState(required = true, value = value)
            )
        }
    }

    @Test
    fun `required variable keeps no error state once a value is saved`() {
        listOf("sk-1a2b3c4d", "+8613800138000", "0").forEach { value ->
            assertEquals(
                PackageEnvVarFieldState.REQUIRED_FILLED,
                packageEnvVarFieldState(required = true, value = value)
            )
        }
    }

    @Test
    fun `optional variable is never an error state regardless of value`() {
        listOf(null, "", "   ", "https://api.example.com").forEach { value ->
            assertEquals(
                PackageEnvVarFieldState.OPTIONAL,
                packageEnvVarFieldState(required = false, value = value)
            )
        }
    }

    @Test
    fun `a filled required variable stays distinct from an optional one`() {
        assertEquals(
            PackageEnvVarFieldState.REQUIRED_FILLED,
            packageEnvVarFieldState(required = true, value = "token")
        )
        assertEquals(
            PackageEnvVarFieldState.OPTIONAL,
            packageEnvVarFieldState(required = false, value = "token")
        )
    }
}
