package com.ai.assistance.operit.data.themesharing

/** 制作资源保留原路径，普通媒体只在独立目录中分配未占用文件名。 */
internal object ThemeResourcePaths {
    fun allocate(extension: String, files: Set<String>, folders: Set<String>): String {
        require(Regex("[A-Za-z0-9]{1,10}").matches(extension)) { "Invalid media extension" }
        for (index in 1..(files.size + folders.size + 1)) {
            val path = "assets/role-media/$index.$extension"
            val fileConflict = files.any { it == path || it.startsWith("$path/") || path.startsWith("$it/") }
            // 允许资源与工程共用父文件夹，但文件不能占用已声明的目录位置。
            val folderConflict = folders.any { it == path || it.startsWith("$path/") }
            if (!fileConflict && !folderConflict) return path
        }
        error("Cannot allocate a theme media path")
    }
}