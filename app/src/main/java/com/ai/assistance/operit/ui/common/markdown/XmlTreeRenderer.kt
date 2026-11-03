package com.ai.assistance.operit.ui.common.markdown

import android.util.Xml
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

private const val MAX_XML_LENGTH = 200_000
private const val MAX_XML_DEPTH = 64
private const val MAX_XML_NODES = 5_000
private const val MAX_INDENT_DEPTH = 8

private data class XmlTreeAttribute(
    val name: String,
    val value: String,
)

private sealed interface XmlTreeEntry {
    data class Element(val node: XmlTreeNode) : XmlTreeEntry
    data class Text(val value: String) : XmlTreeEntry
}

private data class XmlTreeNode(
    val name: String,
    val attributes: List<XmlTreeAttribute>,
    val entries: MutableList<XmlTreeEntry> = mutableListOf(),
)

/**
 * Renders a complete XML document as an expandable tree. Incomplete or unsupported XML falls
 * back to the original monospace representation, which is important while a response is streaming.
 */
@Composable
internal fun XmlTreeRenderer(
    xmlContent: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val root = remember(xmlContent) { parseXmlTree(xmlContent) }

    if (root == null) {
        Text(
            text = xmlContent,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        XmlTreeNodeView(
            node = root,
            path = "root",
            depth = 0,
            textColor = textColor,
        )
    }
}

@Composable
private fun XmlTreeNodeView(
    node: XmlTreeNode,
    path: String,
    depth: Int,
    textColor: Color,
) {
    val meaningfulEntries = node.entries.filterNot {
        it is XmlTreeEntry.Text && it.value.isBlank()
    }
    val elementChildren = meaningfulEntries.filterIsInstance<XmlTreeEntry.Element>()
    val textEntries = meaningfulEntries.filterIsInstance<XmlTreeEntry.Text>()
    val isTextOnlyLeaf = elementChildren.isEmpty() && textEntries.isNotEmpty()
    val isExpandable = elementChildren.isNotEmpty()
    var expanded by rememberSaveable(path) { mutableStateOf(depth < 2) }

    val indentation = (depth.coerceAtMost(MAX_INDENT_DEPTH) * 12).dp
    val attributes = node.attributes.joinToString(separator = "") { attribute ->
        " ${attribute.name}=\"${attribute.value.escapeXmlAttribute()}\""
    }
    val expansionStateDescription = if (isExpandable) {
        stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentation, top = 1.dp, bottom = 1.dp)
            .then(
                if (isExpandable) {
                    Modifier.clickable(role = Role.Button) { expanded = !expanded }
                } else {
                    Modifier
                }
            )
            .then(
                if (isExpandable) {
                    Modifier.semantics {
                        stateDescription = checkNotNull(expansionStateDescription)
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (isExpandable) {
                if (expanded) "▼" else "▶"
            } else {
                "•"
            },
            color = textColor.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                isTextOnlyLeaf -> {
                    val value = textEntries
                        .joinToString(separator = "") { it.value }
                        .escapeXmlText()
                    "<${node.name}$attributes>$value</${node.name}>"
                }
                meaningfulEntries.isEmpty() -> "<${node.name}$attributes />"
                !expanded -> "<${node.name}$attributes>…</${node.name}>"
                else -> "<${node.name}$attributes>"
            },
            color = textColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }

    if (!isExpandable || !expanded) return

    meaningfulEntries.forEachIndexed { index, entry ->
        key("$path/$index") {
            when (entry) {
                is XmlTreeEntry.Element -> XmlTreeNodeView(
                    node = entry.node,
                    path = "$path/$index",
                    depth = depth + 1,
                    textColor = textColor,
                )
                is XmlTreeEntry.Text -> {
                    val value = entry.value.escapeXmlText()
                    if (entry.value.isNotBlank()) {
                        Text(
                            text = value,
                            color = textColor.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.padding(
                                start = ((depth + 1).coerceAtMost(MAX_INDENT_DEPTH) * 12 + 20).dp,
                                vertical = 2.dp,
                            ),
                        )
                    }
                }
            }
        }
    }

    Text(
        text = "</${node.name}>",
        color = textColor.copy(alpha = 0.86f),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.padding(
            start = (depth.coerceAtMost(MAX_INDENT_DEPTH) * 12 + 20).dp,
            top = 1.dp,
            bottom = 2.dp,
        ),
    )
}

private fun parseXmlTree(xmlContent: String): XmlTreeNode? {
    val source = xmlContent.trim()
    if (source.isEmpty() || source.length > MAX_XML_LENGTH) return null
    if (FORBIDDEN_XML_DECLARATION.containsMatchIn(source)) return null

    return runCatching {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(source))
        }
        val stack = ArrayDeque<XmlTreeNode>()
        var root: XmlTreeNode? = null
        var nodeCount = 0
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (stack.size >= MAX_XML_DEPTH) {
                        error("XML nesting is too deep")
                    }
                    nodeCount++
                    if (nodeCount > MAX_XML_NODES) {
                        error("XML contains too many nodes")
                    }
                    val node = XmlTreeNode(
                        name = parser.name,
                        attributes = List(parser.attributeCount) { index ->
                            XmlTreeAttribute(
                                name = parser.getAttributeName(index),
                                value = parser.getAttributeValue(index),
                            )
                        },
                    )
                    if (stack.isEmpty()) {
                        if (root != null) error("XML must have one root element")
                        root = node
                    } else {
                        stack.last().entries += XmlTreeEntry.Element(node)
                    }
                    stack.addLast(node)
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    val text = parser.text.orEmpty()
                    if (stack.isNotEmpty() && text.isNotEmpty()) {
                        nodeCount++
                        if (nodeCount > MAX_XML_NODES) {
                            error("XML contains too many nodes")
                        }
                        stack.last().entries += XmlTreeEntry.Text(text)
                    } else if (text.isNotBlank()) {
                        error("Text outside the root element is not supported")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (stack.isEmpty() || stack.last().name != parser.name) {
                        error("Mismatched XML tag")
                    }
                    stack.removeLast()
                }
            }
            event = parser.nextToken()
        }

        if (stack.isNotEmpty()) error("Incomplete XML document")
        root ?: error("XML root element is missing")
    }.getOrNull()
}

private fun String.escapeXmlText(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun String.escapeXmlAttribute(): String =
    escapeXmlText()
        .replace("\"", "&" + "quot;")
        .replace("'", "&" + "apos;")

private val FORBIDDEN_XML_DECLARATION = Regex(
    pattern = "<!\\s*(DOCTYPE|ENTITY)",
    option = RegexOption.IGNORE_CASE,
)
