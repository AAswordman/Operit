package com.ai.assistance.operit.plugins.toolpkg

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal object ToolPkgHostBridge {
    private const val TAG = "ToolPkgHostBridge"

    data class Request(
        val context: Context,
        val packageManager: PackageManager,
        val packageName: String,
        val capability: String,
        val payload: JSONObject
    )

    fun interface Handler {
        fun handle(request: Request): JSONObject
    }

    private val handlers = ConcurrentHashMap<String, Handler>()

    fun register(capability: String, handler: Handler) {
        val normalizedCapability = normalize(capability, "capability")
        check(handlers.putIfAbsent(normalizedCapability, handler) == null) {
            "ToolPkg host capability already registered: $normalizedCapability"
        }
    }

    fun invoke(
        context: Context,
        packageManager: PackageManager,
        packageName: String,
        capability: String,
        payloadJson: String
    ): String {
        return try {
            val normalizedPackageName = normalize(packageName, "package name")
            val normalizedCapability = normalize(capability, "capability")
            packageManager.ensureInitialized()
            check(packageManager.isPackageEnabled(normalizedPackageName)) {
                "ToolPkg package is disabled: $normalizedPackageName"
            }
            val runtime =
                packageManager.toolPkgContainersInternal[normalizedPackageName]
                    ?: error("ToolPkg container not found: $normalizedPackageName")
            check(runtime.requiredHostCapabilities.contains(normalizedCapability)) {
                "ToolPkg host capability is not declared: $normalizedCapability"
            }
            val handler =
                handlers[normalizedCapability]
                    ?: error("ToolPkg host capability is unavailable: $normalizedCapability")
            val payload = JSONObject(payloadJson.trim().ifBlank { "{}" })
            val data =
                handler.handle(
                    Request(
                        context = context.applicationContext,
                        packageManager = packageManager,
                        packageName = normalizedPackageName,
                        capability = normalizedCapability,
                        payload = payload
                    )
                )
            JSONObject()
                .put("success", true)
                .put("schemaVersion", 1)
                .put("data", data)
                .toString()
        } catch (error: Exception) {
            AppLogger.e(
                TAG,
                "ToolPkg host bridge failed: package=$packageName, capability=$capability",
                error
            )
            JSONObject()
                .put("success", false)
                .put("schemaVersion", 1)
                .put("code", errorCode(error))
                .put("message", error.message ?: error.javaClass.simpleName)
                .toString()
        }
    }

    private fun normalize(value: String, fieldName: String): String {
        return value.trim().also { normalized ->
            require(normalized.isNotBlank()) { "ToolPkg host $fieldName is required" }
        }
    }

    private fun errorCode(error: Exception): String {
        return when (error) {
            is IllegalArgumentException -> "invalid_request"
            is IllegalStateException -> "capability_unavailable"
            else -> "host_error"
        }
    }
}
