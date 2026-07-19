package com.ai.assistance.operit.core.tools.javascript

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal object QuickJsNativeBridge {
    init {
        System.loadLibrary("quickjsjni")
    }

    @JvmStatic
    external fun nativeCreate(hostBridge: QuickJsNativeRuntime.HostBridge): Long

    @JvmStatic
    external fun nativeDestroy(handle: Long)

    @JvmStatic
    external fun nativeEvaluate(handle: Long, script: String, fileName: String): String

    @JvmStatic
    external fun nativeCallFunction(
        handle: Long,
        functionName: String,
        argsJson: String,
        callSite: String
    ): String

    @JvmStatic
    external fun nativeExecutePendingJobs(handle: Long, maxJobs: Int): Int

    @JvmStatic
    external fun nativeInterrupt(handle: Long)
}

class QuickJsNativeRuntime private constructor(
    private val handle: Long,
    @Suppress("unused") private val hostBridge: HostBridge
) : Closeable {

    interface HostBridge {
        fun onCall(method: String, argsJson: String?): String?
    }

    data class EvalResult(
        val success: Boolean,
        val valueJson: String?,
        val errorMessage: String?,
        val errorStack: String?,
        val errorDetailsJson: String?
    )

    companion object {
        fun create(hostBridge: HostBridge): QuickJsNativeRuntime {
            val handle = QuickJsNativeBridge.nativeCreate(hostBridge)
            require(handle != 0L) { "Failed to create QuickJS runtime" }
            return QuickJsNativeRuntime(handle, hostBridge)
        }
    }

    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()

    fun eval(script: String, fileName: String = "<eval>"): EvalResult {
        val resultJson = QuickJsNativeBridge.nativeEvaluate(requireHandle(), script, fileName)
        return parseEvalResult(resultJson)
    }

    fun callFunction(
        functionName: String,
        argsJson: String,
        callSite: String = "<call:$functionName>"
    ): EvalResult {
        val resultJson =
            QuickJsNativeBridge.nativeCallFunction(
                requireHandle(),
                functionName,
                argsJson,
                callSite
            )
        return parseEvalResult(resultJson)
    }

    fun installCompatLayerOrThrow() {
        val result = eval(
            script = buildQuickJsCompatScript(),
            fileName = "<quickjs-compat>"
        )
        executePendingJobs()
        check(result.success) {
            result.describeFailure("Failed to initialize QuickJS compat layer")
        }
    }

    fun executePendingJobs(maxJobs: Int = 128): Int {
        require(maxJobs > 0) { "maxJobs must be > 0" }
        return QuickJsNativeBridge.nativeExecutePendingJobs(requireHandle(), maxJobs)
    }

    fun dispatchTimer(timerId: Int): EvalResult {
        return eval(
            script = "(function(){ return globalThis.__operitDispatchTimer($timerId); })()",
            fileName = "<timer:$timerId>"
        )
    }

    fun clearAllTimers(): EvalResult {
        return eval(
            script = "(function(){ if (typeof globalThis.__operitClearAllTimers === 'function') { globalThis.__operitClearAllTimers(); } return null; })()",
            fileName = "<clear-all-timers>"
        )
    }

    fun interrupt() {
        synchronized(lifecycleLock) {
            if (!closed.get()) {
                QuickJsNativeBridge.nativeInterrupt(handle)
            }
        }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed.compareAndSet(false, true)) {
                QuickJsNativeBridge.nativeDestroy(handle)
            }
        }
    }

    private fun requireHandle(): Long {
        check(!closed.get()) { "QuickJS runtime already closed" }
        return handle
    }

    private fun parseEvalResult(resultJson: String): EvalResult {
        val payload = JSONObject(resultJson)
        return EvalResult(
            success = payload.optBoolean("success", false),
            valueJson = payload.optNullableString("valueJson"),
            errorMessage = payload.optNullableString("errorMessage"),
            errorStack = payload.optNullableString("errorStack"),
            errorDetailsJson = payload.optNullableString("errorDetailsJson")
        )
    }
}

internal fun QuickJsNativeRuntime.EvalResult.describeFailure(defaultMessage: String): String {
    return buildString {
        append(errorMessage ?: defaultMessage)
        if (!errorStack.isNullOrBlank()) {
            append('\n').append(errorStack)
        }
        if (!errorDetailsJson.isNullOrBlank()) {
            append("\nQuickJS details: ").append(errorDetailsJson)
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (!has(name) || isNull(name)) {
        null
    } else {
        getString(name)
    }
}
