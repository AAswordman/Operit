package com.ai.assistance.operit.util.files

import android.os.Process

object AndroidUserPathUtils {
    private const val PER_USER_RANGE = 100000

    fun currentUserId(): Int = Process.myUid() / PER_USER_RANGE

    fun currentUserDataRootPath(): String = "/data/user/${currentUserId()}"

    fun currentUserPackageDataPath(packageName: String): String =
        "${currentUserDataRootPath()}/$packageName"

    fun currentUserPackageFilesPath(packageName: String): String =
        "${currentUserPackageDataPath(packageName)}/files"

    fun isCurrentUserPackageDataPath(path: String, packageName: String): Boolean {
        val normalizedPath = path.trim()
        return normalizedPath.startsWith(currentUserPackageDataPath(packageName))
    }
}
