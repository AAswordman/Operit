package com.ai.assistance.operit.core.tools.builtin.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIActionResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.builtin.ui.StandardUITools
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.shell.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.shell.ShellIdentity
import com.ai.assistance.operit.core.tools.system.uiautomator.UiAutomatorDumpGuard
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.files.OperitPaths
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.media.ImagePoolManager
import java.io.File
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** 管理员 UI 工具在一次操作前按实际授权选择无障碍或 Shell，Root 复用公共能力。 */
open class AdminUITools(context: Context) : StandardUITools(context) {

    companion object {
        private const val TAG = "AdminUITools"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 300L
    }

    protected open val uiShellIdentity: ShellIdentity? = null

    protected suspend fun executeUiShellCommand(command: String): AndroidShellExecutor.CommandResult {
        // 读取页面和按元素点击共用入口；Root 子类也必须保存并恢复调用前的旋转设置。
        if (command.startsWith("uiautomator dump ")) {
            return UiAutomatorDumpGuard.execute(command) { protectedCommand ->
                AndroidShellExecutor.executeShellCommand(protectedCommand, uiShellIdentity)
            }
        }
        return AndroidShellExecutor.executeShellCommand(command, uiShellIdentity)
    }

    protected suspend fun shouldUseAccessibility(tool: AITool): Boolean =
        !hasDisplayParam(tool) && PermissionCapabilityResolver.uiSnapshot(context).canUseAccessibility

    /** 是否包含 display 相关参数（有的话强制走 Shell，不走无障碍） */
    private fun hasDisplayParam(tool: AITool): Boolean {
        return tool.parameters.any { param ->
            param.name.equals("display", ignoreCase = true)
        }
    }

    private fun getDisplayArg(tool: AITool): String {
        val display = tool.parameters.find { it.name.equals("display", ignoreCase = true) }?.value?.trim()
        return if (!display.isNullOrEmpty()) "-d $display " else ""
    }

    /** 使用Shell命令实现点击操作 */
    override suspend fun tap(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍点击")
            return tapWithAccessibility(tool)
        }

        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }
        withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

        // 使用Shell命令执行点击
        try {
            AppLogger.d(TAG, "Attempting to tap at coordinates: ($x, $y) via shell command")
            val command = "input ${getDisplayArg(tool)}tap $x $y"
            val result = executeUiShellCommand(command)

            if (result.success) {
                AppLogger.d(TAG, "Tap successful at coordinates: ($x, $y)")
                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(x, y)
                    operationOverlay.hide()
                }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "tap",
                                        actionDescription =
                                                "Successfully tapped at coordinates ($x, $y) via shell command",
                                        coordinates = Pair(x, y)
                                ),
                        error = ""
                )
            } else {
                AppLogger.e(TAG, "Tap failed at coordinates: ($x, $y), error: ${result.stderr}")
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Failed to tap at coordinates ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error tapping at coordinates ($x, $y)", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error tapping at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    override suspend fun longPress(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍长按")
            return longPressWithAccessibility(tool)
        }

        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }
        withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

        try {
            AppLogger.d(TAG, "Attempting to long press at coordinates: ($x, $y) via shell command")
            // Use swipe to simulate long press
            val command = "input ${getDisplayArg(tool)}swipe $x $y $x $y 800"
            val result = executeUiShellCommand(command)

            if (result.success) {
                AppLogger.d(TAG, "Long press successful at coordinates: ($x, $y)")
                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(x, y)
                    operationOverlay.hide()
                }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = UIActionResultData(
                                actionType = "long_press",
                                actionDescription = "Successfully long pressed at ($x, $y) via shell command",
                                coordinates = Pair(x, y)
                        ),
                        error = ""
                )
            } else {
                AppLogger.e(TAG, "Long press failed at coordinates: ($x, $y), error: ${result.stderr}")
                withContext(Dispatchers.Main) { operationOverlay.hide() }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to long press at coordinates ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error long pressing at coordinates ($x, $y)", e)
            withContext(Dispatchers.Main) { operationOverlay.hide() }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error long pressing at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令实现滑动操作 */
    override suspend fun swipe(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍滑动")
            return swipeWithAccessibility(tool)
        }

        val startX = tool.parameters.find { it.name == "start_x" }?.value?.toIntOrNull()
        val startY = tool.parameters.find { it.name == "start_y" }?.value?.toIntOrNull()
        val endX = tool.parameters.find { it.name == "end_x" }?.value?.toIntOrNull()
        val endY = tool.parameters.find { it.name == "end_y" }?.value?.toIntOrNull()
        val duration = tool.parameters.find { it.name == "duration" }?.value?.toIntOrNull() ?: 300

        if (startX == null || startY == null || endX == null || endY == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. 'start_x', 'start_y', 'end_x', and 'end_y' must be valid integers."
            )
        }
        withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

        try {
            AppLogger.d(
                    TAG,
                    "Attempting to swipe from ($startX, $startY) to ($endX, $endY) with duration $duration ms via shell command"
            )
            val command = "input ${getDisplayArg(tool)}swipe $startX $startY $endX $endY $duration"
            val result = executeUiShellCommand(command)

            if (result.success) {
                AppLogger.d(TAG, "Swipe successful from ($startX, $startY) to ($endX, $endY)")
                withContext(Dispatchers.Main) {
                    operationOverlay.showSwipe(startX, startY, endX, endY)
                    operationOverlay.hide()
                }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "swipe",
                                        actionDescription =
                                                "Successfully performed swipe from ($startX, $startY) to ($endX, $endY) via shell command"
                                ),
                        error = ""
                )
            } else {
                AppLogger.e(TAG, "Swipe failed: ${result.stderr}")
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to perform swipe: ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing swipe", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing swipe: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令点击元素 */
    override suspend fun clickElement(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍点击元素")
            return clickElementWithAccessibility(tool)
        }

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        // index kept for future use
        tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val bounds = tool.parameters.find { it.name == "bounds" }?.value

        if (resourceId == null && className == null && bounds == null && contentDesc == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing element identifier. Provide at least one of: 'resourceId', 'className', 'contentDesc', or 'bounds'."
            )
        }

        // 如果提供了边界坐标，直接点击
        if (bounds != null) {
            try {
                // 解析边界坐标格式 [left,top][right,bottom]
                val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                val matchResult = boundsPattern.find(bounds)

                if (matchResult == null || matchResult.groupValues.size < 5) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid bounds format. Should be: [left,top][right,bottom]"
                    )
                }

                // 提取坐标
                val x1 = matchResult.groupValues[1].toInt()
                val y1 = matchResult.groupValues[2].toInt()
                val x2 = matchResult.groupValues[3].toInt()
                val y2 = matchResult.groupValues[4].toInt()

                // 计算中心点
                val centerX = (x1 + x2) / 2
                val centerY = (y1 + y2) / 2

                // 利用tap方法点击中心点
                val tapTool =
                        AITool(
                                name = "tap",
                                parameters =
                                        listOf(
                                                com.ai.assistance.operit.data.model.ToolParameter(
                                                        "x",
                                                        centerX.toString()
                                                ),
                                                com.ai.assistance.operit.data.model.ToolParameter(
                                                        "y",
                                                        centerY.toString()
                                                )
                                        )
                        )

                return tap(tapTool)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing bounds", e)
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error processing bounds: ${e.message}"
                )
            }
        }

        // 使用uiautomator获取和点击元素
        return clickElementWithUiautomator(tool)
    }

    /** 使用Shell命令设置输入文本 */
    override suspend fun setInputText(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍设置文本")
            return setInputTextWithAccessibility(tool)
        }

        val text = tool.parameters.find { it.name == "text" }?.value ?: ""

        try {
            // 获取屏幕中心作为文本输入的位置
            val displayMetrics = context.resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2
            val centerY = displayMetrics.heightPixels / 2

            // 显示文本输入反馈（在主线程上执行）
            withContext(Dispatchers.Main) { operationOverlay.showTextInput(centerX, centerY, text) }

            // 使用KEYCODE_CLEAR清除字段，这比模拟CTRL+A和DEL更直接
            AppLogger.d(TAG, "Clearing text field with KEYCODE_CLEAR")
            val clearCommand = "input ${getDisplayArg(tool)}keyevent KEYCODE_CLEAR"
            executeUiShellCommand(clearCommand)

            // 短暂延迟
            kotlinx.coroutines.delay(300)

            // 如果文本为空，只需清除字段
            if (text.isEmpty()) {
                // 成功后主动隐藏overlay
                withContext(Dispatchers.Main) { operationOverlay.hide() }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully cleared input field via shell command"
                                ),
                        error = ""
                )
            }

            // 使用原生复制和ADB粘贴来输入文本，这比'input text'更可靠
            AppLogger.d(TAG, "Setting text to clipboard and pasting via ADB: $text")
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("operit_input", text)
                clipboard.setPrimaryClip(clip)
            }

            // 短暂延迟以确保剪贴板操作完成
            kotlinx.coroutines.delay(100)

            // 执行粘贴命令
            val pasteCommand = "input ${getDisplayArg(tool)}keyevent KEYCODE_PASTE"
            val pasteResult = executeUiShellCommand(pasteCommand)

            if (pasteResult.success) {
                // 成功后主动隐藏overlay
                withContext(Dispatchers.Main) { operationOverlay.hide() }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully set input text to: $text via clipboard paste"
                                ),
                        error = ""
                )
            } else {
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to paste text from clipboard: ${pasteResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting input text", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error setting input text: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令实现按键操作 */
    override suspend fun pressKey(tool: AITool): ToolResult {
        val keyCode = tool.parameters.find { it.name == "key_code" }?.value

        if (keyCode == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing 'key_code' parameter."
            )
        }

        if (shouldUseAccessibility(tool) &&
            (accessibilityKeyAction(keyCode) != null ||
                !PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell)
        ) {
            return pressKeyWithAccessibility(tool)
        }

        val command = "input ${getDisplayArg(tool)}keyevent $keyCode"

        return try {
            val result = executeUiShellCommand(command)

            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "keyPress",
                                        actionDescription =
                                                "Successfully pressed key: $keyCode via shell command"
                                ),
                        error = ""
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to press key: ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error pressing key", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error pressing key: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    override suspend fun captureScreenshotToFile(tool: AITool): Pair<String?, Pair<Int, Int>?> {
        if (!PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) {
            return if (hasDisplayParam(tool)) Pair(null, null) else captureScreenshotToFileWithAccessibility(tool)
        }
        return try {
            val screenshotDir = OperitPaths.cleanOnExitDir()

            val shortName = System.currentTimeMillis().toString().takeLast(4)
            val file = File(screenshotDir, "$shortName.png")

            // 1) Debugger 模式下优先尝试 Shell 模式 (ADB) 截图
            AppLogger.d(TAG, "captureScreenshotToFile: Attempting shell screencap")
            val command = "screencap -p ${file.absolutePath}"
            val result = executeUiShellCommand(command)

            if (result.success && file.exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val dimensions = if (options.outWidth > 0 && options.outHeight > 0) {
                    Pair(options.outWidth, options.outHeight)
                } else {
                    null
                }
                AppLogger.d(TAG, "captureScreenshotToFile: Shell screencap success")
                return Pair(file.absolutePath, dimensions)
            }

            // 2) 如果 Shell 失败，作为回退尝试无障碍截图 (调用父类)
            AppLogger.w(TAG, "captureScreenshotToFile: Shell screencap failed, falling back to accessibility")
            if (hasDisplayParam(tool)) Pair(null, null) else captureScreenshotToFileWithAccessibility(tool)
        } catch (e: Exception) {
            AppLogger.e(TAG, "captureScreenshotToFile failed in Debugger", e)
            Pair(null, null)
        }
    }

    override suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?> {
        return captureScreenshotToFile(tool)
    }

    override suspend fun captureScreenshotBitmap(tool: AITool): Pair<Bitmap?, Pair<Int, Int>?> {
        val (filePath, dimensions) = captureScreenshot(tool)
        if (filePath == null) {
            return Pair(null, dimensions)
        }

        val bitmap = BitmapFactory.decodeFile(filePath) ?: return Pair(null, dimensions)
        val resolvedDimensions = dimensions ?: Pair(bitmap.width, bitmap.height)
        return Pair(bitmap, resolvedDimensions)
    }

    /** 使用Shell命令获取页面信息 */
    override suspend fun getPageInfo(tool: AITool): ToolResult {
        if (shouldUseAccessibility(tool)) {
            AppLogger.d(TAG, "无障碍服务已启用，使用无障碍获取页面信息")
            val result = getPageInfoWithAccessibility(tool)
            if (result.success || !PermissionCapabilityResolver.shellSnapshot(context).hasPrivilegedShell) return result
        }

        val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
        // detail kept for future use
        tool.parameters.find { it.name == "detail" }?.value ?: "summary"

        if (format !in listOf("xml", "json")) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid format specified. Must be 'xml' or 'json'."
            )
        }

        return try {
            // 获取UI数据
            val uiData = getUIDataFromShell(tool)
            if (uiData == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to retrieve UI data."
                )
            }

            // 解析当前窗口信息
            val focusInfo = extractFocusInfoFromShell(uiData.windowInfo)

            // 简化布局信息
            val simplifiedLayout = simplifyLayoutFromXml(uiData.uiXml)

            // 创建结构化数据
            val resultData =
                    UIPageResultData(
                            packageName = focusInfo.packageName ?: "Unknown",
                            activityName = focusInfo.activityName ?: "Unknown",
                            uiElements = simplifiedLayout
                    )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting page info", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error getting page info: ${e.message}"
            )
        }
    }

    /** UI数据类，保存XML和窗口信息 */
    private data class UIData(val uiXml: String, val windowInfo: String)

    /** 获取UI数据，使用Shell命令，严格遵守工具参数中的 display（如有） */
    private suspend fun getUIDataFromShell(tool: AITool): UIData? {
        return try {
            // 使用ADB命令获取UI dump
            AppLogger.d(TAG, "使用ADB命令获取UI数据")

            val displayId = tool.parameters
                .find { it.name.equals("display", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            // 执行UI dump命令，只有在显式提供 display 参数时才使用 --display-id
            val dumpResult = if (displayId != null) {
                val cmd = "uiautomator dump --display-id $displayId /sdcard/window_dump.xml"
                AppLogger.d(TAG, "UI dump using explicit display-id=$displayId")
                executeUiShellCommand(cmd)
            } else {
                executeUiShellCommand("uiautomator dump /sdcard/window_dump.xml")
            }

            if (!dumpResult.success) {
                AppLogger.e(TAG, "uiautomator dump失败: ${dumpResult.stderr}")
                return null
            }
            AppLogger.d(TAG, "uiautomator dump成功: ${dumpResult.stdout}")

            // 读取dump文件内容
            val readResult = executeUiShellCommand("cat /sdcard/window_dump.xml")
            if (!readResult.success) {
                AppLogger.e(TAG, "读取UI dump文件失败: ${readResult.stderr}")
                return null
            }

            // 获取窗口信息
            var windowInfo = getWindowInfoFromShell()

            // 如果窗口信息为空，尝试延迟后重试一次
            if (windowInfo.isEmpty()) {
                AppLogger.w(TAG, "首次获取窗口信息失败，延迟500ms后重试")
                kotlinx.coroutines.delay(500)
                windowInfo = getWindowInfoFromShell()
            }

            UIData(readResult.stdout, windowInfo)
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取UI数据时出错", e)
            null
        }
    }

    /** 获取窗口信息，使用多种命令尝试 */
    private suspend fun getWindowInfoFromShell(): String {
        // 尝试多种命令来获取窗口信息
        val commands =
                listOf(
                        // 标准命令，获取当前焦点和焦点应用
                        "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
                        // 备用命令，只获取当前焦点
                        "dumpsys window | grep -E 'mCurrentFocus'",
                        // 备用命令，只获取焦点应用
                        "dumpsys window | grep -E 'mFocusedApp'",
                        // 最后的备用命令，尝试获取任何窗口信息
                        "dumpsys window | grep -E 'Window #|Focus'",
                        // 极端情况下，尝试获取前台应用包名
                        "dumpsys activity recents | grep 'Recent #0' -A2"
                )

        // 依次尝试每个命令，直到有一个成功
        for (command in commands) {
            try {
                val result = executeUiShellCommand(command)
                if (result.success && result.stdout.isNotEmpty()) {
                    AppLogger.d(TAG, "成功获取窗口信息: ${result.stdout.take(100)}")
                    return result.stdout
                }
                // 如果命令执行失败或返回空结果，尝试下一个命令
                AppLogger.w(TAG, "窗口信息命令 '$command' 失败或返回空结果")
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行窗口信息命令 '$command' 出错", e)
                // 继续尝试下一个命令
            }
        }

        // 所有命令都失败时，尝试获取topActivity作为最后的手段
        try {
            val topActivityCommand =
                    "dumpsys activity activities | grep -E 'topResumedActivity|topActivity'"
            val result = executeUiShellCommand(topActivityCommand)
            if (result.success && result.stdout.isNotEmpty()) {
                AppLogger.d(TAG, "使用topActivity作为窗口信息替代: ${result.stdout.take(100)}")
                return result.stdout
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取topActivity失败", e)
        }

        AppLogger.e(TAG, "所有获取窗口信息的尝试均失败")
        return ""
    }

    /** UI节点数据类，仅在Shell实现中使用 */
    private data class UINodeShell(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val children: MutableList<UINodeShell> = mutableListOf()
    )

    /** 简化布局，从XML中提取UI元素，Shell版本 */
    private fun simplifyLayoutFromXml(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        val nodeStack = mutableListOf<UINodeShell>()
        var rootNode: UINodeShell? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = createNodeShell(parser)
                        if (rootNode == null) {
                            rootNode = newNode
                            nodeStack.add(newNode)
                        } else {
                            nodeStack.lastOrNull()?.children?.add(newNode)
                            nodeStack.add(newNode)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            parser.next()
        }

        // 创建默认根节点，如果解析失败
        return rootNode?.toUINodeSimplified()
                ?: SimplifiedUINode(
                        className = null,
                        text = null,
                        contentDesc = null,
                        resourceId = null,
                        bounds = null,
                        isClickable = false,
                        children = emptyList()
                )
    }

    // 转换内部UINodeShell为SimplifiedUINode
    private fun UINodeShell.toUINodeSimplified(): SimplifiedUINode {
        return SimplifiedUINode(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable,
                children = children.map { it.toUINodeSimplified() }
        )
    }

    private fun createNodeShell(parser: XmlPullParser): UINodeShell {
        // 解析关键属性
        val className = parser.getAttributeValue(null, "class")?.substringAfterLast('.')
        val text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
        val contentDesc = parser.getAttributeValue(null, "content-desc")
        val resourceId = parser.getAttributeValue(null, "resource-id")
        val bounds = parser.getAttributeValue(null, "bounds")
        val isClickable = parser.getAttributeValue(null, "clickable") == "true"

        return UINodeShell(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable
        )
    }

    /** 窗口焦点信息数据类 */
    private data class FocusInfoShell(
            var packageName: String? = null,
            var activityName: String? = null
    )

    /** 从窗口焦点数据中提取包名和活动名 */
    private fun extractFocusInfoFromShell(windowInfo: String): FocusInfoShell {
        val result = FocusInfoShell()

        try {
            if (windowInfo.isBlank()) {
                AppLogger.w(TAG, "Window info is empty, cannot extract focus information")
                // 即使窗口信息为空，也设置默认值，确保不会返回Unknown
                result.packageName = "android"
                result.activityName = "ForegroundActivity"
                return result
            }

            AppLogger.d(TAG, "Window info for extraction: ${windowInfo.take(200)}")

            // 尝试不同的提取方法，按照特异性顺序
            if (!extractFromCurrentFocusShell(windowInfo, result) &&
                            !extractFromFocusedAppShell(windowInfo, result) &&
                            !extractFromLauncherInfoShell(windowInfo, result) &&
                            !extractFromTopActivityShell(windowInfo, result) &&
                            !extractUsingGenericPatternsShell(windowInfo, result)
            ) {

                AppLogger.w(TAG, "Could not extract focus information using any method")
            }

            // 最后的回退：如果我们仍然无法确定任何信息，使用默认值
            if (result.packageName == null) {
                if (windowInfo.contains("statusbar") || windowInfo.contains("SystemUI")) {
                    result.packageName = "com.android.systemui"
                    result.activityName = "SystemUI"
                    AppLogger.d(TAG, "Using SystemUI fallback")
                } else if (windowInfo.contains("recents")) {
                    result.packageName = "com.android.systemui"
                    result.activityName = "Recents"
                    AppLogger.d(TAG, "Using Recents fallback")
                } else {
                    result.packageName = "android"
                    result.activityName = "ForegroundActivity"
                    AppLogger.d(TAG, "Using last-resort fallback values")
                }
            }

            // 记录提取结果
            AppLogger.d(
                    TAG,
                    "Final extraction result - package: ${result.packageName}, activity: ${result.activityName}"
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing window info", e)
            // 确保即使出现异常，我们也有至少一些默认值
            if (result.packageName == null) result.packageName = "android"
            if (result.activityName == null) result.activityName = "ForegroundActivity"
        }

        return result
    }

    /**
     * 从mCurrentFocus格式提取 例如：mCurrentFocus=Window{1234567 u0
     * com.example.app/com.example.app.MainActivity}
     */
    private fun extractFromCurrentFocusShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // 尝试多种mCurrentFocus格式模式
        val currentFocusPatterns =
                listOf(
                        // 标准格式，具有包/活动（同时也支持 mFocusedWindow）
                        "(?:mCurrentFocus|mFocusedWindow)=.*?\\{.*?\\s+([a-zA-Z0-9_.]+)/([^\\s}]+)".toRegex(),
                        // 有时会看到的替代格式
                        "(?:mCurrentFocus|mFocusedWindow)=.*?\\s+([a-zA-Z0-9_.]+)/([^\\s}]+)\\}".toRegex(),
                        // 只有包名的格式（活动将单独处理）
                        "(?:mCurrentFocus|mFocusedWindow)=.*?\\{.*?\\s+([a-zA-Z0-9_.]+)(?:/|\\s)".toRegex()
                )

        for (pattern in currentFocusPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null) {
                if (match.groupValues.size >= 3) {
                    // 包含包和活动的模式
                    result.packageName = match.groupValues[1]
                    result.activityName = match.groupValues[2]
                    AppLogger.d(TAG, "Extracted from mCurrentFocus pattern (full): ${pattern.pattern}")
                    return true
                } else if (match.groupValues.size >= 2) {
                    // 只有包名的模式
                    result.packageName = match.groupValues[1]
                    AppLogger.d(TAG, "Extracted package from mCurrentFocus pattern: ${pattern.pattern}")
                    // 返回false以允许其他方法提取活动名称
                    return false
                }
            }
        }
        return false
    }

    /**
     * 从mFocusedApp格式提取 例如：mFocusedApp=AppWindowToken{token=Token{12345 ActivityRecord{67890 u0
     * com.example.app/.MainActivity t123}}}
     */
    private fun extractFromFocusedAppShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // mFocusedApp格式的多种模式
        val focusedAppPatterns =
                listOf(
                        // 带有ActivityRecord的标准格式
                        "mFocusedApp=.*?ActivityRecord\\{.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex(),
                        // 处理类似于 mFocusedApp=null 后的真实输出行
                        "ActivityRecord\\{.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex(),
                        // 有时会看到的替代格式
                        "mFocusedApp=.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)\\s".toRegex(),
                        // 只有包名的格式
                        "mFocusedApp=.*?\\s+([a-zA-Z0-9_.]+)(?:/|\\s)".toRegex()
                )

        for (pattern in focusedAppPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null) {
                if (match.groupValues.size >= 3) {
                    // 包含包和活动的完全匹配
                    result.packageName = match.groupValues[1]
                    result.activityName = match.groupValues[2]
                    AppLogger.d(TAG, "Extracted from mFocusedApp pattern (full): ${pattern.pattern}")
                    return true
                } else if (match.groupValues.size >= 2) {
                    // 只有包的部分匹配
                    result.packageName = match.groupValues[1]
                    AppLogger.d(TAG, "Extracted package from mFocusedApp pattern: ${pattern.pattern}")
                    // 返回false以允许通过其他方法提取活动名称
                    return false
                }
            }
        }
        return false
    }

    /** 为启动器窗口提取信息 例如：mCurrentFocus=Window{1a23bc4 u0 Launcher} */
    private fun extractFromLauncherInfoShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // 查找启动器特定模式
        if (windowInfo.contains("mCurrentFocus") && windowInfo.contains("Launcher")) {
            val launcherPatterns =
                    listOf(
                            "\\{.*?\\s+([a-zA-Z0-9_.]+\\.launcher)/".toRegex(),
                            "\\{.*?\\s+([a-zA-Z0-9_.]+\\.home)/".toRegex(),
                            "\\{.*?\\s+Launcher\\}".toRegex()
                    )

            for (pattern in launcherPatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    result.packageName = match.groupValues[1]
                    result.activityName = "Launcher"
                    AppLogger.d(TAG, "Extracted launcher info")
                    return true
                }
            }

            // 如果我们检测到Launcher但无法提取特定的包，
            // 使用默认的启动器包和名称
            result.packageName = "com.android.launcher3"
            result.activityName = "Launcher"
            AppLogger.d(TAG, "Using default launcher info")
            return true
        }
        return false
    }

    /**
     * 从topActivity/topResumedActivity输出格式提取
     * 例如：topActivity=ComponentInfo{com.example.app/.MainActivity}
     */
    private fun extractFromTopActivityShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // topActivity格式的模式
        val topActivityPatterns =
                listOf(
                        "topActivity=ComponentInfo\\{([a-zA-Z0-9_.]+)/\\.?([^}]+)\\}".toRegex(),
                        "topResumedActivity=ComponentInfo\\{([a-zA-Z0-9_.]+)/\\.?([^}]+)\\}".toRegex(),
                        "ResumedActivity:\\s+\\{([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex()
                )

        for (pattern in topActivityPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null && match.groupValues.size >= 3) {
                result.packageName = match.groupValues[1]
                result.activityName = match.groupValues[2]
                AppLogger.d(TAG, "Extracted from topActivity pattern: ${pattern.pattern}")
                return true
            }
        }

        // 还要查找Recent tasks格式
        val recentPattern = "Recent #0.*?\\{([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex()
        val recentMatch = recentPattern.find(windowInfo)
        if (recentMatch != null && recentMatch.groupValues.size >= 3) {
            result.packageName = recentMatch.groupValues[1]
            result.activityName = recentMatch.groupValues[2]
            AppLogger.d(TAG, "Extracted from Recent tasks pattern")
            return true
        }

        return false
    }

    /** 作为后备使用更通用的模式提取 */
    private fun extractUsingGenericPatternsShell(
            windowInfo: String,
            result: FocusInfoShell
    ): Boolean {
        var foundAny = false

        // 尝试用各种模式提取包名
        if (result.packageName == null) {
            // 查找常见的包模式，如com.android.something
            val packagePatterns =
                    listOf(
                            "\\s([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})/".toRegex(), // com.example.app/
                            "\\s([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})\\s".toRegex(), // com.example.app (空格后)
                            "([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})".toRegex() // 只查找任何包名类似的名称
                    )

            for (pattern in packagePatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    val potentialPackage = match.groupValues[1]
                    // 验证这看起来像一个真实的包（避免匹配随机字符串）
                    if (potentialPackage.split(".").size >= 3 &&
                                    !potentialPackage.contains("@") &&
                                    !potentialPackage.startsWith("1") &&
                                    !potentialPackage.startsWith("0")
                    ) {

                        result.packageName = potentialPackage
                        foundAny = true
                        AppLogger.d(TAG, "Found package name using fallback pattern: ${pattern.pattern}")
                        break
                    }
                }
            }
        }

        // 如果我们还没有活动名称，尝试提取它
        if (result.activityName == null) {
            // 查找活动名称模式
            val activityPatterns =
                    listOf(
                            "/\\.?([A-Z][a-zA-Z0-9_]+Activity)".toRegex(), // /.MainActivity or
                            // /MainActivity
                            "/([^\\s/}]+)".toRegex(), // 斜杠后的任何段
                            "\\.([A-Z][a-zA-Z0-9_]+)".toRegex() // .MainActivity
                    )

            for (pattern in activityPatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    val activityName = match.groupValues[1]
                    // 验证它看起来像一个活动名称（以大写字母开头）
                    if (activityName.isNotEmpty() &&
                                    activityName[0].isUpperCase() &&
                                    !activityName.contains("@")
                    ) {

                        result.activityName = activityName
                        foundAny = true
                        AppLogger.d(TAG, "Found activity name using fallback pattern: ${pattern.pattern}")
                        break
                    }
                }
            }
        }

        // 特殊情况处理：如果我们有包名但没有活动名称
        if (result.packageName != null && result.activityName == null) {
            // 尝试根据包猜测主活动名称
            val packageParts = result.packageName!!.split(".")
            if (packageParts.isNotEmpty()) {
                val lastPart = packageParts.last().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                result.activityName = "${lastPart}Activity"
                AppLogger.d(TAG, "Guessed activity name from package: ${result.activityName}")
                foundAny = true
            }
        }

        // 如果我们找到了包或活动，将其视为部分成功
        return foundAny
    }



    /** 使用uiautomator点击元素 */
    private suspend fun clickElementWithUiautomator(tool: AITool): ToolResult {
        AppLogger.d(TAG, "Using uiautomator to click element")

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val partialMatch =
                tool.parameters.find { it.name == "partialMatch" }?.value?.toBoolean() ?: false

        try {
            // 先尝试获取UI dump
            AppLogger.d(TAG, "Dumping UI hierarchy to find element")
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val result = executeUiShellCommand(dumpCommand)

            if (!result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to dump UI hierarchy: ${result.stderr ?: "Unknown error"}"
                )
            }

            // 读取dump文件
            val readCommand = "cat /sdcard/window_dump.xml"
            val readResult = executeUiShellCommand(readCommand)

            if (!readResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read UI dump: ${readResult.stderr ?: "Unknown error"}"
                )
            }

            val xml = readResult.stdout

            // 使用XML Parser查找匹配的元素（取代有问题的正则方式）
            val hasSelectors = resourceId != null || className != null || contentDesc != null
            if (!hasSelectors) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No element selector provided."
                )
            }

            // 用XmlPullParser逐节点匹配，避免正则跨节点匹配的问题
            data class MatchedNode(val bounds: String?)
            val matchingNodes = mutableListOf<MatchedNode>()

            try {
                val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                        val actualId = parser.getAttributeValue(null, "resource-id")
                        val actualClass = parser.getAttributeValue(null, "class")
                        val actualDesc = parser.getAttributeValue(null, "content-desc")
                        val actualBounds = parser.getAttributeValue(null, "bounds")

                        var matches = true

                        if (resourceId != null) {
                            if (actualId == null) {
                                matches = false
                            } else if (partialMatch) {
                                matches = actualId.contains(resourceId)
                            } else {
                                // 支持短ID (expand_search) 和完整ID (tv.danmaku.bili:id/expand_search)
                                matches = actualId == resourceId || actualId.endsWith(":id/$resourceId")
                            }
                        }

                        if (matches && className != null) {
                            if (actualClass == null) {
                                matches = false
                            } else if (partialMatch) {
                                matches = actualClass.contains(className)
                            } else {
                                // 支持短类名 (ImageView) 和完整类名 (android.widget.ImageView)
                                matches = actualClass == className || actualClass.endsWith(".$className")
                            }
                        }

                        if (matches && contentDesc != null) {
                            if (actualDesc == null) {
                                matches = false
                            } else if (partialMatch) {
                                matches = actualDesc.contains(contentDesc, ignoreCase = true)
                            } else {
                                matches = actualDesc.equals(contentDesc, ignoreCase = true)
                            }
                        }

                        if (matches) {
                            matchingNodes.add(MatchedNode(bounds = actualBounds))
                        }
                    }
                    parser.next()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error parsing UI XML", e)
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to parse UI hierarchy XML: ${e.message}"
                )
            }

            if (matchingNodes.isEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No matching element found."
                )
            }

            if (index < 0 || index >= matchingNodes.size) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Index out of range. Found ${matchingNodes.size} elements, but requested index $index."
                )
            }

            // 获取指定索引的节点bounds
            val nodeBounds = matchingNodes[index].bounds
            if (nodeBounds == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Target element has no bounds."
                )
            }

            // 提取边界坐标
            val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
            val boundsMatch = boundsPattern.find(nodeBounds)
            if (boundsMatch == null || boundsMatch.groupValues.size < 5) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to extract bounds from the element: $nodeBounds"
                )
            }

            // 提取坐标
            val x1 = boundsMatch.groupValues[1].toInt()
            val y1 = boundsMatch.groupValues[2].toInt()
            val x2 = boundsMatch.groupValues[3].toInt()
            val y2 = boundsMatch.groupValues[4].toInt()

            // 计算中心点
            val centerX = (x1 + x2) / 2
            val centerY = (y1 + y2) / 2

            withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

            val tapCommand = "input tap $centerX $centerY"
            val tapResult = executeUiShellCommand(tapCommand)

            if (tapResult.success) {
                val identifierDescription =
                        when {
                            resourceId != null -> " with resource ID: $resourceId"
                            contentDesc != null -> " with content description: $contentDesc"
                            else -> " with class name: $className"
                        }

                val matchCount =
                        if (matchingNodes.size > 1) {
                            " (index $index of ${matchingNodes.size} matches)"
                        } else ""

                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(centerX, centerY)
                    operationOverlay.hide()
                }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "click",
                                        actionDescription =
                                                "Successfully clicked element$identifierDescription$matchCount at coordinates ($centerX, $centerY) via shell command",
                                        coordinates = Pair(centerX, centerY),
                                        elementId = resourceId ?: className ?: contentDesc
                                ),
                        error = ""
                )
            } else {
                withContext(Dispatchers.Main) {
                    operationOverlay.hide()
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to click element: ${tapResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error clicking element with uiautomator", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide()
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error clicking element: ${e.message ?: "Unknown exception"}"
            )
        } finally {
            // 清理临时文件
            try {
                Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")
            } catch (cleanupEx: Exception) {
                AppLogger.e(TAG, "Error cleaning up temp file", cleanupEx)
            }
        }
    }

    /** 从边界字符串提取中心坐标 返回中心点坐标，或null如果格式无效 */
    protected fun extractCenterCoordinates(bounds: String): Pair<Int, Int>? {
        val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
        val matchResult = boundsPattern.find(bounds) ?: return null

        if (matchResult.groupValues.size < 5) return null

        // 提取坐标
        val x1 = matchResult.groupValues[1].toInt()
        val y1 = matchResult.groupValues[2].toInt()
        val x2 = matchResult.groupValues[3].toInt()
        val y2 = matchResult.groupValues[4].toInt()

        // 计算并返回中心点
        val centerX = (x1 + x2) / 2
        val centerY = (y1 + y2) / 2

        return Pair(centerX, centerY)
    }

    // 无障碍是管理员的执行能力，以下方法不再形成独立权限等级。



    /**
     * 检查无障碍服务是否正在运行
     */
    private suspend fun isAccessibilityServiceEnabled(): Boolean {
        return UIHierarchyManager.isAccessibilityServiceEnabled(context)
    }

    /**
     * 为需要无障碍服务的工具创建一个前置检查的包装器
     */
    private suspend fun <T> withAccessibilityCheck(tool: AITool, block: suspend () -> T): T {
        if (!isAccessibilityServiceEnabled()) {
            throw IllegalStateException("Accessibility Service is not enabled. Please enable it in system settings to use this feature.")
        }
        return block()
    }

    /**
     * 获取UI层次结构，失败时重试
     * @return UI层次结构XML字符串，获取失败返回空字符串
     */
    private suspend fun getUIHierarchyWithRetry(): String {
        var retryCount = 0
        var uiXml = ""

        while (retryCount < MAX_RETRY_COUNT) {
            uiXml = UIHierarchyManager.getUIHierarchy(context)
            if (uiXml.isNotEmpty()) {
                return uiXml
            }

            retryCount++
            if (retryCount < MAX_RETRY_COUNT) {
                AppLogger.d(TAG, "获取UI层次结构失败，正在重试 #$retryCount")
                delay(RETRY_DELAY_MS)
            }
        }

        AppLogger.w(TAG, "获取UI层次结构失败，已重试${MAX_RETRY_COUNT}次")
        return uiXml
    }

    /** Gets the current UI page/window information */
    private suspend fun getPageInfoWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
        val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
        val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"

        if (format !in listOf("xml", "json")) {
                    return@withAccessibilityCheck ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid format specified. Must be 'xml' or 'json'."
            )
        }

            // 使用无障碍服务获取UI数据（带重试）
            val uiXml = getUIHierarchyWithRetry()
            if (uiXml.isEmpty()) {
                    return@withAccessibilityCheck ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to retrieve UI data via accessibility service."
                )
            }

            // 解析当前窗口信息
            val focusInfo = extractFocusInfoFromAccessibility()

            // 简化布局信息
            val simplifiedLayout = simplifyLayout(uiXml)

            // 创建结构化数据
            val resultData =
                    UIPageResultData(
                            packageName = focusInfo.packageName ?: "Unknown",
                            activityName = focusInfo.activityName ?: "Unknown",
                            uiElements = simplifiedLayout
                    )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting page info", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error getting page info: ${e.message}"
            )
        }
    }

    /** 从无障碍服务获取焦点信息 */
    private suspend fun extractFocusInfoFromAccessibility(): FocusInfo {
        val focusInfo = FocusInfo()
        try {
            // 1. 获取UI层次结构的XML快照（带重试）
            val hierarchyXml = getUIHierarchyWithRetry()
            if (hierarchyXml.isEmpty()) {
                AppLogger.w(TAG, "无法获取UI层次结构XML，使用默认值。")
                focusInfo.packageName = "android"
                // 即使XML获取失败，仍然尝试获取Activity名称
                focusInfo.activityName = UIHierarchyManager.getCurrentActivityName(context) ?: "ForegroundActivity"
                return focusInfo
            }

            // 2. 从XML中解析包名
            val (packageName, _) = UIHierarchyManager.extractWindowInfo(hierarchyXml)
            // 3. 从服务中直接获取当前Activity名称
            val activityName = UIHierarchyManager.getCurrentActivityName(context)

            focusInfo.packageName = packageName
            focusInfo.activityName = activityName // 使用从服务获取的Activity名称

            // 如果没有获取到，使用默认值
            if (focusInfo.packageName == null) focusInfo.packageName = "android"
            if (focusInfo.activityName == null) focusInfo.activityName = "ForegroundActivity"
        } catch (e: Exception) {
            AppLogger.e(TAG, "从XML解析焦点信息时出错", e)
            // 设置默认值
            focusInfo.packageName = "android"
            focusInfo.activityName = "ForegroundActivity"
        }
        return focusInfo
    }

    /** 简化XML布局为节点树 */
    fun simplifyLayout(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        val nodeStack = mutableListOf<UINode>()
        var rootNode: UINode? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = createNode(parser)
                        if (rootNode == null) {
                            rootNode = newNode
                            nodeStack.add(newNode)
                        } else {
                            nodeStack.lastOrNull()?.children?.add(newNode)
                            nodeStack.add(newNode)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            parser.next()
        }

        return rootNode?.toUINode()
                ?: SimplifiedUINode(
                        className = null,
                        text = null,
                        contentDesc = null,
                        resourceId = null,
                        bounds = null,
                        isClickable = false,
                        children = emptyList()
                )
    }

    private fun createNode(parser: XmlPullParser): UINode {
        // 解析关键属性
        val className = parser.getAttributeValue(null, "class")?.substringAfterLast('.')
        val text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
        val contentDesc = parser.getAttributeValue(null, "content-desc")
        val resourceId = parser.getAttributeValue(null, "resource-id")
        val bounds = parser.getAttributeValue(null, "bounds")
        val isClickable = parser.getAttributeValue(null, "clickable") == "true"

        return UINode(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable
        )
    }

    /** 点击元素 */
    private suspend fun clickElementWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
                val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
                val className = tool.parameters.find { it.name == "className" }?.value
                val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
                val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
                val bounds = tool.parameters.find { it.name == "bounds" }?.value

                if (resourceId == null && className == null && bounds == null && contentDesc == null) {
                    return@withAccessibilityCheck ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Missing element identifier. Provide at least one of 'resourceId', 'className', 'contentDesc', or 'bounds'."
                    )
                }

                // 如果提供了边界坐标，直接解析并点击中心点
                if (bounds != null) {
                    return@withAccessibilityCheck handleClickByBounds(tool, bounds)
                }

                // 获取UI层次结构XML（带重试）
                val uiXml = getUIHierarchyWithRetry()
                if (uiXml.isEmpty()) {
                    return@withAccessibilityCheck ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Unable to get UI hierarchy.")
                }

                // 在XML中查找匹配的节点
                val matchedNodes = findNodesInXml(uiXml) { parser ->
                    val hasSelectors = resourceId != null || className != null || contentDesc != null
                    if (!hasSelectors) {
                        return@findNodesInXml false
                    }

                    val actualId = parser.getAttributeValue(null, "resource-id")
                    val actualClass = parser.getAttributeValue(null, "class")
                    val actualDesc = parser.getAttributeValue(null, "content-desc")

                    if(resourceId != null && (actualId == null || !actualId.endsWith(resourceId))){
                        return@findNodesInXml false
                    }

                    if(className != null && (actualClass == null || actualClass != className)){
                        return@findNodesInXml false
                    }

                    if(contentDesc != null && (actualDesc == null || !actualDesc.equals(contentDesc, ignoreCase = true))){
                        return@findNodesInXml false
                    }

                    true
                }

                if (matchedNodes.isEmpty()) {
                    return@withAccessibilityCheck ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "No matching element found.")
                }

                // 检查索引是否有效
                if (index < 0 || index >= matchedNodes.size) {
                    return@withAccessibilityCheck ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Index out of range. Found ${matchedNodes.size} elements, but requested index $index."
                    )
                }

                // 获取目标节点的bounds
                val targetNodeBounds = matchedNodes[index].bounds
                if (targetNodeBounds == null) {
                    return@withAccessibilityCheck ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Target element has no bounds.")
                }

                // 解析bounds并点击
                handleClickByBounds(tool, targetNodeBounds)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error clicking element", e)
            operationOverlay.hide()
            ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                    error = "Error clicking element: ${e.message}"
                )
            }
    }

    private suspend fun handleClickByBounds(tool: AITool, bounds: String): ToolResult {
        try {
            val rect = parseBounds(bounds)
            if (rect.isEmpty) {
                 return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Invalid bounds format: $bounds")
            }

            val centerX = rect.centerX()
            val centerY = rect.centerY()

            withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }
            val clickSuccess = performAccessibilityClick(centerX, centerY)

            return if (clickSuccess) {
                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(centerX, centerY)
                    operationOverlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                    result = UIActionResultData(
                                        actionType = "click",
                        actionDescription = "Successfully clicked at bounds $bounds",
                        coordinates = Pair(centerX, centerY)
                    )
                )
            } else {
                operationOverlay.hide()
                ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to click at bounds $bounds via accessibility service.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error clicking by bounds", e)
            operationOverlay.hide()
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Error clicking at bounds: ${e.message}")
        }
    }

    private fun findNodesInXml(xml: String, predicate: (parser: XmlPullParser) -> Boolean): List<NodeInfo> {
        val matchedNodes = mutableListOf<NodeInfo>()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                if (predicate(parser)) {
                    matchedNodes.add(
                        NodeInfo(
                            bounds = parser.getAttributeValue(null, "bounds"),
                            text = parser.getAttributeValue(null, "text")
                        )
                    )
                }
            }
            parser.next()
        }
        return matchedNodes
    }

    private data class NodeInfo(val bounds: String?, val text: String?)

    /** 设置输入文本 */
    private suspend fun setInputTextWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
        val text = tool.parameters.find { it.name == "text" }?.value ?: ""

            // 通过UIHierarchyManager请求远程服务找到焦点节点的ID
            val focusedNodeId = UIHierarchyManager.findFocusedNodeId(context)
            if (focusedNodeId.isNullOrEmpty()) {
                    return@withAccessibilityCheck ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No focused editable field found."
                )
            }

            // 显示反馈
            val rect = parseBounds(focusedNodeId)
            if (!rect.isEmpty) {
            operationOverlay.showTextInput(rect.centerX(), rect.centerY(), text)
            }

            // 通过UIHierarchyManager请求远程服务设置文本
            val result = UIHierarchyManager.setTextOnNode(context, focusedNodeId, text)

                if (result) {
                // 成功后主动隐藏overlay
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully set input text via accessibility service"
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to set text via accessibility service."
                )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting input text", e)
            operationOverlay.hide()
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error setting input text: ${e.message}"
            )
        }
    }

    /** 执行轻触操作 */
    private suspend fun tapWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
                    return@withAccessibilityCheck ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                        error = "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }

            withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

            // 使用无障碍服务执行点击
            val result = performAccessibilityClick(x, y)

                if (result) {
                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(x, y)
                    operationOverlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "tap",
                                        actionDescription =
                                                "Successfully tapped at coordinates ($x, $y) via accessibility service",
                                        coordinates = Pair(x, y)
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to tap at coordinates via accessibility service."
                )
            }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error tapping at coordinates", e)
            operationOverlay.hide()
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error tapping at coordinates: ${e.message}"
            )
        }
    }

    /** 执行长按操作 */
    private suspend fun longPressWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
                    return@withAccessibilityCheck ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                        error = "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }

            withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

            // 使用无障碍服务执行长按
            val result = performAccessibilityLongPress(x, y)

                if (result) {
                withContext(Dispatchers.Main) {
                    operationOverlay.showTap(x, y)
                    operationOverlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "long_press",
                                        actionDescription =
                                                "Successfully long pressed at coordinates ($x, $y) via accessibility service",
                                        coordinates = Pair(x, y)
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to long press at coordinates via accessibility service."
                )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error long pressing at coordinates", e)
            operationOverlay.hide()
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error long pressing at coordinates: ${e.message}"
            )
        }
    }

    /** 执行滑动操作 */
    private suspend fun swipeWithAccessibility(tool: AITool): ToolResult {
        return try {
            withAccessibilityCheck(tool) {
        val startX = tool.parameters.find { it.name == "start_x" }?.value?.toIntOrNull()
        val startY = tool.parameters.find { it.name == "start_y" }?.value?.toIntOrNull()
        val endX = tool.parameters.find { it.name == "end_x" }?.value?.toIntOrNull()
        val endY = tool.parameters.find { it.name == "end_y" }?.value?.toIntOrNull()
        val duration = tool.parameters.find { it.name == "duration" }?.value?.toIntOrNull() ?: 300

        if (startX == null || startY == null || endX == null || endY == null) {
                    return@withAccessibilityCheck ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                        error = "Missing or invalid coordinates. 'start_x', 'start_y', 'end_x', and 'end_y' must be valid integers."
            )
        }

            withContext(Dispatchers.Main) { operationOverlay.hideImmediately() }

            // 使用无障碍服务执行滑动
            val result = performAccessibilitySwipe(startX, startY, endX, endY, duration)

                if (result) {
                withContext(Dispatchers.Main) {
                    operationOverlay.showSwipe(startX, startY, endX, endY)
                    operationOverlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "swipe",
                                        actionDescription =
                                                "Successfully performed swipe from ($startX, $startY) to ($endX, $endY) via accessibility service"
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to perform swipe via accessibility service."
                )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing swipe", e)
            operationOverlay.hide()
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing swipe: ${e.message}"
            )
        }
    }

    // 使用无障碍服务执行点击的辅助方法
    private suspend fun performAccessibilityClick(x: Int, y: Int): Boolean {
        return try {
            UIHierarchyManager.performClick(context, x, y)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing accessibility click", e)
            return false
        }
    }

    // 使用无障碍服务执行长按的辅助方法
    private suspend fun performAccessibilityLongPress(x: Int, y: Int): Boolean {
        return try {
            UIHierarchyManager.performLongPress(context, x, y)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing accessibility long press", e)
            return false
        }
    }

    // 使用无障碍服务执行滑动的辅助方法
    private suspend fun performAccessibilitySwipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            duration: Int
    ): Boolean {
        return try {
            UIHierarchyManager.performSwipe(context, startX, startY, endX, endY, duration.toLong())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing accessibility swipe", e)
            return false
        }
    }

    protected fun accessibilityKeyAction(keyCode: String): Int? = when (keyCode) {
        "KEYCODE_BACK" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        "KEYCODE_HOME" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        "KEYCODE_RECENTS" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
        "KEYCODE_NOTIFICATIONS" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        "KEYCODE_QUICK_SETTINGS" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        "KEYCODE_POWER_DIALOG" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
        else -> null
    }

    /** 模拟按键操作 */
    private suspend fun pressKeyWithAccessibility(tool: AITool): ToolResult {
        val keyCode = tool.parameters.find { it.name == "key_code" }?.value

        if (keyCode == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing 'key_code' parameter."
            )
        }

        try {
            // 将字符串keyCode转换为AccessibilityService中的常量
            val keyAction = accessibilityKeyAction(keyCode)

            if (keyAction != null) {
                // 通过UIHierarchyManager请求远程服务执行操作
                val success = UIHierarchyManager.performGlobalAction(context, keyAction)
                return if (success) {
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    UIActionResultData(
                                            actionType = "keyPress",
                                            actionDescription =
                                                    "Successfully pressed key: $keyCode via accessibility service"
                                    ),
                            error = ""
                    )
                } else {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error =
                                    "Failed to press key: $keyCode via accessibility service. Not all keys are supported."
                    )
                }
            } else {
                // 如果不是标准全局操作，返回不支持的错误
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Key: $keyCode is not supported via accessibility service. Only system keys like BACK, HOME, etc. are supported."
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error pressing key", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error pressing key: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    private suspend fun captureScreenshotToFileWithAccessibility(tool: AITool): Pair<String?, Pair<Int, Int>?> {
        return try {
            val screenshotDir = OperitPaths.cleanOnExitDir()

            val shortName = System.currentTimeMillis().toString().takeLast(4)
            val file = File(screenshotDir, "$shortName.png")

            val success = UIHierarchyManager.takeScreenshot(context, file.absolutePath, "png")
            if (!success) {
                AppLogger.w(TAG, "captureScreenshotForAgent: AIDL takeScreenshot failed")
                return Pair(null, null)
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val dimensions = if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }

            Pair(file.absolutePath, dimensions)
        } catch (e: Exception) {
            AppLogger.e(TAG, "captureScreenshot via accessibility failed", e)
            Pair(null, null)
        }
    }

    private suspend fun captureScreenshotWithAccessibility(tool: AITool): Pair<String?, Pair<Int, Int>?> {
        return captureScreenshotToFile(tool)
    }

    private suspend fun captureScreenshotBitmapWithAccessibility(tool: AITool): Pair<Bitmap?, Pair<Int, Int>?> {
        val (filePath, dimensions) = captureScreenshot(tool)
        if (filePath == null) {
            return Pair(null, dimensions)
        }

        val bitmap = BitmapFactory.decodeFile(filePath) ?: return Pair(null, dimensions)
        val resolvedDimensions = dimensions ?: Pair(bitmap.width, bitmap.height)
        return Pair(bitmap, resolvedDimensions)
    }

    private fun parseBounds(boundsString: String): android.graphics.Rect {
        // 解析 "[left,top][right,bottom]" 格式的边界字符串
        val rect = android.graphics.Rect()
        try {
            val parts = boundsString.replace("[", "").replace("]", ",").split(",")
            if (parts.size >= 4) {
                rect.left = parts[0].toInt()
                rect.top = parts[1].toInt()
                rect.right = parts[2].toInt()
                rect.bottom = parts[3].toInt()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing bounds: $boundsString", e)
        }
        return rect
    }
}
