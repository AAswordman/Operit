package com.ai.assistance.operit.data.persistence

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File

object StorageRecoveryFaultFixtures {
    private val corruptPreferencesPayload = byteArrayOf(0x0A, 0x7F)
    private val corruptDatabasePayload = "operit-storage-corruption-fixture".toByteArray()

    fun corruptPreferenceStore(context: Context, storeName: String): File {
        require(storeName in PreferenceStoreCatalog.recoverable)
        return context.preferencesDataStoreFile(storeName).apply {
            parentFile?.mkdirs()
            writeBytes(corruptPreferencesPayload)
        }
    }

    fun replacePreferenceStoreWithDirectory(context: Context, storeName: String): File {
        require(storeName in PreferenceStoreCatalog.recoverable)
        val source = context.preferencesDataStoreFile(storeName)
        if (source.exists()) check(source.deleteRecursively())
        check(source.mkdirs())
        File(source, "unexpected-remnant").writeText("invalid DataStore path")
        return source
    }

    fun corruptRoomDatabase(context: Context): File {
        return context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME).apply {
            parentFile?.mkdirs()
            writeBytes(corruptDatabasePayload)
        }
    }

    fun replaceRoomDatabaseWithDirectory(context: Context): File {
        val source = context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val candidate = File(source.absolutePath + suffix)
            if (candidate.exists()) check(candidate.deleteRecursively())
        }
        check(source.mkdirs())
        File(source, "unexpected-remnant").writeText("invalid Room database path")
        val walPath = File(source.absolutePath + "-wal")
        check(walPath.mkdirs())
        File(walPath, "unexpected-sidecar-remnant").writeText("invalid Room sidecar path")
        return source
    }

    fun replaceRoomWalWithDirectory(context: Context): File {
        val databaseFile = context.getDatabasePath(RoomRecoveryStorage.DATABASE_NAME)
        check(databaseFile.isFile)
        val walPath = File(databaseFile.absolutePath + "-wal")
        if (walPath.exists()) check(walPath.deleteRecursively())
        check(walPath.mkdirs())
        File(walPath, "unexpected-sidecar-remnant").writeText("invalid Room sidecar path")
        return walPath
    }

    fun corruptObjectBoxProfile(context: Context, profileId: String): File {
        val directoryName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
        return File(File(context.filesDir, directoryName), "data.mdb").apply {
            parentFile?.mkdirs()
            writeBytes(corruptDatabasePayload)
        }
    }

    fun replaceObjectBoxDataWithDirectory(context: Context, profileId: String): File {
        val directoryName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
        val source = File(File(context.filesDir, directoryName), "data.mdb")
        if (source.exists()) check(source.deleteRecursively())
        check(source.mkdirs())
        File(source, "unexpected-remnant").writeText("invalid ObjectBox data path")
        return source
    }

    fun replaceObjectBoxLockWithDirectory(context: Context, profileId: String): File {
        val directoryName = if (profileId == "default") "objectbox" else "objectbox_$profileId"
        val source = File(File(context.filesDir, directoryName), "lock.mdb")
        if (source.exists()) check(source.deleteRecursively())
        check(source.mkdirs())
        File(source, "unexpected-lock-remnant").writeText("invalid ObjectBox lock path")
        return source
    }
}
