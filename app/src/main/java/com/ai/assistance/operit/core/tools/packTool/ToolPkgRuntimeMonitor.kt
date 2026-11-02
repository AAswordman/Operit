package com.ai.assistance.operit.core.tools.packTool

import android.os.SystemClock
import java.util.ArrayDeque

internal object ToolPkgRuntimeMonitor {
    private const val MAX_LOG_ENTRIES_PER_PACKAGE = 120
    private const val MAX_LOG_MESSAGE_CHARS = 4_000
    private const val REGISTRATION_PLUGIN_PREFIX = "registerToolPkg:"

    data class MemorySample(
        val timestampMs: Long,
        val jsHeapUsedKb: Long,
        val jsMallocUsedKb: Long,
        val jsPeakMallocUsedKb: Long
    )

    data class LogEntry(
        val sequence: Long,
        val timestampMs: Long,
        val level: String,
        val event: String?,
        val functionName: String,
        val pluginId: String?,
        val message: String
    )

    data class Snapshot(
        val packageName: String,
        val activeCalls: Int,
        val startedCalls: Long,
        val completedCalls: Long,
        val failedCalls: Long,
        val lastDurationMs: Long?,
        val averageDurationMs: Long?,
        val maxDurationMs: Long?,
        val currentMemoryDeltaKb: Long?,
        val currentMemoryPeakDeltaKb: Long?,
        val lastMemoryDeltaKb: Long?,
        val lastMemoryPeakDeltaKb: Long?,
        val peakMemoryDeltaKb: Long?,
        val peakMemoryPeakDeltaKb: Long?,
        val currentMemory: MemorySample?,
        val lastMemory: MemorySample?,
        val lastStartedAtMs: Long?,
        val lastFinishedAtMs: Long?,
        val lastFunctionName: String?,
        val lastEvent: String?,
        val lastPluginId: String?,
        val logs: List<LogEntry>
    )

    @ConsistentCopyVisibility
    data class CallHandle internal constructor(val callId: String)

    private data class ActiveCall(
        val callId: String,
        val packageName: String,
        val pluginId: String?,
        val event: String?,
        val functionName: String,
        val startedAtMs: Long,
        val startedAtElapsedMs: Long,
        val startMemory: MemorySample?
    )

    private class MutablePackageStats(val packageName: String) {
        var startedCalls: Long = 0L
        var completedCalls: Long = 0L
        var failedCalls: Long = 0L
        var totalDurationMs: Long = 0L
        var lastDurationMs: Long? = null
        var maxDurationMs: Long? = null
        var lastMemoryDeltaKb: Long? = null
        var lastMemoryPeakDeltaKb: Long? = null
        var peakMemoryDeltaKb: Long? = null
        var peakMemoryPeakDeltaKb: Long? = null
        var currentMemory: MemorySample? = null
        var lastMemory: MemorySample? = null
        var lastStartedAtMs: Long? = null
        var lastFinishedAtMs: Long? = null
        var lastFunctionName: String? = null
        var lastEvent: String? = null
        var lastPluginId: String? = null
        val logs: ArrayDeque<LogEntry> = ArrayDeque()
    }

    private val lock = Any()
    private val statsByPackage = linkedMapOf<String, MutablePackageStats>()
    private val activeCalls = linkedMapOf<String, ActiveCall>()
    private var nextLogSequence = 1L

    fun beginCall(
        callId: String,
        functionName: String,
        params: Map<String, Any?>,
        startMemory: MemorySample? = null
    ): CallHandle? {
        val packageName = resolvePackageName(params) ?: return null
        val normalizedCallId = callId.trim()
        if (normalizedCallId.isBlank()) {
            return null
        }

        val now = System.currentTimeMillis()
        val activeCall =
            ActiveCall(
                callId = normalizedCallId,
                packageName = packageName,
                pluginId = resolvePluginId(params),
                event = firstNonBlank(params["eventName"], params["event"]),
                functionName = functionName.trim().ifBlank { "runtime" },
                startedAtMs = now,
                startedAtElapsedMs = SystemClock.elapsedRealtime(),
                startMemory = startMemory
            )

        synchronized(lock) {
            activeCalls[normalizedCallId] = activeCall
            statsFor(packageName).apply {
                startedCalls += 1
                lastStartedAtMs = activeCall.startedAtMs
                lastFunctionName = activeCall.functionName
                lastEvent = activeCall.event
                lastPluginId = activeCall.pluginId
                currentMemory = startMemory
                lastMemory = startMemory
            }
        }

        return CallHandle(normalizedCallId)
    }

    fun finishCall(
        handle: CallHandle?,
        success: Boolean,
        message: String? = null,
        endMemory: MemorySample? = null
    ) {
        if (handle == null) {
            return
        }

        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val activeCall = activeCalls.remove(handle.callId) ?: return
            val durationMs = (nowElapsed - activeCall.startedAtElapsedMs).coerceAtLeast(0L)
            val memory = endMemory ?: activeCall.startMemory
            val memoryDeltaKb =
                if (memory != null && activeCall.startMemory != null) {
                    memory.jsMallocUsedKb - activeCall.startMemory.jsMallocUsedKb
                } else {
                    null
                }
            val memoryPeakDeltaKb =
                if (memory != null && activeCall.startMemory != null) {
                    memory.jsPeakMallocUsedKb - activeCall.startMemory.jsMallocUsedKb
                } else {
                    null
                }
            statsFor(activeCall.packageName).apply {
                completedCalls += 1
                if (!success) {
                    failedCalls += 1
                }
                totalDurationMs += durationMs
                lastDurationMs = durationMs
                maxDurationMs = maxOf(maxDurationMs ?: durationMs, durationMs)
                lastMemoryDeltaKb = memoryDeltaKb
                lastMemoryPeakDeltaKb = memoryPeakDeltaKb
                if (memoryDeltaKb != null) {
                    peakMemoryDeltaKb = maxOf(peakMemoryDeltaKb ?: memoryDeltaKb, memoryDeltaKb)
                }
                if (memoryPeakDeltaKb != null) {
                    peakMemoryPeakDeltaKb =
                        maxOf(peakMemoryPeakDeltaKb ?: memoryPeakDeltaKb, memoryPeakDeltaKb)
                }
                if (memory != null) {
                    currentMemory = memory
                    lastMemory = memory
                }
                lastFinishedAtMs = now
                lastFunctionName = activeCall.functionName
                lastEvent = activeCall.event
                lastPluginId = activeCall.pluginId
                if (!success && !message.isNullOrBlank()) {
                    appendLogLocked(
                        stats = this,
                        timestampMs = now,
                        level = "error",
                        event = activeCall.event,
                        functionName = activeCall.functionName,
                        pluginId = activeCall.pluginId,
                        message = message.take(MAX_LOG_MESSAGE_CHARS)
                    )
                }
            }
        }
    }

    fun recordCurrentMemory(packageName: String, memory: MemorySample) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            return
        }
        synchronized(lock) {
            statsFor(normalizedPackageName).currentMemory = memory
        }
    }

    fun recordLog(
        handle: CallHandle?,
        level: String,
        message: String
    ) {
        if (handle == null) {
            return
        }

        val normalizedMessage = message.trim()
        if (normalizedMessage.isBlank()) {
            return
        }

        synchronized(lock) {
            val activeCall = activeCalls[handle.callId] ?: return
            appendLogLocked(
                stats = statsFor(activeCall.packageName),
                timestampMs = System.currentTimeMillis(),
                level = normalizeLevel(level),
                event = activeCall.event,
                functionName = activeCall.functionName,
                pluginId = activeCall.pluginId,
                message = normalizedMessage.take(MAX_LOG_MESSAGE_CHARS)
            )
        }
    }

    fun snapshot(packageName: String): Snapshot {
        val normalizedPackageName = packageName.trim()
        synchronized(lock) {
            val stats = statsByPackage[normalizedPackageName]
            val packageActiveCalls =
                activeCalls.values.filter { activeCall ->
                    activeCall.packageName == normalizedPackageName
                }
            val currentMemory = stats?.currentMemory
            val currentMemoryDeltaKb =
                currentMemory?.let { memory ->
                    packageActiveCalls
                        .mapNotNull { activeCall ->
                            activeCall.startMemory?.let { start ->
                                memory.jsMallocUsedKb - start.jsMallocUsedKb
                            }
                        }
                        .maxOrNull()
                }
            val currentMemoryPeakDeltaKb =
                currentMemory?.let { memory ->
                    packageActiveCalls
                        .mapNotNull { activeCall ->
                            activeCall.startMemory?.let { start ->
                                memory.jsPeakMallocUsedKb - start.jsMallocUsedKb
                            }
                        }
                        .maxOrNull()
                }
            if (stats == null) {
                return Snapshot(
                    packageName = normalizedPackageName,
                    activeCalls = packageActiveCalls.size,
                    startedCalls = 0L,
                    completedCalls = 0L,
                    failedCalls = 0L,
                    lastDurationMs = null,
                    averageDurationMs = null,
                    maxDurationMs = null,
                    currentMemoryDeltaKb = currentMemoryDeltaKb,
                    currentMemoryPeakDeltaKb = currentMemoryPeakDeltaKb,
                    lastMemoryDeltaKb = null,
                    lastMemoryPeakDeltaKb = null,
                    peakMemoryDeltaKb = null,
                    peakMemoryPeakDeltaKb = null,
                    currentMemory = null,
                    lastMemory = null,
                    lastStartedAtMs = null,
                    lastFinishedAtMs = null,
                    lastFunctionName = null,
                    lastEvent = null,
                    lastPluginId = null,
                    logs = emptyList()
                )
            }

            return Snapshot(
                packageName = normalizedPackageName,
                activeCalls = packageActiveCalls.size,
                startedCalls = stats.startedCalls,
                completedCalls = stats.completedCalls,
                failedCalls = stats.failedCalls,
                lastDurationMs = stats.lastDurationMs,
                averageDurationMs =
                    if (stats.completedCalls > 0L) {
                        stats.totalDurationMs / stats.completedCalls
                    } else {
                        null
                    },
                maxDurationMs = stats.maxDurationMs,
                currentMemoryDeltaKb = currentMemoryDeltaKb,
                currentMemoryPeakDeltaKb = currentMemoryPeakDeltaKb,
                lastMemoryDeltaKb = stats.lastMemoryDeltaKb,
                lastMemoryPeakDeltaKb = stats.lastMemoryPeakDeltaKb,
                peakMemoryDeltaKb = stats.peakMemoryDeltaKb,
                peakMemoryPeakDeltaKb = stats.peakMemoryPeakDeltaKb,
                currentMemory = stats.currentMemory,
                lastMemory = stats.lastMemory,
                lastStartedAtMs = stats.lastStartedAtMs,
                lastFinishedAtMs = stats.lastFinishedAtMs,
                lastFunctionName = stats.lastFunctionName,
                lastEvent = stats.lastEvent,
                lastPluginId = stats.lastPluginId,
                logs = stats.logs.toList()
            )
        }
    }

    fun clearLogs(packageName: String) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            return
        }
        synchronized(lock) {
            statsByPackage[normalizedPackageName]?.logs?.clear()
        }
    }

    private fun appendLogLocked(
        stats: MutablePackageStats,
        timestampMs: Long,
        level: String,
        event: String?,
        functionName: String,
        pluginId: String?,
        message: String
    ) {
        val entry =
            LogEntry(
                sequence = nextLogSequence++,
                timestampMs = timestampMs,
                level = level,
                event = event,
                functionName = functionName,
                pluginId = pluginId,
                message = message
            )
        stats.logs.addLast(entry)
        while (stats.logs.size > MAX_LOG_ENTRIES_PER_PACKAGE) {
            stats.logs.removeFirst()
        }
    }

    private fun statsFor(packageName: String): MutablePackageStats {
        return statsByPackage.getOrPut(packageName) { MutablePackageStats(packageName) }
    }

    private fun resolvePackageName(params: Map<String, Any?>): String? {
        firstNonBlank(
            params["toolPkgId"],
            params["containerPackageName"],
            params["__operit_ui_package_name"],
            params["__operit_ui_toolpkg_id"],
            params["__operit_package_name"],
            params["packageName"]
        )?.let { return it }

        val registrationPluginId = firstNonBlank(params["__operit_plugin_id"])
        if (registrationPluginId != null && registrationPluginId.startsWith(REGISTRATION_PLUGIN_PREFIX)) {
            val packageName = registrationPluginId.removePrefix(REGISTRATION_PLUGIN_PREFIX).trim()
            if (packageName.isNotBlank()) {
                return packageName
            }
        }

        return null
    }

    private fun resolvePluginId(params: Map<String, Any?>): String? {
        val pluginId =
            firstNonBlank(
                params["pluginId"],
                params["hookId"],
                params["__operit_plugin_id"]
            )
        if (pluginId != null && pluginId.startsWith(REGISTRATION_PLUGIN_PREFIX)) {
            return null
        }
        return pluginId
    }

    private fun normalizeLevel(level: String): String {
        return when (level.trim().lowercase()) {
            "error" -> "error"
            "warn", "warning" -> "warn"
            "debug" -> "debug"
            else -> "info"
        }
    }

    private fun firstNonBlank(vararg values: Any?): String? {
        return values
            .asSequence()
            .mapNotNull { value -> value?.toString()?.trim() }
            .firstOrNull { value -> value.isNotBlank() }
    }
}
