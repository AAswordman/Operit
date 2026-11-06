package com.ai.assistance.operit.core.tools.packTool.floating

import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * 插件悬浮窗控制器，行为契约逐条对齐 Operit2 的 RuntimeFloatingWindowController：
 *
 * - 属主鉴权：每条命令携带插件包名，窗口 ID 只在插件内命名空间生效
 * - 命令串行化：synchronized 单锁顺序执行
 * - ID 校验：trim 后 1-128 字符；重复 open 抛错；单插件上限 16 个窗口
 * - update：options 补丁合并 + state 浅合并，不重建 UI 会话
 * - show/hide 保留会话；close 释放会话并注销
 * - 审计：插件被禁用后强制关窗；窗口被外部移除后修剪注册表
 *
 * 纯逻辑，不依赖 Android UI；窗口操作经 [ToolPkgFloatingWindowSurface] 抽象。
 */
class ToolPkgFloatingWindowController(
    private val surfaceFactory: ToolPkgFloatingWindowSurfaceFactory,
    private val capabilitiesProvider: () -> JSONObject,
    private val requestPermissionAction: () -> Boolean,
    private val enabledOwnersProvider: () -> Set<String>,
) {
    private class OwnedWindow(
        val owner: String,
        val id: String,
        val route: String,
        val surface: ToolPkgFloatingWindowSurface,
        var options: ToolPkgFloatingWindowOptions,
        var state: JSONObject,
        var visible: Boolean,
    )

    private val lock = Any()
    private val windows = LinkedHashMap<String, OwnedWindow>()

    /**
     * 命令入口。
     *
     * 返回值：open/update/show/hide/capabilities 返回 [JSONObject]；list 返回
     * [JSONArray]；requestPermission 返回 [Boolean]；close 返回 null。
     * 参数非法抛 [IllegalArgumentException]，状态非法（重复 ID、未知窗口、
     * 超上限）抛 [IllegalStateException]。
     */
    fun command(owner: String, operation: String, args: JSONObject): Any? {
        if (owner.isBlank()) {
            throw IllegalArgumentException("Missing authenticated window owner")
        }
        return synchronized(lock) {
            when (operation) {
                "capabilities" -> capabilitiesProvider()
                "requestPermission" -> requestPermissionAction()
                "list" -> listWindows(owner)
                "open" -> openWindow(owner, args)
                "update" -> updateWindow(owner, args)
                "show" -> setVisibility(owner, args, visible = true)
                "hide" -> setVisibility(owner, args, visible = false)
                "close" -> {
                    closeWindow(owner, args)
                    // The bridge serializes this result; returning Unit breaks the JSON null contract.
                    null
                }
                else -> throw IllegalArgumentException(
                    "Unknown floating window operation: $operation"
                )
            }
        }
    }

    /**
     * 审计：关闭属主已禁用插件的窗口，修剪宿主已不持有的窗口。
     * 返回被强制关闭的数量。生产环境由周期任务与包目录变更触发，
     * 对齐 Operit2 的 5 秒 reconcile + catalog 监听。
     */
    fun audit(): Int = synchronized(lock) {
        pruneDeadSurfaces()
        val enabled = enabledOwnersProvider()
        var closed = 0
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.owner !in enabled) {
                closeSurfaceQuietly(entry.value)
                iterator.remove()
                closed++
            }
        }
        if (closed > 0) {
            AppLogger.i(TAG, "Floating window audit closed $closed window(s) of disabled plugins")
        }
        closed
    }

    fun hasWindows(): Boolean = synchronized(lock) { windows.isNotEmpty() }

    /** 窗口被拖拽后由宿主回报实际坐标，保持 info().options 与真实位置一致。 */
    fun reportGeometry(owner: String, id: String, x: Int, y: Int) = synchronized(lock) {
        windows[key(owner, id)]?.let { window ->
            window.options = window.options.copy(x = x, y = y)
        }
        Unit
    }

    private fun listWindows(owner: String): JSONArray {
        pruneDeadSurfaces()
        val result = JSONArray()
        for (window in windows.values) {
            if (window.owner == owner) {
                result.put(windowInfo(window))
            }
        }
        return result
    }

    private fun openWindow(owner: String, args: JSONObject): JSONObject {
        val id = requireValidId(args)
        val route = args.optString("route")
        if (route.isBlank()) {
            throw IllegalArgumentException("Floating window route must be a non-empty string")
        }
        val key = key(owner, id)
        if (windows.containsKey(key)) {
            throw IllegalStateException("Window already exists: $id")
        }
        if (windows.values.count { it.owner == owner } >= MAX_WINDOWS_PER_OWNER) {
            throw IllegalStateException("A plugin may own at most $MAX_WINDOWS_PER_OWNER floating windows")
        }
        val options = ToolPkgFloatingWindowOptions.fromJson(args.optJSONObject("options"))
            .mergedOver(ToolPkgFloatingWindowOptions.DEFAULTS)
        validateGeometry(options)
        val rawState = args.opt("state")
        if (rawState != null && rawState != JSONObject.NULL && rawState !is JSONObject) {
            throw IllegalArgumentException("Window state must be an object")
        }
        val state = args.optJSONObject("state") ?: JSONObject()
        val surface = surfaceFactory.open(owner, id, route, options, state)
        val window = OwnedWindow(
            owner = owner,
            id = id,
            route = route,
            surface = surface,
            options = options,
            state = state,
            visible = true,
        )
        windows[key] = window
        return windowInfo(window)
    }

    private fun updateWindow(owner: String, args: JSONObject): JSONObject {
        val id = requireValidId(args)
        val window = requireOwnedWindow(owner, id)
        val options = ToolPkgFloatingWindowOptions.fromJson(args.optJSONObject("options"))
            .mergedOver(window.options)
        validateGeometry(options)
        val rawState = args.opt("state")
        if (rawState != null && rawState != JSONObject.NULL && rawState !is JSONObject) {
            throw IllegalArgumentException("Window state must be an object")
        }
        window.options = options
        window.surface.applyOptions(options)
        if (rawState is JSONObject) {
            val merged = shallowMerge(window.state, rawState)
            window.state = merged
            window.surface.updateState(merged)
        }
        return windowInfo(window)
    }

    private fun setVisibility(owner: String, args: JSONObject, visible: Boolean): JSONObject {
        val id = requireValidId(args)
        val window = requireOwnedWindow(owner, id)
        if (visible) window.surface.show() else window.surface.hide()
        window.visible = visible
        return windowInfo(window)
    }

    private fun closeWindow(owner: String, args: JSONObject) {
        val id = requireValidId(args)
        val window = requireOwnedWindow(owner, id)
        closeSurfaceQuietly(window)
        windows.remove(key(owner, id))
    }

    private fun requireOwnedWindow(owner: String, id: String): OwnedWindow {
        return windows[key(owner, id)]
            ?: throw IllegalStateException("Unknown window owned by this plugin: $id")
    }

    private fun windowInfo(window: OwnedWindow): JSONObject {
        return JSONObject()
            .put("id", window.id)
            .put("route", window.route)
            .put("visible", window.visible)
            .put("options", window.options.toJson())
    }

    private fun pruneDeadSurfaces() {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.value.surface.isAlive()) {
                iterator.remove()
            }
        }
    }

    private fun closeSurfaceQuietly(window: OwnedWindow) {
        try {
            window.surface.close()
        } catch (error: Exception) {
            AppLogger.e(TAG, "Error closing floating window ${window.id}", error)
        }
    }

    private fun requireValidId(args: JSONObject): String {
        val id = args.optString("id")
        if (id.isEmpty() || id.trim() != id || id.length > MAX_ID_LENGTH) {
            throw IllegalArgumentException(
                "Window id must be a trimmed string of 1-$MAX_ID_LENGTH characters"
            )
        }
        return id
    }

    private fun validateGeometry(options: ToolPkgFloatingWindowOptions) {
        options.width?.let {
            require(it in ToolPkgFloatingWindowOptions.MIN_SIZE..ToolPkgFloatingWindowOptions.MAX_SIZE) {
                "Window width must be ${ToolPkgFloatingWindowOptions.MIN_SIZE} through " +
                    "${ToolPkgFloatingWindowOptions.MAX_SIZE} logical pixels"
            }
        }
        options.height?.let {
            require(it in ToolPkgFloatingWindowOptions.MIN_SIZE..ToolPkgFloatingWindowOptions.MAX_SIZE) {
                "Window height must be ${ToolPkgFloatingWindowOptions.MIN_SIZE} through " +
                    "${ToolPkgFloatingWindowOptions.MAX_SIZE} logical pixels"
            }
        }
    }

    private fun key(owner: String, id: String): String = "$owner\u0000$id"

    companion object {
        private const val TAG = "ToolPkgFloatWin"

        const val MAX_WINDOWS_PER_OWNER = 16
        const val MAX_ID_LENGTH = 128

        /** 对齐 Operit2 的 {...owned.state, ...patch} 浅合并。 */
        internal fun shallowMerge(base: JSONObject, patch: JSONObject): JSONObject {
            val merged = JSONObject()
            for (name in base.keys()) {
                merged.put(name, base.get(name))
            }
            for (name in patch.keys()) {
                merged.put(name, patch.get(name))
            }
            return merged
        }
    }
}
