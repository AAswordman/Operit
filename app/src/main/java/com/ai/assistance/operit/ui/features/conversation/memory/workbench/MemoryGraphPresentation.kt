package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Node

internal fun memoryMatchesFolder(node: Node, folder: String, uncategorized: String): Boolean {
    if (folder.isEmpty()) return true
    val path = node.metadata["folder"].orEmpty()
    return if (folder == uncategorized) path.isEmpty() else path == folder || path.startsWith("$folder/")
}

/** 所有筛选只作用于视图，完整图谱仍用于详情、检索与创建关联。 */
internal fun memoryGraphScope(graph: Graph, folder: String, uncategorized: String, showCross: Boolean, relatedId: String? = null, searchIds: Set<String>? = null): Graph {
    val ids = when {
        relatedId != null -> buildSet {
            add(relatedId)
            graph.edges.filter { it.sourceId == relatedId || it.targetId == relatedId }.forEach { add(it.sourceId); add(it.targetId) }
        }
        else -> {
            val local = graph.nodes.filter { memoryMatchesFolder(it, folder, uncategorized) && (searchIds == null || it.id in searchIds) }.map { it.id }.toSet()
            if (showCross && (folder.isNotEmpty() || searchIds != null)) buildSet {
                addAll(local)
                graph.edges.filter { it.sourceId in local || it.targetId in local }.forEach { add(it.sourceId); add(it.targetId) }
            } else local
        }
    }
    return Graph(graph.nodes.filter { it.id in ids }, graph.edges.filter { it.sourceId in ids && it.targetId in ids })
}

/** 文件夹之间的线仅汇总真实跨组关联，不创造新的语义分组。 */
internal fun memoryFolderGraph(graph: Graph, uncategorized: String): Graph {
    val byId = graph.nodes.associateBy { it.id }
    val folders = graph.nodes.groupBy { it.metadata["folder"].orEmpty() }.toSortedMap()
    val nodes = folders.map { (path, members) ->
        Node(id = "folder:$path", label = path.ifEmpty { uncategorized }, metadata = mapOf("folder" to path.ifEmpty { uncategorized }, "count" to members.size.toString(), "group" to "true"))
    }
    val groups = linkedMapOf<Pair<String, String>, Int>()
    graph.edges.forEach { edge ->
        val from = byId[edge.sourceId] ?: return@forEach
        val to = byId[edge.targetId] ?: return@forEach
        val a = from.metadata["folder"].orEmpty()
        val b = to.metadata["folder"].orEmpty()
        if (a != b) {
            val pair = if (a < b) a to b else b to a
            groups[pair] = (groups[pair] ?: 0) + 1
        }
    }
    val edges = groups.entries.mapIndexed { index, (pair, count) ->
        Edge(id = -(index + 1).toLong(), sourceId = "folder:${pair.first}", targetId = "folder:${pair.second}", weight = count.toFloat(), metadata = mapOf("count" to count.toString(), "group" to "true"), isCrossFolderLink = true)
    }
    return Graph(nodes, edges)
}