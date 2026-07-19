package com.ai.assistance.operit.util

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal object ToolPkgWasmRuntime {
    private data class LoadedModule(
        val handle: Long,
        val fingerprint: String
    )

    private val modules = ConcurrentHashMap<String, LoadedModule>()
    private val moduleLock = Any()
    private val hexDigits = "0123456789abcdef".toCharArray()

    fun call(
        cacheKey: String,
        wasmBytes: ByteArray,
        exportName: String,
        argTypes: IntArray,
        argBits: LongArray
    ): String {
        val loaded = resolveLoadedModule(cacheKey, wasmBytes)
        return ToolPkgWasmNative.call(
            handle = loaded.handle,
            functionName = exportName,
            argTypes = argTypes,
            argBits = argBits
        )
    }

    fun close(cacheKey: String) {
        val normalizedKey = cacheKey.trim()
        if (normalizedKey.isEmpty()) {
            return
        }
        val loaded = modules.remove(normalizedKey) ?: return
        ToolPkgWasmNative.close(loaded.handle)
    }

    fun closeAllForPackage(packageName: String) {
        val prefix = "${packageName.trim()}:"
        if (prefix.length <= 1) {
            return
        }
        val keys = modules.keys.filter { key -> key.startsWith(prefix) }
        keys.forEach(::close)
    }

    fun closeAll() {
        val keys = modules.keys.toList()
        keys.forEach(::close)
    }

    private fun resolveLoadedModule(cacheKey: String, wasmBytes: ByteArray): LoadedModule {
        val normalizedKey = cacheKey.trim()
        require(normalizedKey.isNotEmpty()) { "WASM cache key is required" }
        require(wasmBytes.isNotEmpty()) { "WASM module bytes are empty" }

        val fingerprint = sha256Hex(wasmBytes)
        synchronized(moduleLock) {
            val current = modules[normalizedKey]
            if (current != null && current.fingerprint == fingerprint) {
                return current
            }
            if (current != null) {
                ToolPkgWasmNative.close(current.handle)
                modules.remove(normalizedKey)
            }

            val nativeHandle = ToolPkgWasmNative.load(wasmBytes)
            require(nativeHandle != 0L) { "WASM module load returned an empty handle" }
            val loaded = LoadedModule(
                handle = nativeHandle,
                fingerprint = fingerprint
            )
            modules[normalizedKey] = loaded
            return loaded
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = CharArray(digest.size * 2)
        digest.forEachIndexed { index, value ->
            val unsigned = value.toInt() and 0xff
            out[index * 2] = hexDigits[unsigned ushr 4]
            out[index * 2 + 1] = hexDigits[unsigned and 0x0f]
        }
        return String(out)
    }
}

internal object ToolPkgWasmNative {
    init {
        System.loadLibrary("toolpkgwasm")
    }

    external fun load(bytes: ByteArray): Long

    external fun call(
        handle: Long,
        functionName: String,
        argTypes: IntArray,
        argBits: LongArray
    ): String

    external fun close(handle: Long)
}
