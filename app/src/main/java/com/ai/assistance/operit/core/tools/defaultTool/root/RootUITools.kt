package com.ai.assistance.operit.core.tools.defaultTool.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIActionResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.defaultTool.admin.AdminUITools
import com.ai.assistance.operit.core.tools.permissions.PermissionCapabilityResolver
import com.ai.assistance.operit.core.tools.system.ShellIdentity
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Root-level UI tools that use shell commands (uiautomator, input) for robust UI automation.
 * This implementation is modeled after DebuggerUITools but operates without accessibility fallbacks.
 */
open class RootUITools(context: Context) : AdminUITools(context) {

    companion object {
        private const val TAG = "RootUITools"
    }

    override val uiShellIdentity: ShellIdentity = ShellIdentity.SHELL

    private fun getDisplayArg(tool: AITool): String {
        val display = tool.parameters.find { it.name.equals("display", ignoreCase = true) }?.value?.trim()
        return if (!display.isNullOrEmpty()) "-d $display " else ""
    }

    /** Performs a tap action using the 'input tap' shell command. */
    override suspend fun tap(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.tap(tool)

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

        val overlay = operationOverlay
        withContext(Dispatchers.Main) { overlay.hideImmediately() }

        try {
            AppLogger.d(TAG, "Attempting to tap at coordinates: ($x, $y) via shell command")
            val command = "input ${getDisplayArg(tool)}tap $x $y"
            val result = executeUiShellCommand(command)

            return if (result.success) {
                AppLogger.d(TAG, "Tap successful at coordinates: ($x, $y)")
                withContext(Dispatchers.Main) {
                    overlay.showTap(x, y)
                    overlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "tap",
                            actionDescription = "Successfully tapped at ($x, $y) via shell command",
                                        coordinates = Pair(x, y)
                        )
                )
            } else {
                AppLogger.e(TAG, "Tap failed at coordinates: ($x, $y), error: ${result.stderr}")
                withContext(Dispatchers.Main) { overlay.hide() }
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to tap at ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error tapping at coordinates ($x, $y)", e)
            withContext(Dispatchers.Main) { overlay.hide() }
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error tapping at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    override suspend fun longPress(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.longPress(tool)

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

        val overlay = operationOverlay
        withContext(Dispatchers.Main) { overlay.hideImmediately() }

        val durationMs = 800

        try {
            AppLogger.d(TAG, "Attempting to long press at coordinates: ($x, $y) via shell swipe command")
            val command = "input ${getDisplayArg(tool)}swipe $x $y $x $y $durationMs"
            val result = executeUiShellCommand(command)

            return if (result.success) {
                AppLogger.d(TAG, "Long press successful at coordinates: ($x, $y)")
                withContext(Dispatchers.Main) {
                    overlay.showTap(x, y)
                    overlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "long_press",
                            actionDescription = "Successfully long pressed at ($x, $y) via shell command",
                                        coordinates = Pair(x, y)
                        )
                )
            } else {
                AppLogger.e(TAG, "Long press failed at coordinates: ($x, $y), error: ${result.stderr}")
                withContext(Dispatchers.Main) { overlay.hide() }
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to long press at ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error long pressing at coordinates ($x, $y)", e)
            withContext(Dispatchers.Main) { overlay.hide() }
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error long pressing at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** Performs a swipe action using the 'input swipe' shell command. */
    override suspend fun swipe(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.swipe(tool)

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

        val overlay = operationOverlay
        withContext(Dispatchers.Main) { overlay.hideImmediately() }

        try {
            AppLogger.d(TAG, "Swiping from ($startX, $startY) to ($endX, $endY) via shell")
            val command = "input ${getDisplayArg(tool)}swipe $startX $startY $endX $endY $duration"
            val result = executeUiShellCommand(command)

            return if (result.success) {
                AppLogger.d(TAG, "Swipe successful")
                withContext(Dispatchers.Main) {
                    overlay.showSwipe(startX, startY, endX, endY)
                    overlay.hide()
                }
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "swipe",
                            actionDescription = "Successfully swiped from ($startX, $startY) to ($endX, $endY)"
                        )
                )
            } else {
                AppLogger.e(TAG, "Swipe failed: ${result.stderr}")
                withContext(Dispatchers.Main) { overlay.hide() }
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to perform swipe: ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error performing swipe", e)
            withContext(Dispatchers.Main) { overlay.hide() }
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error performing swipe: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** Clicks a UI element by finding it via uiautomator dump. */
    override suspend fun clickElement(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.clickElement(tool)

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
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

        if (bounds != null) {
            extractCenterCoordinates(bounds)?.let { (x, y) ->
                val tapTool = AITool("tap", listOf(ToolParameter("x", x.toString()), ToolParameter("y", y.toString())))
                return tap(tapTool)
            }
                ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid bounds format. Should be: [left,top][right,bottom]"
                )
        }

        return clickElementWithUiautomator(tool)
    }

    /** Sets input text by clearing the field and pasting from the clipboard. */
    override suspend fun setInputText(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.setInputText(tool)

        val text = tool.parameters.find { it.name == "text" }?.value ?: ""

        try {
            val overlay = operationOverlay
            val displayMetrics = context.resources.displayMetrics
            withContext(Dispatchers.Main) {
                overlay.showTextInput(displayMetrics.widthPixels / 2, displayMetrics.heightPixels / 2, text)
            }

            AppLogger.d(TAG, "Clearing text field with KEYCODE_CLEAR")
            executeUiShellCommand("input ${getDisplayArg(tool)}keyevent KEYCODE_CLEAR")
            delay(300)

            if (text.isEmpty()) {
                // 成功后主动隐藏overlay
                withContext(Dispatchers.Main) { overlay.hide() }
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                    result = UIActionResultData("textInput", "Successfully cleared input field")
                )
            }

            AppLogger.d(TAG, "Setting text to clipboard and pasting via ADB: $text")
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text))
            }
            delay(100)

            val pasteResult = executeUiShellCommand("input ${getDisplayArg(tool)}keyevent KEYCODE_PASTE")
            return if (pasteResult.success) {
                // 成功后主动隐藏overlay
                withContext(Dispatchers.Main) { overlay.hide() }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                            UIActionResultData(
                            "textInput",
                            "Successfully set input text to: $text via clipboard paste"
                        )
                )
            } else {
                withContext(Dispatchers.Main) { overlay.hide() }
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to paste text: ${pasteResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error setting input text", e)
            val overlay = operationOverlay
            withContext(Dispatchers.Main) { overlay.hide() }
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error setting input text: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** Executes a key press using the 'input keyevent' shell command. */
    override suspend fun pressKey(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.pressKey(tool)

        val keyCode = tool.parameters.find { it.name == "key_code" }?.value
            ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing 'key_code' parameter."
            )

        try {
            val result = executeUiShellCommand("input ${getDisplayArg(tool)}keyevent $keyCode")
            return if (result.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = UIActionResultData("keyPress", "Successfully pressed key: $keyCode")
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
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = "Error pressing key: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** Gets page info using uiautomator dump and dumpsys. */
    override suspend fun getPageInfo(tool: AITool): ToolResult {
        if (!PermissionCapabilityResolver.shellSnapshot(context).canUseRoot) return super.getPageInfo(tool)

        return try {
            val uiData = getUIDataFromShell(tool)
                ?: return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to retrieve UI data."
                )

            val focusInfo = extractFocusInfoFromShell(uiData.windowInfo)
            val simplifiedLayout = simplifyLayoutFromXml(uiData.uiXml)

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

    private data class UIData(val uiXml: String, val windowInfo: String)

    private suspend fun getUIDataFromShell(tool: AITool): UIData? {
        return try {
            AppLogger.d(TAG, "Getting UI data via ADB")

            val displayId = tool.parameters
                .find { it.name.equals("display", ignoreCase = true) }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            var dumpResult = if (displayId != null) {
                val cmd = "uiautomator dump --display-id $displayId /sdcard/window_dump.xml"
                AppLogger.d(TAG, "UI dump using explicit display-id=$displayId")
                executeUiShellCommand(cmd)
            } else {
                executeUiShellCommand("uiautomator dump /sdcard/window_dump.xml")
            }

            if (!dumpResult.success && displayId != null) {
                AppLogger.w(TAG, "uiautomator dump with explicit display-id failed, falling back: ${dumpResult.stderr}")
                dumpResult = executeUiShellCommand("uiautomator dump /sdcard/window_dump.xml")
            }

            if (!dumpResult.success) {
                AppLogger.e(TAG, "uiautomator dump failed: ${dumpResult.stderr}")
                return null
            }

            val readResult = executeUiShellCommand("cat /sdcard/window_dump.xml")
            if (!readResult.success) {
                AppLogger.e(TAG, "Reading UI dump file failed: ${readResult.stderr}")
                return null
            }

            var windowInfo = getWindowInfoFromShell()
            if (windowInfo.isEmpty()) {
                AppLogger.w(TAG, "Failed to get window info, retrying after 500ms")
                delay(500)
                windowInfo = getWindowInfoFromShell()
            }

            UIData(readResult.stdout, windowInfo)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting UI data", e)
            null
        }
    }

    private suspend fun getWindowInfoFromShell(): String {
        val commands =
            listOf(
                "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
                "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'",
                "dumpsys activity activities | grep -E 'topResumedActivity|topActivity'"
            )
        for (command in commands) {
            try {
                val result = executeUiShellCommand(command)
                if (result.success && result.stdout.isNotBlank()) {
                    AppLogger.d(TAG, "Successfully got window info with: $command")
                    return result.stdout
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Command failed: '$command'", e)
            }
        }
        AppLogger.e(TAG, "All attempts to get window info failed.")
        return ""
    }

    private data class UINodeShell(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,
        val children: MutableList<UINodeShell> = mutableListOf()
    )

    private fun simplifyLayoutFromXml(xml: String): SimplifiedUINode {
        return try {
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
                            } else {
                                nodeStack.lastOrNull()?.children?.add(newNode)
                            }
                            nodeStack.add(newNode)
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "node") nodeStack.removeLastOrNull()
                }
                parser.next()
            }
            rootNode?.toUINodeSimplified() ?: SimplifiedUINode(null, null, null, null, null, false, emptyList())
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing XML layout", e)
            SimplifiedUINode(null, null, null, null, null, false, emptyList())
        }
    }

    private fun UINodeShell.toUINodeSimplified(): SimplifiedUINode = SimplifiedUINode(
        className, text, contentDesc, resourceId, bounds, isClickable, children.map { it.toUINodeSimplified() }
    )

    private fun createNodeShell(parser: XmlPullParser): UINodeShell {
        return UINodeShell(
            className = parser.getAttributeValue(null, "class")?.substringAfterLast('.'),
            text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n"),
            contentDesc = parser.getAttributeValue(null, "content-desc"),
            resourceId = parser.getAttributeValue(null, "resource-id"),
            bounds = parser.getAttributeValue(null, "bounds"),
            isClickable = parser.getAttributeValue(null, "clickable") == "true"
        )
    }

    private data class FocusInfoShell(var packageName: String? = null, var activityName: String? = null)

    private fun extractFocusInfoFromShell(windowInfo: String): FocusInfoShell {
        val result = FocusInfoShell()
        if (windowInfo.isBlank()) {
            AppLogger.w(TAG, "Window info is empty, cannot extract focus.")
            return result
        }

        val patterns =
            listOf(
                "mCurrentFocus=.*?\\s+([a-zA-Z0-9_.]+)/([^\\s}]+)".toRegex(),
                "mFocusedApp=.*?ActivityRecord\\{.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex(),
                "topActivity=ComponentInfo\\{([a-zA-Z0-9_.]+)/\\.?([^}]+)\\}".toRegex()
            )

        for (pattern in patterns) {
            val match = pattern.find(windowInfo)
            if (match != null && match.groupValues.size >= 3) {
                result.packageName = match.groupValues[1]
                result.activityName = match.groupValues[2]
                AppLogger.d(TAG, "Extracted from pattern ${patterns.indexOf(pattern)}: ${result.packageName}/${result.activityName}")
                return result
            }
        }

        AppLogger.w(TAG, "Could not extract focus info from window data.")
        return result
    }

    private suspend fun clickElementWithUiautomator(tool: AITool): ToolResult {
        AppLogger.d(TAG, "Using uiautomator to click element")
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0

        try {
            val dumpResult = executeUiShellCommand("uiautomator dump /sdcard/window_dump.xml")
            if (!dumpResult.success) {
                return ToolResult(tool.name, false, StringResultData(""), "Failed to dump UI hierarchy: ${dumpResult.stderr}")
            }
            val readResult = executeUiShellCommand("cat /sdcard/window_dump.xml")
            if (!readResult.success) {
                return ToolResult(tool.name, false, StringResultData(""), "Failed to read UI dump: ${readResult.stderr}")
            }
            val xml = readResult.stdout
            
            val partialMatch = tool.parameters.find { it.name == "partialMatch" }?.value?.toBoolean() ?: false

            fun buildPattern(name: String, value: String?) = value?.let {
                if (partialMatch) "$name=\".*?${Regex.escape(it)}.*?\""
                else "$name=\"(?:.*?:id/)?${Regex.escape(it)}\""
            }
            
            val attributes = listOfNotNull(
                buildPattern("resource-id", resourceId),
                buildPattern("class", className),
                buildPattern("content-desc", contentDesc)
            ).joinToString(".*?")
            
            if (attributes.isEmpty()) {
                 return ToolResult(tool.name, false, StringResultData(""), "No element identifiers provided for click.")
            }

            val nodeRegex = "<node[^>]*?$attributes[^>]*?>".toRegex()
            val matchingNodes = nodeRegex.findAll(xml).toList()

            if (matchingNodes.isEmpty()) {
                return ToolResult(tool.name, false, StringResultData(""), "No matching element found.")
            }
            if (index >= matchingNodes.size) {
                return ToolResult(tool.name, false, StringResultData(""), "Index out of range. Found ${matchingNodes.size}, requested $index.")
            }

            val nodeText = matchingNodes[index].value
            val bounds = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex().find(nodeText)
                ?: return ToolResult(tool.name, false, StringResultData(""), "Failed to extract bounds from element.")

            val (x1, y1, x2, y2) = bounds.destructured
            val centerX = (x1.toInt() + x2.toInt()) / 2
            val centerY = (y1.toInt() + y2.toInt()) / 2

            return tap(AITool("tap", listOf(ToolParameter("x", centerX.toString()), ToolParameter("y", centerY.toString()))))

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error clicking with uiautomator", e)
            return ToolResult(tool.name, false, StringResultData(""), "Error clicking element: ${e.message}")
        } finally {
            executeUiShellCommand("rm /sdcard/window_dump.xml")
        }
    }
}
