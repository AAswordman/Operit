package com.ai.assistance.operit.core.tools.agent

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import java.io.StringReader
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** The current provider is main-display-only. Never construct this driver for a virtual screen. */
internal class AccessibilityPhoneUiDriver(
    private val context: Context,
    private val inputScope: suspend (suspend () -> Boolean) -> Boolean,
    private val tapAt: suspend (Int, Int) -> Boolean,
) : PhoneUiDriver {
    private val revision = AtomicLong()

    override suspend fun read(): PhoneUiSnapshot = withContext(Dispatchers.IO) {
        val xml = UIHierarchyManager.getUIHierarchy(context)
        if (xml.isBlank()) throw PhoneUiUnavailableException("Accessibility returned no main-screen hierarchy")
        if (xml.length > 2_000_000) throw PhoneUiException("Accessibility hierarchy exceeds the local read limit")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val nodes = mutableListOf<PhoneUiNode>()
        val parents = mutableListOf<Int>()
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    if (nodes.size >= 5000) throw PhoneUiException("Accessibility hierarchy exceeds 5000 nodes")
                    fun attr(name: String): String = parser.getAttributeValue(null, name).orEmpty()
                    val password = attr("password") == "true"
                    val className = attr("class")
                    val bounds = PhoneBounds.parse(attr("bounds"))
                    nodes += PhoneUiNode(
                        packageName = attr("package"), resourceId = attr("resource-id"), className = className,
                        text = if (password) "" else attr("text"), description = if (password) "" else attr("content-desc"),
                        bounds = bounds, clickable = attr("clickable") == "true",
                        editable = attr("editable") == "true" || className.endsWith("EditText"),
                        focused = attr("focused") == "true", enabled = attr("enabled") != "false", password = password,
                        selected = attr("selected") == "true", checked = attr("checked") == "true",
                        ref = nodes.size + 1, parentRef = parents.lastOrNull(),
                        visibleToUser = listOf("visible-to-user", "visible", "visibleToUser")
                            .map { attr(it) }.firstOrNull { it == "true" || it == "false" }?.toBoolean(),
                        hint = attr("hint"), scrollable = attr("scrollable") == "true",
                    )
                    parents += nodes.size
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "node" && parents.isNotEmpty()) {
                    parents.removeAt(parents.lastIndex)
                }
                parser.next()
            }
        } catch (e: PhoneUiException) {
            throw e
        } catch (e: Exception) {
            throw PhoneUiException("Could not parse accessibility hierarchy: ${e.javaClass.simpleName}")
        }
        val packageName = nodes.firstOrNull { it.packageName.isNotBlank() }?.packageName
            ?: throw PhoneUiUnavailableException("Main-screen hierarchy has no app identity")
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: throw PhoneUiException("Main display is unavailable")
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        PhoneUiSnapshot(revision.incrementAndGet(), packageName, nodes, PhoneBounds(0, 0, size.x, size.y))
    }

    override suspend fun tap(node: PhoneUiNode): Boolean = withContext(Dispatchers.IO) {
        // The executor has just resolved this control; use the session's selected input channel.
        if (!node.enabled || node.password || node.visibleToUser == false) throw PhoneUiException("Tap target is not available")
        val bounds = node.bounds ?: throw PhoneUiException("Tap target has no bounds")
        if (!bounds.isUsable) throw PhoneUiException("Tap target has no usable bounds")
        AppLogger.d("PhoneAgent", "node_tap_request: pixel=(${bounds.centerX},${bounds.centerY}) resource_id=${node.resourceId}")
        tapAt(bounds.centerX, bounds.centerY)
    }

    override suspend fun type(node: PhoneUiNode, text: String): Boolean = inputScope {
        withContext(Dispatchers.IO) {
            if (!node.enabled || !node.focused || !node.editable || node.password || node.visibleToUser == false) {
                throw PhoneUiException("Typing requires the freshly checked ordinary focused input")
            }
            val focusedId = UIHierarchyManager.findFocusedNodeId(context)
                ?: throw PhoneUiException("Accessibility cannot identify the focused input")
            UIHierarchyManager.setTextOnNode(context, focusedId, text)
        }
    }

}
