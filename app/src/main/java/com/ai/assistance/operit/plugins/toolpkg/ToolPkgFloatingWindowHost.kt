package com.ai.assistance.operit.plugins.toolpkg

import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.services.floating.ToolPkgFloatingWindowService
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal object ToolPkgFloatingWindowHost {
    const val CAPABILITY = "toolpkg.floating_window.v4"
    private val registered = AtomicBoolean(false)

    fun register() {
        if (!registered.compareAndSet(false, true)) {
            return
        }
        ToolPkgHostBridge.register(CAPABILITY, ToolPkgHostBridge.Handler(::handle))
    }

    private fun handle(request: ToolPkgHostBridge.Request): JSONObject {
        val operation = request.payload.optString("operation").trim().lowercase()
        val windowId = request.payload.optString("windowId").trim()
        require(operation == "show" || operation == "hide" || operation == "update" || operation == "get") {
            "Unsupported floating window operation: $operation"
        }
        require(windowId.isNotBlank()) { "Floating window id is required" }

        val window =
            request.packageManager
                .getToolPkgFloatingWindows(resolveContext = request.context)
                .firstOrNull { candidate ->
                    candidate.containerPackageName == request.packageName &&
                        candidate.windowId.equals(windowId, ignoreCase = true)
                } ?: error("Floating window is not registered: $windowId")

        val command =
            JSONObject()
                .put("operation", operation)
                .put("packageName", request.packageName)
                .put("windowId", window.windowId)
                .put("spec", serializeWindow(window))
        if (request.payload.has("routeArgs")) {
            command.put("routeArgs", request.payload.getJSONObject("routeArgs"))
        }
        if (request.payload.has("patch")) {
            command.put("patch", request.payload.getJSONObject("patch"))
        }
        val result =
            if (operation == "get") {
                ToolPkgFloatingWindowService.getPersistedState(request.context, command)
            } else {
                ToolPkgFloatingWindowService.dispatch(request.context, command)
            }
        if (result.optString("status").equals("error", ignoreCase = true)) {
            throw IllegalStateException(
                result.optString("errorMessage").trim().ifBlank {
                    "ToolPkg floating window operation failed"
                }
            )
        }
        return result
    }

    private fun serializeWindow(window: PackageManager.ToolPkgFloatingWindow): JSONObject {
        return JSONObject()
            .put("packageName", window.containerPackageName)
            .put("windowId", window.windowId)
            .put("contentRouteId", window.contentRouteId)
            .put("title", window.title)
            .put("description", window.description)
            .put("icon", window.icon ?: JSONObject.NULL)
            .put("widthDp", window.widthDp)
            .put("heightDp", window.heightDp)
            .put("draggable", window.draggable)
            .put("resizable", window.resizable)
            .put("snapMode", window.snapMode)
            .put("contentLayout", serializeContentLayout(window.contentLayout))
            .put("follow", window.follow?.let(::serializeFollow) ?: JSONObject.NULL)
            .put("pressFeedback", serializeFeedback(window.pressFeedback))
            .put("releaseFeedback", serializeFeedback(window.releaseFeedback))
            .put("refreshIntervalMs", window.refreshIntervalMs)
            .put("refreshFunction", window.refreshFunction ?: JSONObject.NULL)
            .put("refreshFunctionSource", window.refreshFunctionSource ?: JSONObject.NULL)
    }

    private fun serializeContentLayout(
        layout: PackageManager.ToolPkgFloatingWindowContentLayout
    ): JSONObject {
        return JSONObject()
            .put("mode", layout.mode)
            .put("widthDp", layout.widthDp)
            .put("heightDp", layout.heightDp)
            .put("scaleMode", layout.scaleMode)
    }

    private fun serializeFollow(follow: PackageManager.ToolPkgFloatingWindowFollow): JSONObject {
        return JSONObject()
            .put("windowId", follow.windowId)
            .put("placement", follow.placement)
            .put(
                "offsetDp",
                JSONObject()
                    .put("x", follow.offsetXDp.toDouble())
                    .put("y", follow.offsetYDp.toDouble())
            )
    }

    private fun serializeFeedback(feedback: PackageManager.ToolPkgFloatingWindowFeedback): JSONObject {
        return JSONObject()
            .put("soundResource", feedback.soundResource ?: JSONObject.NULL)
            .put("animation", feedback.animation?.let(::serializeAnimation) ?: JSONObject.NULL)
    }

    private fun serializeAnimation(animation: PackageManager.ToolPkgFloatingWindowAnimation): JSONObject {
        return JSONObject()
            .put("scaleX", animation.scaleX.toDouble())
            .put("scaleY", animation.scaleY.toDouble())
            .put("alpha", animation.alpha.toDouble())
            .put("translationXDp", animation.translationXDp.toDouble())
            .put("translationYDp", animation.translationYDp.toDouble())
            .put("durationMs", animation.durationMs)
            .put("easing", animation.easing)
            .put("pivotX", animation.pivotX.toDouble())
            .put("pivotY", animation.pivotY.toDouble())
    }
}
