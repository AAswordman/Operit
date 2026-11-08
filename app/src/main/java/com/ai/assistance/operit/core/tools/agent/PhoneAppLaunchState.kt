package com.ai.assistance.operit.core.tools.agent

import org.json.JSONObject

/** A missing UI observation cannot revoke an accepted launch request. */
internal class PhoneAppLaunchState(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    var packageName: String? = null
        private set
    var deadlineMs: Long? = null
        private set
    var foregroundConfirmed = false
        private set
    private var awaitingInitialObservation = false
    private var observationUnavailable = false
    private var leftAcceptedApp = false
    private var reusedForeground = false

    val pendingPackage: String? get() = if (awaitingInitialObservation) packageName else null

    fun reset() {
        packageName = null
        deadlineMs = null
        foregroundConfirmed = false
        awaitingInitialObservation = false
        observationUnavailable = false
        leftAcceptedApp = false
        reusedForeground = false
    }

    fun accepted(packageName: String) = beginObservation(packageName, reused = false)

    fun reuseForeground(packageName: String) = beginObservation(packageName, reused = true)

    private fun beginObservation(packageName: String, reused: Boolean) {
        this.packageName = packageName
        deadlineMs = nowMs() + 15_000L
        foregroundConfirmed = reused
        reusedForeground = reused
        awaitingInitialObservation = true
        observationUnavailable = false
        leftAcceptedApp = false
    }

    fun foregroundConfirmed() {
        foregroundConfirmed = true
    }

    fun observationAvailable(observedPackage: String) {
        observationUnavailable = false
        foregroundConfirmed = observedPackage == packageName
        leftAcceptedApp = packageName != null && observedPackage != packageName
        if (observedPackage == packageName) awaitingInitialObservation = false
    }

    fun observationUnavailable() {
        // Preserve launch evidence even after the startup observation budget has expired.
        observationUnavailable = true
    }

    fun leftApp() {
        leftAcceptedApp = true
        foregroundConfirmed = false
        awaitingInitialObservation = false
    }

    fun suppressesLaunch(packageName: String): Boolean = this.packageName == packageName && !leftAcceptedApp

    fun prompt(): String {
        val launchedPackage = packageName ?: return ""
        return "[APP_LAUNCH_STATE] " + JSONObject()
            .put("package", launchedPackage)
            .put("launch_request", if (reusedForeground) "not_dispatched" else "accepted")
            .put("foreground_reused", reusedForeground)
            .put("foreground_confirmed", foregroundConfirmed)
            .put("ui_observation", when {
                observationUnavailable -> "unavailable"
                awaitingInitialObservation -> "pending"
                else -> "available"
            })
            .put("same_app_launch_allowed", leftAcceptedApp).toString()
    }
}
