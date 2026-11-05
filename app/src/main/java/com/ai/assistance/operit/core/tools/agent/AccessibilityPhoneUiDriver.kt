package com.ai.assistance.operit.core.tools.agent

import android.content.Context
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
) : PhoneUiDriver {
    private val revision = AtomicLong()

    override suspend fun read(): PhoneUiSnapshot = withContext(Dispatchers.IO) {
        val xml = UIHierarchyManager.getUIHierarchy(context)
        if (xml.isBlank()) throw PhoneUiException("Accessibility returned no main-screen hierarchy")
        if (xml.length > 2_000_000) throw PhoneUiException("Accessibility hierarchy exceeds the local read limit")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val nodes = mutableListOf<PhoneUiNode>()
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    if (nodes.size >= 5000) throw PhoneUiException("Accessibility hierarchy exceeds 5000 nodes")
                    fun attr(name: String): String = parser.getAttributeValue(null, name).orEmpty()
                    val password = attr("password") == "true"
                    val className = attr("class")
                    val boundsMatch = boundsPattern.matchEntire(attr("bounds"))
                    val bounds = boundsMatch?.groupValues?.drop(1)?.map { it.toInt() }?.let {
                        PhoneBounds(it[0], it[1], it[2], it[3])
                    }
                    nodes += PhoneUiNode(
                        packageName = attr("package"), resourceId = attr("resource-id"), className = className,
                        text = if (password) "" else attr("text"), description = if (password) "" else attr("content-desc"),
                        bounds = bounds, clickable = attr("clickable") == "true",
                        editable = attr("editable") == "true" || className.endsWith("EditText"),
                        focused = attr("focused") == "true", enabled = attr("enabled") != "false", password = password,
                        selected = attr("selected") == "true", checked = attr("checked") == "true",
                    )
                }
                parser.next()
            }
        } catch (e: PhoneUiException) {
            throw e
        } catch (e: Exception) {
            throw PhoneUiException("Could not parse accessibility hierarchy: ${e.javaClass.simpleName}")
        }
        val packageName = nodes.firstOrNull { it.packageName.isNotBlank() }?.packageName
            ?: throw PhoneUiException("Main-screen hierarchy has no app identity")
        PhoneUiSnapshot(revision.incrementAndGet(), packageName, nodes)
    }

    override suspend fun tap(node: PhoneUiNode): Boolean = inputScope {
        withContext(Dispatchers.IO) {
            val current = read()
            if (current.packageName != node.packageName || node !in current.nodes) {
                throw PhoneUiException("Tap target changed before dispatch; no tap was sent")
            }
            val bounds = node.bounds ?: throw PhoneUiException("Tap target has no bounds")
            UIHierarchyManager.performClick(context, bounds.centerX, bounds.centerY)
        }
    }

    override suspend fun type(node: PhoneUiNode, text: String): Boolean = inputScope {
        withContext(Dispatchers.IO) {
            val current = read()
            val focused = current.nodes.filter { it.packageName == node.packageName && it.focused && it.editable }
            if (current.packageName != node.packageName || focused.size != 1 || focused.single() != node) {
                throw PhoneUiException("Input focus changed before typing; no text was sent")
            }
            val focusedId = UIHierarchyManager.findFocusedNodeId(context)
                ?: throw PhoneUiException("Accessibility cannot identify the focused input")
            UIHierarchyManager.setTextOnNode(context, focusedId, text)
        }
    }

    private companion object {
        val boundsPattern = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
    }
}
