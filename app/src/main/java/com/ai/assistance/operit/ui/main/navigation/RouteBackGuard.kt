package com.ai.assistance.operit.ui.main.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

class RouteBackGuardRegistry {
    private data class Registration(
        val token: Any,
        val handler: suspend () -> Boolean
    )

    private val registrations = mutableMapOf<String, Registration>()
    private val lock = Any()

    fun register(routeInstanceId: String, handler: suspend () -> Boolean): () -> Unit {
        val token = Any()
        synchronized(lock) {
            registrations[routeInstanceId] = Registration(token, handler)
        }
        return {
            synchronized(lock) {
                if (registrations[routeInstanceId]?.token === token) {
                    registrations.remove(routeInstanceId)
                }
            }
        }
    }

    fun hasGuard(routeInstanceId: String): Boolean =
        synchronized(lock) { registrations.containsKey(routeInstanceId) }

    suspend fun canNavigateBack(routeInstanceId: String): Boolean {
        val handler = synchronized(lock) { registrations[routeInstanceId]?.handler }
        return handler?.invoke() ?: true
    }
}

val LocalRouteBackGuardRegistry = compositionLocalOf<RouteBackGuardRegistry?> { null }
val LocalRouteInstanceId = compositionLocalOf<String?> { null }

@Composable
fun RegisterRouteBackGuard(handler: suspend () -> Boolean) {
    val registry = LocalRouteBackGuardRegistry.current
    val routeInstanceId = LocalRouteInstanceId.current
    val latestHandler by rememberUpdatedState(handler)

    DisposableEffect(registry, routeInstanceId) {
        val unregister =
            if (registry != null && routeInstanceId != null) {
                registry.register(routeInstanceId) { latestHandler() }
            } else {
                {}
            }
        onDispose { unregister() }
    }
}
