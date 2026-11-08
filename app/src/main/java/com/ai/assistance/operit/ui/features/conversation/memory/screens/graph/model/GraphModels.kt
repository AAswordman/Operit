package com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model

import androidx.compose.ui.graphics.Color

data class Graph(
    val nodes: List<Node>,
    val edges: List<Edge>
)

data class Node(
    val id: String,
    val label: String,
    val color: Color = Color.LightGray,
    val metadata: Map<String, String> = emptyMap()
)

data class Edge(
    val id: Long,
    val sourceId: String,
    val targetId: String,
    val label: String? = null,
    val weight: Float = 1.0f,
    val metadata: Map<String, String> = emptyMap(),
    val isCrossFolderLink: Boolean = false // 标记是否为跨文件夹连接
) 