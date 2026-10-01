package com.ai.assistance.operit.core.tools.defaultTool.accessbility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIActionResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.util.ImagePoolManager
import java.io.StringReader
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlinx.coroutines.delay

/** 无障碍级别的UI工具，使用Android无障碍服务API实现UI操作 */
open class AccessibilityUITools(context: Context) : StandardUITools(context) {

    companion object {
        private const val TAG = "AccessibilityUITools"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 300L
    }

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
    override suspend fun getPageInfo(tool: AITool): ToolResult {
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
    override suspend fun clickElement(tool: AITool): ToolResult {
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
    override suspend fun setInputText(tool: AITool): ToolResult {
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
    override suspend fun tap(tool: AITool): ToolResult {
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
    override suspend fun longPress(tool: AITool): ToolResult {
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
    override suspend fun swipe(tool: AITool): ToolResult {
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

    override suspend fun captureScreenshotToFile(tool: AITool): Pair<String?, Pair<Int, Int>?> {
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
