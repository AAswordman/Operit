package com.ai.assistance.operit.core.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    var passed = 0
    val failures = mutableListOf<String>()
    for (case in ProviderOAuthRegressionCases.cases) {
        try {
            withTimeout(10000) { case.run() }
            println("PASS ${case.name}")
            passed++
        } catch (error: Throwable) {
            failures.add(case.name)
            System.err.println("FAIL ${case.name}")
            error.printStackTrace()
        }
    }
    println("RESULT: $passed passed, ${failures.size} failed")
    check(failures.isEmpty()) { failures.joinToString() }
}
