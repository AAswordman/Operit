package com.ai.assistance.operit.ui.features.chat.webview.workspace

import java.io.File
import kotlinx.serialization.Serializable

/** 打开文件时携带来源，保存和预览不依赖当前浏览目录。 */
@Serializable
data class OpenFileInfo(
    val path: String,
    val content: String,
    val lastModified: Long,
    val name: String = File(path).name,
    val mimeType: String = "",
    val environment: String = "android",
    val initialLine: Int? = null
)

internal val OpenFileInfo.key: String
    get() = "$environment:$path"
