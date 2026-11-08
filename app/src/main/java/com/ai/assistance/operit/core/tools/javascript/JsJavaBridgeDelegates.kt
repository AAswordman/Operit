package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.util.logging.AppLogger
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal typealias JsInterfaceCallbackInvoker = (jsObjectId: String, methodName: String, argsJson: String) -> String
internal typealias JsInterfaceReleaseInvoker = (jsObjectId: String) -> Unit

internal object JsJavaBridgeDelegates {
    private const val TAG = "JsJavaBridge"
    private const val HANDLE_KEY = "__javaHandle"
    private const val CLASS_KEY = "__javaClass"
    private const val JS_INTERFACE_KEY = "__javaJsInterface"
    private const val JS_OBJECT_ID_KEY = "__javaJsObjectId"
    private const val JS_INTERFACES_KEY = "__javaInterfaces"

    private val primitiveWrapperMap: Map<Class<*>, Class<*>> =
        mapOf(
            java.lang.Boolean.TYPE to java.lang.Boolean::class.java,
            java.lang.Byte.TYPE to java.lang.Byte::class.java,
            java.lang.Short.TYPE to java.lang.Short::class.java,
            java.lang.Integer.TYPE to java.lang.Integer::class.java,
            java.lang.Long.TYPE to java.lang.Long::class.java,
            java.lang.Float.TYPE to java.lang.Float::class.java,
            java.lang.Double.TYPE to java.lang.Double::class.java,
            java.lang.Character.TYPE to java.lang.Character::class.java
        )

    private data class ConvertedArg(
        val value: Any?,
        val score: Int
    )

    private data class MethodMatch(
        val method: Method,
        val args: Array<Any?>,
        val score: Int
    )

    private data class ConstructorMatch(
        val constructor: Constructor<*>,
        val args: Array<Any?>,
        val score: Int
    )

    private data class ConstructedJavaObject(
        val value: Any
    )

    private data class JsInterfaceBinding(
        val jsObjectId: String,
        val interfaceNames: List<String>
    )

    private data class BridgeResponse(
        val success: Boolean,
        val dataRaw: Any?,
        val error: String?
    )

    private class JsInterfaceProxyReference(
        referent: Any,
        val callbackInvoker: JsInterfaceCallbackInvoker,
        val jsObjectIds: Set<String>
    ) : PhantomReference<Any>(referent, jsInterfaceProxyReferenceQueue)

    private val jsInterfaceProxyReferenceQueue = ReferenceQueue<Any>()
    private val jsInterfaceProxyReferences =
        Collections.newSetFromMap(ConcurrentHashMap<JsInterfaceProxyReference, Boolean>())
    private val jsInterfaceLifecycleLock = Any()
    private val jsInterfaceReleaseInvokers =
        IdentityHashMap<JsInterfaceCallbackInvoker, JsInterfaceReleaseInvoker>()
    private val jsInterfaceReferenceCounts =
        IdentityHashMap<JsInterfaceCallbackInvoker, MutableMap<String, Int>>()
    private val jsInterfaceLifecycleWorkerStarted = AtomicBoolean(false)

    internal fun parseJsonObject(raw: Any?): JSONObject? {
        return when (raw) {
            is JSONObject -> raw
            is Map<*, *> -> toPlainJsonValue(raw) as? JSONObject
            is String -> {
                val trimmed = raw.trim()
                if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) {
                    null
                } else {
                    runCatching {
                        when (val token = JSONTokener(trimmed).nextValue()) {
                            is JSONObject -> token
                            is String -> JSONObject(token)
                            else -> null
                        }
                    }.getOrNull()
                }
            }

            else -> raw?.toString()?.let(::parseJsonObject)
        }
    }

    internal fun parsePlainJsonObjectToMap(raw: String): Map<String, Any?> {
        val parsed = parseJsonObject(raw) ?: return emptyMap()
        return decodePlainJsonValue(parsed) as? Map<String, Any?> ?: emptyMap()
    }

    internal fun parsePlainJsonValueOrRawString(raw: String?): Any? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) {
            return null
        }
        return runCatching {
            decodePlainJsonValue(JSONTokener(trimmed).nextValue())
        }.getOrElse {
            raw
        }
    }

    internal fun parsePlainJsonArray(raw: String): List<Any?>? {
        val normalized = raw.trim().ifBlank { "[]" }
        val token = runCatching { JSONTokener(normalized).nextValue() }.getOrNull() ?: return null
        return when (token) {
            is JSONArray -> decodePlainJsonValue(token) as? List<Any?>
            is List<*> -> token.map { item -> decodePlainJsonValue(item) }
            else -> null
        }
    }

    internal fun decodePlainJsonValue(raw: Any?): Any? {
        return decodeJsonValue(
            raw = raw,
            objectRegistry = ConcurrentHashMap(),
            interpretBridgeMarkers = false
        )
    }

    internal fun toPlainJsonValue(value: Any?): Any {
        return toJsonCompatibleValue(
            value = value,
            objectRegistry = ConcurrentHashMap(),
            bridgeAware = false
        )
    }

    internal fun toPlainJsonText(value: Any?): String {
        return when (val jsonValue = toPlainJsonValue(value)) {
            JSONObject.NULL -> "null"
            is JSONObject -> jsonValue.toString()
            is JSONArray -> jsonValue.toString()
            is String -> JSONObject.quote(jsonValue)
            is Number, is Boolean -> jsonValue.toString()
            else -> throw IllegalArgumentException(
                "value is not JSON-serializable: ${jsonValue.javaClass.name}"
            )
        }
    }

    internal fun containsBridgeMarkers(raw: Any?): Boolean {
        return when (raw) {
            null, JSONObject.NULL -> false
            is JSONObject -> {
                if (
                    (raw.has(HANDLE_KEY) && raw.has(CLASS_KEY)) ||
                        (
                            (raw.optBoolean(JS_INTERFACE_KEY, false) || raw.has(JS_OBJECT_ID_KEY)) &&
                                raw.optString(JS_OBJECT_ID_KEY).trim().isNotEmpty()
                            )
                ) {
                    true
                } else {
                    raw.keys().asSequence().any { key ->
                        containsBridgeMarkers(raw.opt(key))
                    }
                }
            }

            is JSONArray -> {
                (0 until raw.length()).any { index ->
                    containsBridgeMarkers(raw.opt(index))
                }
            }

            is Map<*, *> -> {
                raw.values.any(::containsBridgeMarkers)
            }

            is Iterable<*> -> {
                raw.any(::containsBridgeMarkers)
            }

            else -> false
        }
    }

    fun registerJsInterfaceReleaseInvoker(
        callbackInvoker: JsInterfaceCallbackInvoker,
        releaseInvoker: JsInterfaceReleaseInvoker
    ) {
        ensureJsInterfaceLifecycleWorker()
        synchronized(jsInterfaceLifecycleLock) {
            jsInterfaceReleaseInvokers[callbackInvoker] = releaseInvoker
        }
    }

    fun unregisterJsInterfaceReleaseInvoker(callbackInvoker: JsInterfaceCallbackInvoker) {
        synchronized(jsInterfaceLifecycleLock) {
            jsInterfaceReleaseInvokers.remove(callbackInvoker)
            jsInterfaceReferenceCounts.remove(callbackInvoker)
        }
    }

    fun classExists(className: String, bridgeClassLoader: ClassLoader? = null): Boolean {
        return try {
            loadClass(className, bridgeClassLoader)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun newInstance(
        className: String,
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val clazz = loadClass(className, bridgeClassLoader)
            val rawArgs = parseArgsJson(argsJson, objectRegistry)
            val constructorMatch =
                selectConstructor(
                    clazz = clazz,
                    rawArgs = rawArgs,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            ConstructedJavaObject(constructorMatch.constructor.newInstance(*constructorMatch.args))
        }
    }

    fun callStatic(
        className: String,
        methodName: String,
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val clazz = loadClass(className, bridgeClassLoader)
            val normalizedMethodName = methodName.trim()
            require(normalizedMethodName.isNotEmpty()) { "method name is required" }

            val rawArgs = parseArgsJson(argsJson, objectRegistry)
            val staticMethodMatch =
                try {
                    selectMethod(
                        clazz = clazz,
                        methodName = normalizedMethodName,
                        rawArgs = rawArgs,
                        staticOnly = true,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                } catch (_: NoSuchMethodException) {
                    null
                }
            if (staticMethodMatch != null) {
                return@runBridgeCall staticMethodMatch.method.invoke(null, *staticMethodMatch.args)
            }

            val fallbackInstance =
                findStaticFallbackInstance(clazz)
                    ?: throw NoSuchMethodException("static method '$normalizedMethodName' not found on ${clazz.name}")
            val fallbackMethodMatch =
                selectMethod(
                    clazz = fallbackInstance.javaClass,
                    methodName = normalizedMethodName,
                    rawArgs = rawArgs,
                    staticOnly = false,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            fallbackMethodMatch.method.invoke(fallbackInstance, *fallbackMethodMatch.args)
        }
    }

    fun callInstance(
        instanceHandle: String,
        methodName: String,
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val instance = requireInstance(instanceHandle, objectRegistry)
            val clazz = instance.javaClass
            val normalizedMethodName = methodName.trim()
            require(normalizedMethodName.isNotEmpty()) { "method name is required" }

            val rawArgs = parseArgsJson(argsJson, objectRegistry)
            val methodMatch =
                selectMethod(
                    clazz = clazz,
                    methodName = normalizedMethodName,
                    rawArgs = rawArgs,
                    staticOnly = false,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            methodMatch.method.invoke(instance, *methodMatch.args)
        }
    }

    fun callStaticSuspend(
        className: String,
        methodName: String,
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        callback: (String) -> Unit,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ) {
        val target = loadClass(className, bridgeClassLoader)
        callSuspendInternal(
            targetClass = target,
            instance = null,
            methodName = methodName,
            argsJson = argsJson,
            staticOnly = true,
            objectRegistry = objectRegistry,
            callback = callback,
            jsCallbackInvoker = jsCallbackInvoker,
            bridgeClassLoader = bridgeClassLoader
        )
    }

    fun callInstanceSuspend(
        instanceHandle: String,
        methodName: String,
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        callback: (String) -> Unit,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ) {
        val instance = requireInstance(instanceHandle, objectRegistry)
        callSuspendInternal(
            targetClass = instance.javaClass,
            instance = instance,
            methodName = methodName,
            argsJson = argsJson,
            staticOnly = false,
            objectRegistry = objectRegistry,
            callback = callback,
            jsCallbackInvoker = jsCallbackInvoker,
            bridgeClassLoader = bridgeClassLoader
        )
    }

    fun getStaticField(
        className: String,
        fieldName: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val clazz = loadClass(className, bridgeClassLoader)
            val normalizedFieldName = fieldName.trim()
            require(normalizedFieldName.isNotEmpty()) { "field name is required" }

            val field = findField(clazz, normalizedFieldName, staticOnly = true)
            if (field != null) {
                return@runBridgeCall field.get(null)
            }

            val getter = findGetter(clazz, normalizedFieldName, staticOnly = true)
            if (getter != null) {
                return@runBridgeCall getter.invoke(null)
            }

            val fallbackInstance = findStaticFallbackInstance(clazz)
            if (fallbackInstance != null) {
                val fallbackField = findField(fallbackInstance.javaClass, normalizedFieldName, staticOnly = false)
                if (fallbackField != null) {
                    return@runBridgeCall fallbackField.get(fallbackInstance)
                }

                val fallbackGetter =
                    findGetter(fallbackInstance.javaClass, normalizedFieldName, staticOnly = false)
                if (fallbackGetter != null) {
                    return@runBridgeCall fallbackGetter.invoke(fallbackInstance)
                }
            }

            throw NoSuchFieldException("static field/property '$normalizedFieldName' not found on ${clazz.name}")
        }
    }

    fun setStaticField(
        className: String,
        fieldName: String,
        valueJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val clazz = loadClass(className, bridgeClassLoader)
            val normalizedFieldName = fieldName.trim()
            require(normalizedFieldName.isNotEmpty()) { "field name is required" }

            val rawValue = parseSingleValueJson(valueJson, objectRegistry)
            val field = findField(clazz, normalizedFieldName, staticOnly = true)
            if (field != null && !Modifier.isFinal(field.modifiers)) {
                val converted =
                    convertArg(
                        rawValue = rawValue,
                        targetType = field.type,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                        ?: throw IllegalArgumentException(
                            "cannot assign value of type ${describeValueType(rawValue)} to ${field.type.name}"
                        )
                field.set(null, converted.value)
                return@runBridgeCall converted.value
            }

            val setter =
                findSetter(
                    clazz = clazz,
                    fieldName = normalizedFieldName,
                    rawValue = rawValue,
                    staticOnly = true,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            if (setter != null) {
                setter.first.invoke(null, setter.second)
                return@runBridgeCall setter.second
            }

            val fallbackInstance = findStaticFallbackInstance(clazz)
            if (fallbackInstance != null) {
                val fallbackField =
                    findField(fallbackInstance.javaClass, normalizedFieldName, staticOnly = false)
                if (fallbackField != null && !Modifier.isFinal(fallbackField.modifiers)) {
                    val converted =
                        convertArg(
                            rawValue = rawValue,
                            targetType = fallbackField.type,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        )
                            ?: throw IllegalArgumentException(
                                "cannot assign value of type ${describeValueType(rawValue)} to ${fallbackField.type.name}"
                            )
                    fallbackField.set(fallbackInstance, converted.value)
                    return@runBridgeCall converted.value
                }

                val fallbackSetter =
                    findSetter(
                        clazz = fallbackInstance.javaClass,
                        fieldName = normalizedFieldName,
                        rawValue = rawValue,
                        staticOnly = false,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                if (fallbackSetter != null) {
                    fallbackSetter.first.invoke(fallbackInstance, fallbackSetter.second)
                    return@runBridgeCall fallbackSetter.second
                }
            }

            throw NoSuchFieldException("writable static field/property '$normalizedFieldName' not found on ${clazz.name}")
        }
    }

    fun getInstanceField(
        instanceHandle: String,
        fieldName: String,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): String {
        return runBridgeCall(objectRegistry) {
            val instance = requireInstance(instanceHandle, objectRegistry)
            val clazz = instance.javaClass
            val normalizedFieldName = fieldName.trim()
            require(normalizedFieldName.isNotEmpty()) { "field name is required" }

            val field = findField(clazz, normalizedFieldName, staticOnly = false)
            if (field != null) {
                field.get(instance)
            } else {
                val getter =
                    findGetter(clazz, normalizedFieldName, staticOnly = false)
                        ?: throw NoSuchFieldException(
                            "field/property '$normalizedFieldName' not found on ${clazz.name}"
                        )
                getter.invoke(instance)
            }
        }
    }

    fun hasInstanceMethod(
        instanceHandle: String,
        methodName: String,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): String {
        return runBridgeCall(objectRegistry) {
            val instance = requireInstance(instanceHandle, objectRegistry)
            val clazz = instance.javaClass
            val normalizedMethodName = methodName.trim()
            require(normalizedMethodName.isNotEmpty()) { "method name is required" }
            hasMethod(clazz, normalizedMethodName, staticOnly = false)
        }
    }

    fun setInstanceField(
        instanceHandle: String,
        fieldName: String,
        valueJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker? = null,
        bridgeClassLoader: ClassLoader? = null
    ): String {
        return runBridgeCall(objectRegistry) {
            val instance = requireInstance(instanceHandle, objectRegistry)
            val clazz = instance.javaClass
            val normalizedFieldName = fieldName.trim()
            require(normalizedFieldName.isNotEmpty()) { "field name is required" }

            val rawValue = parseSingleValueJson(valueJson, objectRegistry)
            val field = findField(clazz, normalizedFieldName, staticOnly = false)
            if (field != null && !Modifier.isFinal(field.modifiers)) {
                val converted =
                    convertArg(
                        rawValue = rawValue,
                        targetType = field.type,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                        ?: throw IllegalArgumentException(
                            "cannot assign value of type ${describeValueType(rawValue)} to ${field.type.name}"
                        )
                field.set(instance, converted.value)
                converted.value
            } else {
                val setter =
                    findSetter(
                        clazz = clazz,
                        fieldName = normalizedFieldName,
                        rawValue = rawValue,
                        staticOnly = false,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                        ?: throw NoSuchFieldException(
                            "writable field/property '$normalizedFieldName' not found on ${clazz.name}"
                        )
                setter.first.invoke(instance, setter.second)
                setter.second
            }
        }
    }

    fun releaseInstance(instanceHandle: String, objectRegistry: ConcurrentHashMap<String, Any>): String {
        return runBridgeCall(objectRegistry) {
            val handle = instanceHandle.trim()
            require(handle.isNotEmpty()) { "instance handle is required" }
            objectRegistry.remove(handle) != null
        }
    }

    private fun ensureJsInterfaceLifecycleWorker() {
        if (!jsInterfaceLifecycleWorkerStarted.compareAndSet(false, true)) {
            return
        }

        Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val reference = jsInterfaceProxyReferenceQueue.remove() as? JsInterfaceProxyReference ?: continue
                    jsInterfaceProxyReferences.remove(reference)
                    releaseCollectedJsInterfaceProxy(reference)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to release collected JS interface proxy: ${e.message}", e)
                }
            }
        }.apply {
            isDaemon = true
            name = "OperitJsInterfaceRelease"
        }.start()
    }

    private fun trackJsInterfaceProxy(
        proxy: Any,
        callbackInvoker: JsInterfaceCallbackInvoker,
        jsObjectIds: Collection<String>
    ) {
        val normalizedIds =
            jsObjectIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        if (normalizedIds.isEmpty()) {
            return
        }

        ensureJsInterfaceLifecycleWorker()
        synchronized(jsInterfaceLifecycleLock) {
            if (!jsInterfaceReleaseInvokers.containsKey(callbackInvoker)) {
                return
            }
            val counts = jsInterfaceReferenceCounts.getOrPut(callbackInvoker) { LinkedHashMap() }
            normalizedIds.forEach { jsObjectId ->
                counts[jsObjectId] = (counts[jsObjectId] ?: 0) + 1
            }
        }

        jsInterfaceProxyReferences.add(
            JsInterfaceProxyReference(
                referent = proxy,
                callbackInvoker = callbackInvoker,
                jsObjectIds = normalizedIds
            )
        )
    }

    private fun releaseCollectedJsInterfaceProxy(reference: JsInterfaceProxyReference) {
        val idsToRelease = mutableListOf<String>()
        val releaseInvoker: JsInterfaceReleaseInvoker?

        synchronized(jsInterfaceLifecycleLock) {
            val counts = jsInterfaceReferenceCounts[reference.callbackInvoker]
            releaseInvoker = jsInterfaceReleaseInvokers[reference.callbackInvoker]
            if (counts == null) {
                return
            }

            reference.jsObjectIds.forEach { jsObjectId ->
                val remaining = (counts[jsObjectId] ?: 0) - 1
                if (remaining <= 0) {
                    counts.remove(jsObjectId)
                    if (releaseInvoker != null) {
                        idsToRelease += jsObjectId
                    }
                } else {
                    counts[jsObjectId] = remaining
                }
            }

            if (counts.isEmpty()) {
                jsInterfaceReferenceCounts.remove(reference.callbackInvoker)
            }
        }

        if (releaseInvoker == null) {
            return
        }

        idsToRelease.forEach { jsObjectId ->
            try {
                releaseInvoker.invoke(jsObjectId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to auto-release JS interface object $jsObjectId: ${e.message}", e)
            }
        }
    }

    private fun collectJsInterfaceObjectIds(value: Any?, output: MutableSet<String>) {
        when (value) {
            is JsInterfaceBinding -> {
                val normalized = value.jsObjectId.trim()
                if (normalized.isNotEmpty()) {
                    output += normalized
                }
            }
            is Map<*, *> -> value.values.forEach { collectJsInterfaceObjectIds(it, output) }
            is Iterable<*> -> value.forEach { collectJsInterfaceObjectIds(it, output) }
            is Array<*> -> value.forEach { collectJsInterfaceObjectIds(it, output) }
        }
    }

    private inline fun runBridgeCall(
        objectRegistry: ConcurrentHashMap<String, Any>,
        block: () -> Any?
    ): String {
        return try {
            val value = block.invoke()
            success(value = value, objectRegistry = objectRegistry)
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e
            AppLogger.e(TAG, "Java bridge invocation error: ${cause.message}", cause)
            failure(describeThrowable(cause))
        } catch (e: Exception) {
            val shouldLog =
                e !is NoSuchFieldException &&
                    e !is NoSuchMethodException
            if (shouldLog) {
                AppLogger.e(TAG, "Java bridge error: ${e.message}", e)
            }
            failure(describeThrowable(e))
        }
    }

    private fun describeThrowable(error: Throwable): String {
        val parts = ArrayList<String>()
        val seen = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current) && parts.size < 6) {
            val label = current.message?.trim().takeUnless { it.isNullOrEmpty() }
                ?: current.javaClass.name
            parts += label
            current = current.cause
        }
        return parts.joinToString(separator = " | caused by: ")
    }

    private fun callSuspendInternal(
        targetClass: Class<*>,
        instance: Any?,
        methodName: String,
        argsJson: String,
        staticOnly: Boolean,
        objectRegistry: ConcurrentHashMap<String, Any>,
        callback: (String) -> Unit,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ) {
        val normalizedMethodName = methodName.trim()
        if (normalizedMethodName.isEmpty()) {
            callback(failure("method name is required"))
            return
        }

        val rawArgs = parseArgsJson(argsJson, objectRegistry)
        val candidates =
            targetClass.methods.filter { method ->
                method.name == normalizedMethodName &&
                    Modifier.isStatic(method.modifiers) == staticOnly &&
                    method.parameterTypes.isNotEmpty() &&
                    Continuation::class.java.isAssignableFrom(method.parameterTypes.last())
            }

        var best: MethodMatch? = null
        for (method in candidates) {
            val parameterTypes = method.parameterTypes
            val argParamTypes = parameterTypes.copyOfRange(0, parameterTypes.size - 1)
            val converted =
                convertArguments(
                    parameterTypes = argParamTypes,
                    isVarArgs = method.isVarArgs,
                    rawArgs = rawArgs,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: continue

            val match = MethodMatch(method, converted.first, converted.second)
            if (best == null || match.score < best.score) {
                best = match
            }
        }

        if ((candidates.isEmpty() || best == null) && staticOnly) {
            val fallbackInstance = findStaticFallbackInstance(targetClass)
            if (fallbackInstance != null) {
                callSuspendInternal(
                    targetClass = fallbackInstance.javaClass,
                    instance = fallbackInstance,
                    methodName = methodName,
                    argsJson = argsJson,
                    staticOnly = false,
                    objectRegistry = objectRegistry,
                    callback = callback,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
                return
            }
        }
        if (candidates.isEmpty()) {
            val callType = if (staticOnly) "static" else "instance"
            callback(failure("$callType suspend method '$normalizedMethodName' not found on ${targetClass.name}"))
            return
        }

        val selected = best
        if (selected == null) {
            callback(
                failure("no suspend method '$normalizedMethodName' matched on ${targetClass.name} with ${rawArgs.size} args")
            )
            return
        }

        val completed = AtomicBoolean(false)
        val continuation =
            object : Continuation<Any?> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Any?>) {
                    if (!completed.compareAndSet(false, true)) {
                        return
                    }
                    if (result.isSuccess) {
                        callback(success(result.getOrNull(), objectRegistry))
                    } else {
                        val error = result.exceptionOrNull()
                        callback(failure(error?.message ?: error?.javaClass?.name ?: "unknown error"))
                    }
                }
            }

        try {
            val argsWithContinuation = selected.args + continuation
            val outcome = selected.method.invoke(instance, *argsWithContinuation)
            if (outcome !== COROUTINE_SUSPENDED && completed.compareAndSet(false, true)) {
                callback(success(outcome, objectRegistry))
            }
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e
            if (completed.compareAndSet(false, true)) {
                callback(failure(cause.message ?: cause.javaClass.name))
            }
        } catch (e: Exception) {
            if (completed.compareAndSet(false, true)) {
                callback(failure(e.message ?: e.javaClass.name))
            }
        }
    }

    private fun success(value: Any?, objectRegistry: ConcurrentHashMap<String, Any>): String {
        val payloadValue =
            when (value) {
                is ConstructedJavaObject -> exposeConstructedJavaObject(value.value, objectRegistry)
                else -> toJsonCompatibleValue(value, objectRegistry)
            }
        val payload =
            JSONObject()
                .put("success", true)
                .put("data", payloadValue)
        return payload.toString()
    }

    private fun failure(message: String): String {
        return JSONObject()
            .put("success", false)
            .put("message", message)
            .toString()
    }

    private fun loadClass(className: String, classLoader: ClassLoader? = null): Class<*> {
        val normalized = className.trim()
        require(normalized.isNotEmpty()) { "class name is required" }
        return when (normalized) {
            "boolean" -> java.lang.Boolean.TYPE
            "byte" -> java.lang.Byte.TYPE
            "short" -> java.lang.Short.TYPE
            "int" -> java.lang.Integer.TYPE
            "long" -> java.lang.Long.TYPE
            "float" -> java.lang.Float.TYPE
            "double" -> java.lang.Double.TYPE
            "char" -> java.lang.Character.TYPE
            "void" -> java.lang.Void.TYPE
            else -> {
                if (classLoader == null) {
                    Class.forName(normalized)
                } else {
                    try {
                        Class.forName(normalized, false, classLoader)
                    } catch (_: ClassNotFoundException) {
                        Class.forName(normalized)
                    }
                }
            }
        }
    }

    private fun requireInstance(
        instanceHandle: String,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): Any {
        val handle = instanceHandle.trim()
        require(handle.isNotEmpty()) { "instance handle is required" }
        return objectRegistry[handle]
            ?: throw IllegalArgumentException("instance handle not found or expired: $handle")
    }

    private fun parseArgsJson(
        argsJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): List<Any?> {
        val normalized = argsJson.trim()
        if (normalized.isEmpty() || normalized == "undefined" || normalized == "null") {
            return emptyList()
        }
        val raw = JSONTokener(normalized).nextValue()
        require(raw is JSONArray) { "arguments must be a JSON array" }

        val args = ArrayList<Any?>(raw.length())
        for (index in 0 until raw.length()) {
            args.add(
                decodeJsonValue(
                    raw = raw.get(index),
                    objectRegistry = objectRegistry,
                    interpretBridgeMarkers = true
                )
            )
        }
        return args
    }

    private fun parseSingleValueJson(
        valueJson: String,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): Any? {
        val normalized = valueJson.trim()
        if (normalized.isEmpty() || normalized == "undefined" || normalized == "null") {
            return null
        }
        val raw = JSONTokener(normalized).nextValue()
        return decodeJsonValue(
            raw = raw,
            objectRegistry = objectRegistry,
            interpretBridgeMarkers = true
        )
    }

    private fun decodeJsonValue(
        raw: Any?,
        objectRegistry: ConcurrentHashMap<String, Any>,
        interpretBridgeMarkers: Boolean
    ): Any? {
        return when (raw) {
            null,
            JSONObject.NULL -> null
            is JSONObject -> {
                if (interpretBridgeMarkers && raw.has(HANDLE_KEY) && raw.has(CLASS_KEY)) {
                    val handle = raw.optString(HANDLE_KEY).trim()
                    if (handle.isEmpty()) {
                        null
                    } else {
                        objectRegistry[handle]
                            ?: throw IllegalArgumentException("instance handle not found or expired: $handle")
                    }
                } else if (
                    interpretBridgeMarkers &&
                        (raw.optBoolean(JS_INTERFACE_KEY, false) || raw.has(JS_OBJECT_ID_KEY)) &&
                        raw.optString(JS_OBJECT_ID_KEY).trim().isNotEmpty()
                ) {
                    val interfaceNames = mutableListOf<String>()
                    val rawInterfaces = raw.opt(JS_INTERFACES_KEY)
                    when (rawInterfaces) {
                        is JSONArray -> {
                            for (i in 0 until rawInterfaces.length()) {
                                val name = rawInterfaces.optString(i).trim()
                                if (name.isNotEmpty()) {
                                    interfaceNames.add(name)
                                }
                            }
                        }
                        is String -> {
                            val name = rawInterfaces.trim()
                            if (name.isNotEmpty()) {
                                interfaceNames.add(name)
                            }
                        }
                    }
                    JsInterfaceBinding(
                        jsObjectId = raw.optString(JS_OBJECT_ID_KEY).trim(),
                        interfaceNames = interfaceNames
                    )
                } else {
                    val map = LinkedHashMap<String, Any?>()
                    raw.keys().forEach { key ->
                        map[key] =
                            decodeJsonValue(
                                raw = raw.opt(key),
                                objectRegistry = objectRegistry,
                                interpretBridgeMarkers = interpretBridgeMarkers
                            )
                    }
                    map
                }
            }
            is JSONArray -> {
                val list = ArrayList<Any?>(raw.length())
                for (index in 0 until raw.length()) {
                    list.add(
                        decodeJsonValue(
                            raw = raw.get(index),
                            objectRegistry = objectRegistry,
                            interpretBridgeMarkers = interpretBridgeMarkers
                        )
                    )
                }
                list
            }
            else -> raw
        }
    }

    private fun toJsonCompatibleValue(
        value: Any?,
        objectRegistry: ConcurrentHashMap<String, Any>,
        bridgeAware: Boolean = true
    ): Any {
        if (value == null) {
            return JSONObject.NULL
        }

        return when (value) {
            JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> value
            is JSONArray -> value
            is String -> value
            is Boolean -> value
            is Int -> value
            is Long -> value
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value
            is Char -> value.toString()
            is Enum<*> -> value.name
            is Class<*> -> value.name
            is Map<*, *> -> {
                val obj = JSONObject()
                value.entries.forEach { entry ->
                    val key = entry.key?.toString() ?: return@forEach
                    obj.put(
                        key,
                        toJsonCompatibleValue(
                            value = entry.value,
                            objectRegistry = objectRegistry,
                            bridgeAware = bridgeAware
                        )
                    )
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                value.forEach { item ->
                    arr.put(
                        toJsonCompatibleValue(
                            value = item,
                            objectRegistry = objectRegistry,
                            bridgeAware = bridgeAware
                        )
                    )
                }
                arr
            }
            else -> {
                if (value.javaClass.isArray) {
                    val arr = JSONArray()
                    val len = ReflectArray.getLength(value)
                    for (index in 0 until len) {
                        arr.put(
                            toJsonCompatibleValue(
                                value = ReflectArray.get(value, index),
                                objectRegistry = objectRegistry,
                                bridgeAware = bridgeAware
                            )
                        )
                    }
                    arr
                } else if (value === Unit) {
                    JSONObject.NULL
                } else {
                    if (!bridgeAware) {
                        throw IllegalArgumentException(
                            "value is not JSON-serializable: ${value.javaClass.name}"
                        )
                    }
                    val handle = UUID.randomUUID().toString()
                    objectRegistry[handle] = value
                    JSONObject()
                        .put(HANDLE_KEY, handle)
                        .put(CLASS_KEY, value.javaClass.name)
                }
            }
        }
    }

    private fun exposeConstructedJavaObject(
        value: Any,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): JSONObject {
        val handle = UUID.randomUUID().toString()
        objectRegistry[handle] = value
        return JSONObject()
            .put(HANDLE_KEY, handle)
            .put(CLASS_KEY, value.javaClass.name)
    }

    private fun toStructuredJsonValue(
        value: Any?,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Any {
        if (value == null) {
            return JSONObject.NULL
        }

        return when (value) {
            JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> {
                val copy = JSONObject()
                value.keys().forEach { key ->
                    copy.put(
                        key,
                        toStructuredJsonValue(
                            value = value.opt(key),
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        )
                    )
                }
                copy
            }
            is JSONArray -> {
                val copy = JSONArray()
                for (index in 0 until value.length()) {
                    copy.put(
                        toStructuredJsonValue(
                            value = value.opt(index),
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        )
                    )
                }
                copy
            }
            is Map<*, *> -> {
                val obj = JSONObject()
                value.entries.forEach { entry ->
                    val key = entry.key?.toString() ?: return@forEach
                    obj.put(
                        key,
                        toStructuredJsonValue(
                            value = entry.value,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        )
                    )
                }
                obj
            }
            is Iterable<*> -> {
                val arr = JSONArray()
                value.forEach { item ->
                    arr.put(
                        toStructuredJsonValue(
                            value = item,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        )
                    )
                }
                arr
            }
            else -> {
                if (value.javaClass.isArray) {
                    val arr = JSONArray()
                    val len = ReflectArray.getLength(value)
                    for (index in 0 until len) {
                        arr.put(
                            toStructuredJsonValue(
                                value = ReflectArray.get(value, index),
                                objectRegistry = objectRegistry,
                                jsCallbackInvoker = jsCallbackInvoker,
                                bridgeClassLoader = bridgeClassLoader
                            )
                        )
                    }
                    arr
                } else {
                    convertArg(
                        rawValue = value,
                        targetType = Any::class.java,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )?.value ?: value
                }
            }
        }
    }

    private fun selectConstructor(
        clazz: Class<*>,
        rawArgs: List<Any?>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): ConstructorMatch {
        val candidates = clazz.constructors
        if (candidates.isEmpty()) {
            throw NoSuchMethodException(buildConstructorUnavailableMessage(clazz))
        }

        var best: ConstructorMatch? = null
        for (constructor in candidates) {
            val converted =
                convertArguments(
                    parameterTypes = constructor.parameterTypes,
                    isVarArgs = constructor.isVarArgs,
                    rawArgs = rawArgs,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: continue

            val match = ConstructorMatch(constructor, converted.first, converted.second)
            if (best == null || match.score < best.score) {
                best = match
            }
        }

        return best
            ?: throw NoSuchMethodException(
                buildConstructorMismatchMessage(clazz, rawArgs)
            )
    }

    private fun buildConstructorUnavailableMessage(clazz: Class<*>): String {
        val suffix =
            when {
                clazz.isInterface ->
                    " ${clazz.name} is an interface; use Java.implement(\"${clazz.name}\", impl) or pass the callback/object into an API that explicitly expects this interface."
                Modifier.isAbstract(clazz.modifiers) ->
                    " ${clazz.name} is abstract and cannot be instantiated directly."
                else -> ""
            }
        return "no public constructor available for ${clazz.name}$suffix"
    }

    private fun buildConstructorMismatchMessage(clazz: Class<*>, rawArgs: List<Any?>): String {
        val hasJsInterfaceArg = rawArgs.any { it is JsInterfaceBinding }
        val hint =
            if (hasJsInterfaceArg) {
                " If one of these arguments is meant to satisfy a Java interface, prefer Java.implement(\"interface.name\", impl) so overload resolution is explicit."
            } else {
                ""
            }
        return "no constructor matched for ${clazz.name} with ${rawArgs.size} args$hint"
    }

    private fun selectMethod(
        clazz: Class<*>,
        methodName: String,
        rawArgs: List<Any?>,
        staticOnly: Boolean,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): MethodMatch {
        val candidates =
            clazz.methods.filter { method ->
                method.name == methodName && Modifier.isStatic(method.modifiers) == staticOnly
            }

        if (candidates.isEmpty()) {
            val callType = if (staticOnly) "static" else "instance"
            throw NoSuchMethodException("$callType method '$methodName' not found on ${clazz.name}")
        }

        var best: MethodMatch? = null
        for (method in candidates) {
            val converted =
                convertArguments(
                    parameterTypes = method.parameterTypes,
                    isVarArgs = method.isVarArgs,
                    rawArgs = rawArgs,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: continue

            val match = MethodMatch(method, converted.first, converted.second)
            if (best == null || match.score < best.score) {
                best = match
            }
        }

        return best
            ?: throw NoSuchMethodException(
                "no method '$methodName' matched on ${clazz.name} with ${rawArgs.size} args"
            )
    }

    private fun convertArguments(
        parameterTypes: Array<Class<*>>,
        isVarArgs: Boolean,
        rawArgs: List<Any?>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Pair<Array<Any?>, Int>? {
        if (!isVarArgs) {
            if (parameterTypes.size != rawArgs.size) {
                return null
            }
            val out = arrayOfNulls<Any?>(parameterTypes.size)
            var score = 0
            for (index in parameterTypes.indices) {
                val converted =
                    convertArg(
                        rawValue = rawArgs[index],
                        targetType = parameterTypes[index],
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    ) ?: return null
                out[index] = converted.value
                score += converted.score
            }
            return Pair(out, score)
        }

        val fixedCount = parameterTypes.size - 1
        if (rawArgs.size < fixedCount) {
            return null
        }

        val out = arrayOfNulls<Any?>(parameterTypes.size)
        var score = 2

        for (index in 0 until fixedCount) {
            val converted =
                convertArg(
                    rawValue = rawArgs[index],
                    targetType = parameterTypes[index],
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: return null
            out[index] = converted.value
            score += converted.score
        }

        val varargArrayType = parameterTypes.last()
        val componentType = varargArrayType.componentType ?: return null
        val rawVarargValues =
            if (
                rawArgs.size == parameterTypes.size &&
                rawArgs.lastOrNull() is List<*> &&
                !componentType.isArray
            ) {
                @Suppress("UNCHECKED_CAST")
                rawArgs.subList(0, fixedCount) + (rawArgs.last() as List<Any?>)
            } else {
                rawArgs
            }
        val varargLength = rawVarargValues.size - fixedCount
        val varargArray = ReflectArray.newInstance(componentType, varargLength)
        for (offset in 0 until varargLength) {
            val converted =
                convertArg(
                    rawValue = rawVarargValues[fixedCount + offset],
                    targetType = componentType,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: return null
            ReflectArray.set(varargArray, offset, converted.value)
            score += converted.score
        }
        out[out.lastIndex] = varargArray

        return Pair(out, score)
    }

    private fun convertArg(
        rawValue: Any?,
        targetType: Class<*>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): ConvertedArg? {
        if (rawValue == null) {
            return if (targetType.isPrimitive) {
                null
            } else {
                ConvertedArg(null, 4)
            }
        }

        if (rawValue is JsInterfaceBinding) {
            val proxy =
                createJsInterfaceProxy(
                    binding = rawValue,
                    targetType = targetType,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            if (proxy != null) {
                return ConvertedArg(proxy, 2)
            }
        }

        val wrapper = primitiveWrapperMap[targetType] ?: targetType

        if (wrapper == Any::class.java || wrapper == Object::class.java) {
            if (rawValue is JsInterfaceBinding) {
                val proxy =
                    createJsInterfaceProxy(
                        binding = rawValue,
                        targetType = wrapper,
                        objectRegistry = objectRegistry,
                        jsCallbackInvoker = jsCallbackInvoker,
                        bridgeClassLoader = bridgeClassLoader
                    )
                if (proxy != null) {
                    return ConvertedArg(proxy, 3)
                }
            }
            return ConvertedArg(rawValue, 10)
        }

        if (
            rawValue is Map<*, *> &&
                wrapper.isInterface &&
                !Map::class.java.isAssignableFrom(wrapper) &&
                !Collection::class.java.isAssignableFrom(wrapper)
        ) {
            val proxy =
                createJsInterfaceProxyFromMap(
                    rawValue = rawValue,
                    targetType = wrapper,
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            if (proxy != null) {
                return ConvertedArg(proxy, 3)
            }
        }

        if (wrapper.isInstance(rawValue)) {
            return ConvertedArg(rawValue, 0)
        }

        if (wrapper == String::class.java) {
            return ConvertedArg(rawValue.toString(), 3)
        }

        if (wrapper == java.lang.Boolean::class.java) {
            return convertToBoolean(rawValue)
        }

        if (Number::class.java.isAssignableFrom(wrapper)) {
            return convertToNumber(rawValue, wrapper)
        }

        if (wrapper == java.lang.Character::class.java) {
            return convertToChar(rawValue)
        }

        if (wrapper.isEnum) {
            return convertToEnum(rawValue, wrapper)
        }

        if (wrapper == Class::class.java && rawValue is String) {
            return try {
                ConvertedArg(loadClass(rawValue, bridgeClassLoader), 4)
            } catch (_: Exception) {
                null
            }
        }

        if (wrapper == JSONObject::class.java) {
            return when (rawValue) {
                is Map<*, *> -> {
                    val obj =
                        toStructuredJsonValue(
                            value = rawValue,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        ) as JSONObject
                    ConvertedArg(obj, 5)
                }
                is String -> {
                    try {
                        ConvertedArg(JSONObject(rawValue), 6)
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }
        }

        if (wrapper == JSONArray::class.java) {
            return when (rawValue) {
                is List<*> -> {
                    val arr =
                        toStructuredJsonValue(
                            value = rawValue,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        ) as JSONArray
                    ConvertedArg(arr, 5)
                }
                is String -> {
                    try {
                        ConvertedArg(JSONArray(rawValue), 6)
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }
        }

        if (wrapper.isArray) {
            if (rawValue is List<*>) {
                val componentType = wrapper.componentType
                val arr = ReflectArray.newInstance(componentType, rawValue.size)
                var score = 5
                for (index in rawValue.indices) {
                    val converted =
                        convertArg(
                            rawValue = rawValue[index],
                            targetType = componentType,
                            objectRegistry = objectRegistry,
                            jsCallbackInvoker = jsCallbackInvoker,
                            bridgeClassLoader = bridgeClassLoader
                        ) ?: return null
                    ReflectArray.set(arr, index, converted.value)
                    score += converted.score
                }
                return ConvertedArg(arr, score)
            }
            if (rawValue.javaClass.isArray && wrapper.isAssignableFrom(rawValue.javaClass)) {
                return ConvertedArg(rawValue, 2)
            }
        }

        if (Collection::class.java.isAssignableFrom(wrapper) && rawValue is List<*>) {
            return ConvertedArg(rawValue.toMutableList(), 7)
        }

        if (Map::class.java.isAssignableFrom(wrapper) && rawValue is Map<*, *>) {
            return ConvertedArg(rawValue, 6)
        }

        if (wrapper.isAssignableFrom(rawValue.javaClass)) {
            return ConvertedArg(rawValue, 1)
        }

        return null
    }

    private fun createJsInterfaceProxy(
        binding: JsInterfaceBinding,
        targetType: Class<*>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Any? {
        val callbackInvoker = jsCallbackInvoker ?: return null

        val interfaceClasses = mutableListOf<Class<*>>()

        if (targetType.isInterface) {
            interfaceClasses.add(targetType)
        } else {
            binding.interfaceNames.forEach { name ->
                try {
                    val loaded = loadClass(name, bridgeClassLoader)
                    if (loaded.isInterface) {
                        interfaceClasses.add(loaded)
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (interfaceClasses.isEmpty()) {
            return null
        }

        val deduped = LinkedHashMap<String, Class<*>>()
        interfaceClasses.forEach { cls ->
            deduped[cls.name] = cls
        }
        val proxyInterfaces = deduped.values.toTypedArray()

        val loader =
            bridgeClassLoader
                ?: proxyInterfaces.firstOrNull()?.classLoader
                ?: JsJavaBridgeDelegates::class.java.classLoader

        val proxy =
            Proxy.newProxyInstance(loader, proxyInterfaces) { proxy, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" ->
                        "JsInterfaceProxy(${binding.jsObjectId}) implements ${proxyInterfaces.joinToString { it.simpleName }}"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

            invokeJsInterfaceBinding(
                binding = binding,
                method = method,
                args = args ?: emptyArray(),
                objectRegistry = objectRegistry,
                callbackInvoker = callbackInvoker,
                jsCallbackInvoker = jsCallbackInvoker,
                bridgeClassLoader = bridgeClassLoader
            )
        }
        trackJsInterfaceProxy(proxy, callbackInvoker, listOf(binding.jsObjectId))
        return proxy
    }

    private fun createJsInterfaceProxyFromMap(
        rawValue: Map<*, *>,
        targetType: Class<*>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Any? {
        val callbackInvoker = jsCallbackInvoker ?: return null
        if (!targetType.isInterface) {
            return null
        }

        val memberMap = LinkedHashMap<String, Any?>()
        rawValue.entries.forEach { entry ->
            val key = entry.key?.toString()?.trim().orEmpty()
            if (key.isNotEmpty()) {
                memberMap[key] = entry.value
            }
        }
        if (memberMap.isEmpty()) {
            return null
        }

        val loader =
            bridgeClassLoader
                ?: targetType.classLoader
                ?: JsJavaBridgeDelegates::class.java.classLoader

        val proxy =
            Proxy.newProxyInstance(loader, arrayOf(targetType)) { proxy, method, args ->
            if (method.declaringClass == Any::class.java) {
                return@newProxyInstance when (method.name) {
                    "toString" -> "JsInterfaceProxy(map) implements ${targetType.simpleName}"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }

            val runtimeArgs = args ?: emptyArray()
            val resolved = resolveMapInterfaceMember(memberMap, method, runtimeArgs)
            if (!resolved.first) {
                if (isVoidLikeReturnType(method.returnType)) {
                    return@newProxyInstance null
                }
                throw IllegalStateException(
                    "JS interface map does not provide implementation for method ${method.name}"
                )
            }

            val mappedValue = resolved.second
            if (mappedValue is JsInterfaceBinding) {
                return@newProxyInstance invokeJsInterfaceBinding(
                    binding = mappedValue,
                    method = method,
                    args = runtimeArgs,
                    objectRegistry = objectRegistry,
                    callbackInvoker = callbackInvoker,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                )
            }

            if (isVoidLikeReturnType(method.returnType)) {
                return@newProxyInstance null
            }

            adaptReturnValue(
                value = mappedValue,
                targetType = method.returnType,
                objectRegistry = objectRegistry,
                jsCallbackInvoker = jsCallbackInvoker,
                bridgeClassLoader = bridgeClassLoader
            )
        }
        val jsObjectIds = linkedSetOf<String>().also { collectJsInterfaceObjectIds(memberMap, it) }
        trackJsInterfaceProxy(proxy, callbackInvoker, jsObjectIds)
        return proxy
    }

    private fun resolveMapInterfaceMember(
        memberMap: MutableMap<String, Any?>,
        method: Method,
        args: Array<out Any?>
    ): Pair<Boolean, Any?> {
        val methodName = method.name
        if (memberMap.containsKey(methodName)) {
            return Pair(true, memberMap[methodName])
        }

        val propertyName = accessorPropertyName(methodName)
        if (propertyName != null) {
            if (methodName.startsWith("set") && args.size == 1) {
                memberMap[propertyName] = args[0]
                return Pair(true, null)
            }
            if (args.isEmpty() && memberMap.containsKey(propertyName)) {
                return Pair(true, memberMap[propertyName])
            }
        }

        return Pair(false, null)
    }

    private fun accessorPropertyName(methodName: String): String? {
        val propertyBase =
            when {
                methodName.startsWith("get") && methodName.length > 3 -> methodName.substring(3)
                methodName.startsWith("is") && methodName.length > 2 -> methodName.substring(2)
                methodName.startsWith("set") && methodName.length > 3 -> methodName.substring(3)
                else -> null
            } ?: return null

        if (propertyBase.isEmpty()) {
            return null
        }

        return propertyBase.replaceFirstChar { ch ->
            if (ch.isUpperCase()) ch.lowercase(Locale.ROOT) else ch.toString()
        }
    }

    private fun isVoidLikeReturnType(returnType: Class<*>): Boolean {
        return returnType == java.lang.Void.TYPE ||
            returnType == Void::class.java ||
            returnType.name == "kotlin.Unit"
    }

    private fun invokeJsInterfaceBinding(
        binding: JsInterfaceBinding,
        method: Method,
        args: Array<out Any?>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        callbackInvoker: JsInterfaceCallbackInvoker,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Any? {
        val argsJson = serializeCallbackArgs(args, objectRegistry)
        val rawResponse = callbackInvoker.invoke(binding.jsObjectId, method.name, argsJson)
        val response = parseBridgeResponse(rawResponse)

        if (!response.success) {
            if (isVoidLikeReturnType(method.returnType)) {
                AppLogger.e(
                    TAG,
                    "JS interface callback failed for void method ${method.name}: ${response.error}"
                )
                return null
            }
            throw IllegalStateException(
                response.error ?: "JS interface callback failed for method ${method.name}"
            )
        }

        if (isVoidLikeReturnType(method.returnType)) {
            return null
        }

        val decoded = decodeJsonValue(
            raw = response.dataRaw,
            objectRegistry = objectRegistry,
            interpretBridgeMarkers = true
        )
        return adaptReturnValue(
            value = decoded,
            targetType = method.returnType,
            objectRegistry = objectRegistry,
            jsCallbackInvoker = jsCallbackInvoker,
            bridgeClassLoader = bridgeClassLoader
        )
    }

    private fun serializeCallbackArgs(
        args: Array<out Any?>,
        objectRegistry: ConcurrentHashMap<String, Any>
    ): String {
        val arr = JSONArray()
        args.forEach { arg ->
            arr.put(toJsonCompatibleValue(arg, objectRegistry))
        }
        return arr.toString()
    }

    private fun parseBridgeResponse(raw: String): BridgeResponse {
        if (raw.isBlank()) {
            return BridgeResponse(success = false, dataRaw = null, error = "empty bridge response")
        }

        return try {
            val token = JSONTokener(raw).nextValue()
            if (token is JSONObject) {
                BridgeResponse(
                    success = token.optBoolean("success", false),
                    dataRaw = token.opt("data"),
                    error = token.optString("message").ifBlank { null }
                )
            } else {
                BridgeResponse(
                    success = false,
                    dataRaw = null,
                    error = "invalid bridge response format"
                )
            }
        } catch (e: Exception) {
            BridgeResponse(
                success = false,
                dataRaw = null,
                error = "failed to parse bridge response: ${e.message}"
            )
        }
    }

    private fun adaptReturnValue(
        value: Any?,
        targetType: Class<*>,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Any? {
        if (targetType == java.lang.Void.TYPE || targetType == Void::class.java) {
            return null
        }

        if (value == null) {
            if (targetType.isPrimitive) {
                return defaultPrimitiveValue(targetType)
            }
            return null
        }

        val converted =
            convertArg(
                rawValue = value,
                targetType = targetType,
                objectRegistry = objectRegistry,
                jsCallbackInvoker = jsCallbackInvoker,
                bridgeClassLoader = bridgeClassLoader
            )

        if (converted != null) {
            return converted.value
        }

        if (targetType.isAssignableFrom(value.javaClass)) {
            return value
        }

        throw IllegalArgumentException(
            "cannot convert callback return type ${describeValueType(value)} to ${targetType.name}"
        )
    }

    private fun defaultPrimitiveValue(type: Class<*>): Any {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> 0
        }
    }

    private fun convertToBoolean(rawValue: Any): ConvertedArg? {
        return when (rawValue) {
            is Boolean -> ConvertedArg(rawValue, 0)
            is Number -> ConvertedArg(rawValue.toInt() != 0, 4)
            is String -> {
                when (rawValue.trim().lowercase(Locale.ROOT)) {
                    "true", "1", "yes", "y" -> ConvertedArg(true, 5)
                    "false", "0", "no", "n" -> ConvertedArg(false, 5)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun convertToChar(rawValue: Any): ConvertedArg? {
        return when (rawValue) {
            is Char -> ConvertedArg(rawValue, 0)
            is Number -> ConvertedArg(rawValue.toInt().toChar(), 5)
            is String -> {
                val normalized = rawValue.trim()
                if (normalized.length == 1) {
                    ConvertedArg(normalized[0], 4)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun convertToEnum(rawValue: Any, enumType: Class<*>): ConvertedArg? {
        val constants = enumType.enumConstants ?: return null
        return when (rawValue) {
            is String -> {
                constants.firstOrNull { enumConstant ->
                    val enumName = (enumConstant as Enum<*>).name
                    enumName.equals(rawValue.trim(), ignoreCase = true)
                }?.let { ConvertedArg(it, 5) }
            }
            is Number -> {
                val index = rawValue.toInt()
                if (index in constants.indices) {
                    ConvertedArg(constants[index], 6)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun convertToNumber(rawValue: Any, numberType: Class<*>): ConvertedArg? {
        val parsed: Number =
            when (rawValue) {
                is Number -> rawValue
                is String -> rawValue.trim().toDoubleOrNull() ?: return null
                is Boolean -> if (rawValue) 1 else 0
                else -> return null
            }

        return try {
            val converted: Any =
                when (numberType) {
                    java.lang.Byte::class.java -> parsed.toByte()
                    java.lang.Short::class.java -> parsed.toShort()
                    java.lang.Integer::class.java -> parsed.toInt()
                    java.lang.Long::class.java -> parsed.toLong()
                    java.lang.Float::class.java -> parsed.toFloat()
                    java.lang.Double::class.java -> parsed.toDouble()
                    else -> return null
                }
            val score = if (rawValue is Number) 2 else 5
            ConvertedArg(converted, score)
        } catch (_: Exception) {
            null
        }
    }

    private fun findField(clazz: Class<*>, fieldName: String, staticOnly: Boolean): Field? {
        return clazz.fields.firstOrNull { field ->
            field.name == fieldName && Modifier.isStatic(field.modifiers) == staticOnly
        }
    }

    private fun hasMethod(clazz: Class<*>, methodName: String, staticOnly: Boolean): Boolean {
        return clazz.methods.any { method ->
            method.name == methodName && Modifier.isStatic(method.modifiers) == staticOnly
        }
    }

    private fun findStaticFallbackInstance(clazz: Class<*>): Any? {
        return findNamedStaticInstance(clazz, "Companion")
            ?: findNamedStaticInstance(clazz, "INSTANCE")
    }

    private fun findNamedStaticInstance(clazz: Class<*>, fieldName: String): Any? {
        return runCatching {
            findField(clazz, fieldName, staticOnly = true)?.get(null)
                ?: findGetter(clazz, fieldName, staticOnly = true)?.invoke(null)
        }.getOrNull()
    }

    private fun findGetter(clazz: Class<*>, fieldName: String, staticOnly: Boolean): Method? {
        val normalized = fieldName.trim()
        if (normalized.isEmpty()) {
            return null
        }

        val capitalized =
            normalized.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }

        val candidates = listOf("get$capitalized", "is$capitalized")
        return clazz.methods.firstOrNull { method ->
            method.parameterCount == 0 &&
                method.name in candidates &&
                Modifier.isStatic(method.modifiers) == staticOnly
        }
    }

    private fun findSetter(
        clazz: Class<*>,
        fieldName: String,
        rawValue: Any?,
        staticOnly: Boolean,
        objectRegistry: ConcurrentHashMap<String, Any>,
        jsCallbackInvoker: JsInterfaceCallbackInvoker?,
        bridgeClassLoader: ClassLoader?
    ): Pair<Method, Any?>? {
        val normalized = fieldName.trim()
        if (normalized.isEmpty()) {
            return null
        }

        val capitalized =
            normalized.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }

        val candidates =
            clazz.methods.filter { method ->
                method.name == "set$capitalized" &&
                    method.parameterCount == 1 &&
                    Modifier.isStatic(method.modifiers) == staticOnly
            }

        var best: Pair<Method, Any?>? = null
        var bestScore = Int.MAX_VALUE

        for (candidate in candidates) {
            val converted =
                convertArg(
                    rawValue = rawValue,
                    targetType = candidate.parameterTypes[0],
                    objectRegistry = objectRegistry,
                    jsCallbackInvoker = jsCallbackInvoker,
                    bridgeClassLoader = bridgeClassLoader
                ) ?: continue
            if (converted.score < bestScore) {
                best = Pair(candidate, converted.value)
                bestScore = converted.score
            }
        }

        return best
    }

    private fun describeValueType(value: Any?): String {
        return value?.javaClass?.name ?: "null"
    }
}
