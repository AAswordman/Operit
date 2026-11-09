package com.ai.assistance.operit.core.auth

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class ProviderOAuthTest(private val case: ProviderOAuthRegressionCases.Case) {
    @Test fun regression() = runBlocking { case.run() }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = ProviderOAuthRegressionCases.cases.map { arrayOf(it) }
    }
}
