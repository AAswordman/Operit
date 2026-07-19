package com.ai.assistance.operit.core.tools.javascript

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class OperitQuickJsEngine : Closeable {

    private val runtimeRef = AtomicReference<QuickJsNativeRuntime?>()
    private val nativeInterfaceRef = AtomicReference<Any?>()
    private val methodCache = ConcurrentHashMap<String, Method>()
    private val closed = AtomicBoolean(false)
    private val runtimeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "OperitQuickJsRuntime").apply { isDaemon = true }
    }
    private val hostDispatcher = QuickJsNativeHostDispatcher(
        dispatchTimer = ::dispatchTimerOnRuntimeThread,
        forwardCall = ::dispatchNativeCall
    )
    private val runtime = runOnRuntimeThread {
        QuickJsNativeRuntime.create(hostDispatcher).also { quickJs ->
            runtimeRef.set(quickJs)
            quickJs.installCompatLayerOrThrow()
        }
    }

    fun bindNativeInterface(instance: Any) {
        check(!closed.get()) { "QuickJS engine already closed" }
        nativeInterfaceRef.set(instance)
        methodCache.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> evaluate(script: String, fileName: String = "<eval>"): T? {
        check(!closed.get()) { "QuickJS engine already closed" }
        return runOnRuntimeThread {
            val result = runtime.eval(script, fileName)
            runtime.executePendingJobs()
            if (!result.success) {
                error(result.describeFailure("QuickJS evaluation failed"))
            }
            decodeJsonValue(result.valueJson) as T?
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> callFunction(
        functionName: String,
        argsJson: String,
        callSite: String = "<call:$functionName>"
    ): T? {
        check(!closed.get()) { "QuickJS engine already closed" }
        return runOnRuntimeThread {
            val result = runtime.callFunction(functionName, argsJson, callSite)
            runtime.executePendingJobs()
            if (!result.success) {
                error(result.describeFailure("QuickJS function call failed"))
            }
            decodeJsonValue(result.valueJson) as T?
        }
    }

    fun interrupt() {
        runtimeRef.get()?.interrupt()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        // Runtime cleanup is dispatched to the runtime executor. Interrupt first so close does
        // not queue behind JavaScript that never yields.
        interrupt()
        runCatching { runOnRuntimeThread { runtime.clearAllTimers() } }
        hostDispatcher.close()
        runtime.close()
        runtimeExecutor.shutdownNow()
        runtimeRef.set(null)
        nativeInterfaceRef.set(null)
        methodCache.clear()
    }

    private fun dispatchTimerOnRuntimeThread(timerId: Int) {
        if (closed.get()) {
            return
        }
        try {
            runtimeExecutor.execute {
                if (closed.get()) {
                    return@execute
                }
                runCatching {
                    val result = runtime.dispatchTimer(timerId)
                    runtime.executePendingJobs()
                    if (!result.success) {
                        error(result.describeFailure("QuickJS timer callback failed"))
                    }
                }.getOrElse { error ->
                    System.err.println("QuickJS timer dispatch failed: ${error.message}")
                    error.printStackTrace()
                }
            }
        } catch (error: RejectedExecutionException) {
            if (!closed.get()) {
                throw error
            }
        }
    }

    private fun <T> runOnRuntimeThread(block: () -> T): T {
        val future = runtimeExecutor.submit<T> { block() }
        try {
            return future.get()
        } catch (error: ExecutionException) {
            throw (error.cause ?: error)
        }
    }

    private fun dispatchNativeCall(methodName: String, argsJson: String?): String? {
        val target = nativeInterfaceRef.get() ?: error("NativeInterface is not bound")
        val args = decodeArgs(argsJson)
        val method = resolveMethod(target, methodName, args.size)
        val convertedArgs = method.parameterTypes.mapIndexed { index, type ->
            convertArg(args[index], type)
        }.toTypedArray()
        return method.invoke(target, *convertedArgs)?.toString()
    }

    private fun resolveMethod(target: Any, methodName: String, argCount: Int): Method {
        val cacheKey = "${target.javaClass.name}#$methodName/$argCount"
        return methodCache.getOrPut(cacheKey) {
            target.javaClass.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.size == argCount
            } ?: error("NativeInterface method not found: $methodName/$argCount")
        }
    }

    private fun decodeArgs(argsJson: String?): List<Any?> {
        if (argsJson.isNullOrBlank()) {
            return emptyList()
        }
        val parsed = JSONTokener(argsJson).nextValue()
        if (parsed !is JSONArray) {
            return emptyList()
        }
        return List(parsed.length()) { index -> normalizeJsonValue(parsed.opt(index)) }
    }

    private fun decodeJsonValue(valueJson: String?): Any? {
        if (valueJson.isNullOrBlank()) {
            return null
        }
        return normalizeJsonValue(JSONTokener(valueJson).nextValue())
    }

    private fun normalizeJsonValue(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONArray -> List(value.length()) { index -> normalizeJsonValue(value.opt(index)) }
            is JSONObject -> value.toString()
            else -> value
        }
    }

    private fun convertArg(value: Any?, parameterType: Class<*>): Any? {
        return when (parameterType) {
            java.lang.String::class.java -> value?.toString() ?: ""
            java.lang.Integer.TYPE,
            java.lang.Integer::class.java -> (value as? Number)?.toInt()
                ?: value?.toString()?.toIntOrNull()
                ?: 0
            java.lang.Long.TYPE,
            java.lang.Long::class.java -> (value as? Number)?.toLong()
                ?: value?.toString()?.toLongOrNull()
                ?: 0L
            java.lang.Boolean.TYPE,
            java.lang.Boolean::class.java -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value?.toString()?.toBooleanStrictOrNull() ?: false
            }
            java.lang.Double.TYPE,
            java.lang.Double::class.java -> (value as? Number)?.toDouble()
                ?: value?.toString()?.toDoubleOrNull()
                ?: 0.0
            java.lang.Float.TYPE,
            java.lang.Float::class.java -> (value as? Number)?.toFloat()
                ?: value?.toString()?.toFloatOrNull()
                ?: 0f
            else -> value?.toString()
        }
    }
}
