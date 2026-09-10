package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.util.OperitPaths
import java.io.File

object OperitBackupDirs {

    fun operitRootDir(): File {
        return OperitPaths.operitRootDir()
    }

    fun backupRootDir(): File {
        return ensureDir(File(operitRootDir(), "backup"))
    }

    fun rawSnapshotDir(): File {
        return ensureDir(File(backupRootDir(), "raw_snapshot"))
    }

    fun roomDbDir(): File {
        return ensureDir(File(backupRootDir(), "room_db"))
    }

    fun preferencesDir(): File {
        return ensureDir(File(backupRootDir(), "preferences"))
    }

    fun chatDir(): File {
        return ensureDir(File(backupRootDir(), "chat"))
    }

    fun memoryDir(): File {
        return ensureDir(File(backupRootDir(), "memory"))
    }

    fun modelConfigDir(): File {
        return ensureDir(File(backupRootDir(), "model_config"))
    }

    fun characterCardsDir(): File {
        return ensureDir(File(backupRootDir(), "character_cards"))
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
