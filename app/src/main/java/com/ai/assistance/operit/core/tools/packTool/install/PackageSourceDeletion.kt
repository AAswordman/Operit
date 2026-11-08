package com.ai.assistance.operit.core.tools.packTool.install

import java.io.File

/** 只删除当前应用安装目录中的已解析源文件，返回成功前核实文件确实消失。 */
internal fun deleteInstalledPackageSources(
    directory: File,
    sourcePaths: List<String>,
    deleteFile: (File) -> Boolean = File::delete,
): Boolean {
    if (sourcePaths.isEmpty()) return false
    val root = directory.canonicalFile
    val sources = sourcePaths.map { File(it).canonicalFile }.distinct()
    if (sources.any { it.parentFile != root || !it.isFile }) return false
    return sources.map { source -> deleteFile(source) && !source.exists() }.all { it }
}
