package com.ai.assistance.operit.data.preferences

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ThemeTargetOperationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> runTransition(action: suspend () -> T): T {
        return mutex.withLock {
            action()
        }
    }
}
