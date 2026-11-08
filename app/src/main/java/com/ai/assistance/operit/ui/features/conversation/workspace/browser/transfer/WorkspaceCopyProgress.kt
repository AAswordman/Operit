package com.ai.assistance.operit.ui.features.conversation.workspace.browser.transfer

import com.ai.assistance.operit.core.files.transfer.FileCopyProgress

/** 一项可包含整个目录；批量粘贴同时显示项目序号，字节进度对应当前项目。 */
internal data class WorkspaceCopyProgress(
    val source: String,
    val destination: String,
    val sourceEnvironment: String,
    val destinationEnvironment: String,
    val itemIndex: Int,
    val itemCount: Int,
    val moving: Boolean,
    val detail: FileCopyProgress? = null,
    val finishing: Boolean = false,
)