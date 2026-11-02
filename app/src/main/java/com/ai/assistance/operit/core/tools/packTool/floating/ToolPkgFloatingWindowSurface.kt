package com.ai.assistance.operit.core.tools.packTool.floating

import org.json.JSONObject

/**
 * 一个由插件持有的活悬浮窗表面。控制器只通过该接口操作窗口，
 * Android 实现负责 WindowManager/ComposeView 与主线程编组，
 * JVM 测试提供假实现。
 */
interface ToolPkgFloatingWindowSurface {
    val id: String

    /** 应用合并后的完整选项（几何与交互 flags）。 */
    fun applyOptions(options: ToolPkgFloatingWindowOptions)

    /** 显示窗口，不重置 UI 会话。 */
    fun show()

    /** 隐藏窗口，保留 UI 会话。 */
    fun hide()

    /** 关闭窗口并释放 UI 会话与引擎。 */
    fun close()

    /** 以浅合并后的完整 state 重渲染内容。 */
    fun updateState(state: JSONObject)

    /**
     * 窗口是否仍被宿主持有。窗口被系统或用户移除后返回 false，
     * 控制器据此修剪注册表（对齐 Operit2 的 liveIds 修剪）。
     */
    fun isAlive(): Boolean
}

/** 打开新窗口的工厂；route 未注册时抛错且不产生任何窗口。 */
fun interface ToolPkgFloatingWindowSurfaceFactory {
    fun open(
        owner: String,
        id: String,
        route: String,
        options: ToolPkgFloatingWindowOptions,
        state: JSONObject,
    ): ToolPkgFloatingWindowSurface
}
