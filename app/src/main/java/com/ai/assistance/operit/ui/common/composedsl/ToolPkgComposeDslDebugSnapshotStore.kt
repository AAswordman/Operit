package com.ai.assistance.operit.ui.common.composedsl

import android.content.Context
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslRenderResult
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

data class ToolPkgComposeDslDebugSnapshot(
    val routeInstanceId: String,
    val containerPackageName: String,
    val uiModuleId: String,
    val fallbackTitle: String,
    val scriptScreenPath: String?,
    val scriptSource: String?,
    val phase: String,
    val rawRenderResultText: String?,
    val renderResult: ToolPkgComposeDslRenderResult?,
    val errorMessage: String?,
    val isLoading: Boolean,
    val isDispatching: Boolean,
    val updatedAtMillis: Long
)

data class ToolPkgComposeDslLayoutSnapshot(
    val routeInstanceId: String,
    val nodePath: String,
    val nodeType: String,
    val nodeKey: String?,
    val rootX: Float,
    val rootY: Float,
    val width: Float,
    val height: Float,
    val windowX: Float,
    val windowY: Float,
    val updatedAtMillis: Long
)

object ToolPkgComposeDslDebugSnapshotStore {
    private const val DEBUG_DIR = "debug/compose_dsl_dump/current"
    private val lock = Any()
    private val snapshots = LinkedHashMap<String, ToolPkgComposeDslDebugSnapshot>()
    private val layoutSnapshots =
        LinkedHashMap<String, LinkedHashMap<String, ToolPkgComposeDslLayoutSnapshot>>()
    private var activeRouteInstanceId: String? = null
    private var latestRouteInstanceId: String? = null

    fun update(snapshot: ToolPkgComposeDslDebugSnapshot) {
        synchronized(lock) {
            snapshots[snapshot.routeInstanceId] = snapshot
            val validNodePaths = snapshot.renderResult?.let { collectNodePaths(it.tree, "0") }
            if (validNodePaths != null) {
                layoutSnapshots[snapshot.routeInstanceId]?.entries?.removeAll { (nodePath, _) ->
                    nodePath !in validNodePaths
                }
            }
            activeRouteInstanceId = snapshot.routeInstanceId
            latestRouteInstanceId = snapshot.routeInstanceId
        }
    }

    fun updateLayout(snapshot: ToolPkgComposeDslLayoutSnapshot) {
        synchronized(lock) {
            val routeLayouts =
                layoutSnapshots.getOrPut(snapshot.routeInstanceId) { LinkedHashMap() }
            routeLayouts[snapshot.nodePath] = snapshot
            activeRouteInstanceId = snapshot.routeInstanceId
            latestRouteInstanceId = snapshot.routeInstanceId
        }
    }

    fun clear(routeInstanceId: String) {
        synchronized(lock) {
            snapshots.remove(routeInstanceId)
            layoutSnapshots.remove(routeInstanceId)
            if (activeRouteInstanceId == routeInstanceId) {
                activeRouteInstanceId = snapshots.keys.lastOrNull()
            }
            if (latestRouteInstanceId == routeInstanceId) {
                latestRouteInstanceId = snapshots.keys.lastOrNull()
            }
        }
    }

    fun dumpCurrentSnapshot(context: Context): File {
        val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
            "External files directory is unavailable"
        }
        val outputDir =
            File(externalFilesDir, DEBUG_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
        val snapshot = currentSnapshot()
        if (snapshot == null) {
            writeText(
                File(outputDir, "dump_manifest.json"),
                JSONObject(
                    linkedMapOf(
                        "success" to false,
                        "error" to "No active compose_dsl snapshot available"
                    )
                ).toString(2)
            )
            return outputDir
        }

        val timestamp =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(snapshot.updatedAtMillis))
        val metadata =
            linkedMapOf(
                "success" to true,
                "routeInstanceId" to snapshot.routeInstanceId,
                "containerPackageName" to snapshot.containerPackageName,
                "uiModuleId" to snapshot.uiModuleId,
                "fallbackTitle" to snapshot.fallbackTitle,
                "scriptScreenPath" to snapshot.scriptScreenPath,
                "phase" to snapshot.phase,
                "isLoading" to snapshot.isLoading,
                "isDispatching" to snapshot.isDispatching,
                "errorMessage" to snapshot.errorMessage,
                "updatedAtMillis" to snapshot.updatedAtMillis,
                "updatedAt" to timestamp
            )
        writeText(File(outputDir, "dump_manifest.json"), JSONObject(metadata as Map<*, *>).toString(2))

        val routeLayouts =
            synchronized(lock) {
                layoutSnapshots[snapshot.routeInstanceId]
                    ?.values
                    ?.sortedBy { it.nodePath }
                    .orEmpty()
            }
        writeText(
            File(outputDir, "layout_nodes.json"),
            JSONArray().apply {
                routeLayouts.forEach { put(layoutToJson(it)) }
            }.toString(2)
        )
        writeText(File(outputDir, "layout_nodes.txt"), buildLayoutListing(routeLayouts))

        snapshot.scriptSource?.let { writeText(File(outputDir, "source_script.js"), it) }
        snapshot.rawRenderResultText?.let { rawText ->
            writeText(File(outputDir, "raw_render_result.txt"), rawText)
            parseJsonString(rawText)?.let { parsedRaw ->
                val prettyRaw =
                    when (parsedRaw) {
                        is JSONObject -> parsedRaw.toString(2)
                        is JSONArray -> parsedRaw.toString(2)
                        else -> parsedRaw.toString()
                    }
                writeText(File(outputDir, "raw_render_result.json"), prettyRaw)
            }
        }

        snapshot.renderResult?.let { renderResult ->
            writeText(
                File(outputDir, "parsed_render_result.json"),
                renderResultToJson(renderResult).toString(2)
            )
            writeText(File(outputDir, "state.json"), mapToJson(renderResult.state).toString(2))
            writeText(File(outputDir, "memo.json"), mapToJson(renderResult.memo).toString(2))
            writeText(File(outputDir, "compose_tree.json"), nodeToJson(renderResult.tree).toString(2))
            writeText(File(outputDir, "compose_tree.txt"), buildReadableTree(renderResult.tree))
            writeText(
                File(outputDir, "compose_layout_tree.txt"),
                buildReadableLayoutTree(
                    node = renderResult.tree,
                    nodePath = "0",
                    layoutByPath = routeLayouts.associateBy { it.nodePath }
                )
            )
        }

        return outputDir
    }

    private fun currentSnapshot(): ToolPkgComposeDslDebugSnapshot? {
        synchronized(lock) {
            val activeSnapshot = activeRouteInstanceId?.let(snapshots::get)
            if (activeSnapshot != null) {
                return activeSnapshot
            }
            return latestRouteInstanceId?.let(snapshots::get)
        }
    }

    private fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }

    private fun collectNodePaths(
        node: ToolPkgComposeDslNode,
        nodePath: String
    ): Set<String> {
        val paths = LinkedHashSet<String>()
        collectNodePathsInto(node = node, nodePath = nodePath, sink = paths)
        return paths
    }

    private fun collectNodePathsInto(
        node: ToolPkgComposeDslNode,
        nodePath: String,
        sink: MutableSet<String>
    ) {
        sink += nodePath
        node.children.forEachIndexed { index, child ->
            collectNodePathsInto(
                node = child,
                nodePath = "$nodePath/$index",
                sink = sink
            )
        }
        node.slots.forEach { (slotName, slotChildren) ->
            slotChildren.forEachIndexed { index, child ->
                collectNodePathsInto(
                    node = child,
                    nodePath = "$nodePath:$slotName/$index",
                    sink = sink
                )
            }
        }
    }

    private fun parseJsonString(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
    }

    private fun renderResultToJson(renderResult: ToolPkgComposeDslRenderResult): JSONObject {
        return JSONObject(
            linkedMapOf(
                "tree" to nodeToJson(renderResult.tree),
                "state" to mapToJson(renderResult.state),
                "memo" to mapToJson(renderResult.memo)
            )
        )
    }

    private fun nodeToJson(node: ToolPkgComposeDslNode): JSONObject {
        val json = JSONObject()
        json.put("type", node.type)
        json.put("props", mapToJson(node.props))
        json.put("children", JSONArray().apply { node.children.forEach { put(nodeToJson(it)) } })
        json.put(
            "slots",
            JSONObject().apply {
                node.slots.forEach { (slotName, slotChildren) ->
                    put(
                        slotName,
                        JSONArray().apply { slotChildren.forEach { put(nodeToJson(it)) } }
                    )
                }
            }
        )
        return json
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        return JSONObject().apply {
            map.forEach { (key, value) ->
                put(key, valueToJson(value))
            }
        }
    }

    private fun listToJson(list: List<Any?>): JSONArray {
        return JSONArray().apply {
            list.forEach { value ->
                put(valueToJson(value))
            }
        }
    }

    private fun valueToJson(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is JSONObject -> value
            is JSONArray -> value
            is ToolPkgComposeDslNode -> nodeToJson(value)
            is Map<*, *> -> {
                JSONObject().apply {
                    value.forEach { (key, itemValue) ->
                        put(key?.toString().orEmpty(), valueToJson(itemValue))
                    }
                }
            }
            is List<*> -> listToJson(value)
            is Array<*> -> listToJson(value.toList())
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    }

    private fun buildReadableTree(node: ToolPkgComposeDslNode): String {
        val lines = mutableListOf<String>()
        appendNode(lines = lines, node = node, indent = "")
        return lines.joinToString(separator = "\n")
    }

    private fun buildReadableLayoutTree(
        node: ToolPkgComposeDslNode,
        nodePath: String,
        layoutByPath: Map<String, ToolPkgComposeDslLayoutSnapshot>
    ): String {
        val lines = mutableListOf<String>()
        appendLayoutNode(
            lines = lines,
            node = node,
            nodePath = nodePath,
            indent = "",
            layoutByPath = layoutByPath
        )
        return lines.joinToString(separator = "\n")
    }

    private fun appendLayoutNode(
        lines: MutableList<String>,
        node: ToolPkgComposeDslNode,
        nodePath: String,
        indent: String,
        layoutByPath: Map<String, ToolPkgComposeDslLayoutSnapshot>
    ) {
        val layout = layoutByPath[nodePath]
        val layoutSummary =
            if (layout == null) {
                " layout=<missing>"
            } else {
                " layout=root(${layout.rootX.format2()}, ${layout.rootY.format2()}) size(${layout.width.format2()} x ${layout.height.format2()})"
            }
        val keySummary = layout?.nodeKey?.takeIf { it.isNotBlank() }?.let { " key=$it" }.orEmpty()
        lines += "$indent- [$nodePath] ${node.type}$keySummary$layoutSummary"
        node.children.forEachIndexed { index, child ->
            appendLayoutNode(
                lines = lines,
                node = child,
                nodePath = "$nodePath/$index",
                indent = "$indent  ",
                layoutByPath = layoutByPath
            )
        }
        node.slots.forEach { (slotName, slotChildren) ->
            lines += "$indent  @$slotName"
            slotChildren.forEachIndexed { index, child ->
                appendLayoutNode(
                    lines = lines,
                    node = child,
                    nodePath = "$nodePath:$slotName/$index",
                    indent = "$indent    ",
                    layoutByPath = layoutByPath
                )
            }
        }
    }

    private fun buildLayoutListing(layouts: List<ToolPkgComposeDslLayoutSnapshot>): String {
        if (layouts.isEmpty()) {
            return "<no layout nodes captured>"
        }
        return layouts.joinToString(separator = "\n") { layout ->
            buildString {
                append("[")
                append(layout.nodePath)
                append("] ")
                append(layout.nodeType)
                layout.nodeKey?.takeIf { it.isNotBlank() }?.let {
                    append(" key=")
                    append(it)
                }
                append(" root=(")
                append(layout.rootX.format2())
                append(", ")
                append(layout.rootY.format2())
                append(")")
                append(" size=(")
                append(layout.width.format2())
                append(" x ")
                append(layout.height.format2())
                append(")")
                append(" window=(")
                append(layout.windowX.format2())
                append(", ")
                append(layout.windowY.format2())
                append(")")
            }
        }
    }

    private fun appendNode(
        lines: MutableList<String>,
        node: ToolPkgComposeDslNode,
        indent: String
    ) {
        val propsSummary =
            if (node.props.isEmpty()) {
                ""
            } else {
                " props=${node.props.keys.joinToString(prefix = "[", postfix = "]")}"
            }
        lines += "$indent- ${node.type}$propsSummary"
        node.children.forEach { child ->
            appendNode(lines = lines, node = child, indent = "$indent  ")
        }
        node.slots.forEach { (slotName, slotChildren) ->
            lines += "$indent  @$slotName"
            slotChildren.forEach { child ->
                appendNode(lines = lines, node = child, indent = "$indent    ")
            }
        }
    }

    private fun layoutToJson(layout: ToolPkgComposeDslLayoutSnapshot): JSONObject {
        return JSONObject(
            linkedMapOf(
                "routeInstanceId" to layout.routeInstanceId,
                "nodePath" to layout.nodePath,
                "nodeType" to layout.nodeType,
                "nodeKey" to layout.nodeKey,
                "rootX" to layout.rootX,
                "rootY" to layout.rootY,
                "width" to layout.width,
                "height" to layout.height,
                "windowX" to layout.windowX,
                "windowY" to layout.windowY,
                "updatedAtMillis" to layout.updatedAtMillis
            )
        )
    }

    private fun Float.format2(): String = String.format(Locale.ROOT, "%.2f", this)
}
