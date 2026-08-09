package com.ai.assistance.operit.data.persistence

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.ai.assistance.operit.util.AppLogger
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Semaphore
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

class StorageBusyException(message: String) : IOException(message)

object StorageProcessLock {
    private const val TAG = "StorageProcessLock"
    private val stateLock = Any()
    private val operationPermit = Semaphore(1, true)
    private var mainLease: Lease? = null

    fun isMainProcess(context: Context): Boolean = currentProcessName() == context.packageName

    fun isRepairProcess(context: Context): Boolean =
        currentProcessName() == "${context.packageName}:repair"

    fun acquireMainProcessLease(context: Context): Boolean {
        if (!isMainProcess(context)) return false
        synchronized(stateLock) {
            if (mainLease != null) return true
            val acquired = tryAcquire(context, "main:${Process.myPid()}") ?: return false
            mainLease = acquired
            AppLogger.i(TAG, "Main process acquired storage lease")
            return true
        }
    }

    fun mainProcessOwnsStorage(): Boolean = synchronized(stateLock) { mainLease != null }

    fun releaseMainProcessLease() {
        val lease = synchronized(stateLock) {
            val current = mainLease
            mainLease = null
            current
        }
        try {
            lease?.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to release main storage lease", e)
        }
        if (lease != null) AppLogger.i(TAG, "Main process released storage lease")
    }

    fun <T> withExclusiveAccess(context: Context, owner: String, block: () -> T): T {
        operationPermit.acquireUninterruptibly()
        return try {
            val usesMainLease = synchronized(stateLock) { mainLease != null }
            if (usesMainLease) {
                block()
            } else {
                val lease =
                    tryAcquire(context.applicationContext, "$owner:${Process.myPid()}")
                        ?: throw StorageBusyException(
                            "Operit storage is active in another process. Close the main app before repair writes."
                        )
                lease.use { block() }
            }
        } finally {
            operationPermit.release()
        }
    }

    fun acquireDescriptorLease(context: Context, owner: String): Closeable {
        return acquireIndependentLease(context, owner)
    }

    fun acquireOperationLease(context: Context, owner: String): Closeable {
        // Raw snapshot work suspends and can resume on another IO thread. A Semaphore can be
        // released by that thread, while a ReentrantLock would strand the operation boundary.
        operationPermit.acquireUninterruptibly()
        return try {
            OperationLease(acquireIndependentLease(context, owner))
        } catch (e: Throwable) {
            operationPermit.release()
            throw e
        }
    }

    private fun acquireIndependentLease(context: Context, owner: String): Closeable {
        val usesMainLease = synchronized(stateLock) { mainLease != null }
        if (usesMainLease) return Closeable {}
        return tryAcquire(context.applicationContext, "$owner:${Process.myPid()}")
            ?: throw StorageBusyException(
                "Operit storage is active in another process. Close the main app before repair writes."
            )
    }

    private fun tryAcquire(context: Context, owner: String): Lease? {
        // Operit Data exposes the credential-protected application directory. Keeping the lease
        // in device-protected no-backup storage prevents a provider mutation from unlinking the
        // locked inode and creating a second lock file while the first lease is still active.
        val lockContext = context.createDeviceProtectedStorageContext()
        val file =
            File(File(lockContext.noBackupFilesDir, "storage-recovery"), "storage.lock")
        file.parentFile?.mkdirs()
        val randomAccessFile =
            try {
                RandomAccessFile(file, "rw")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to open storage lease file for $owner", e)
                throw IOException("Failed to open the Operit storage lease file", e)
            }
        val channel = randomAccessFile.channel
        return try {
            val fileLock = channel.tryLock() ?: run {
                closeFailedAcquire(randomAccessFile, channel)
                return null
            }
            randomAccessFile.setLength(0L)
            randomAccessFile.write(owner.toByteArray(Charsets.UTF_8))
            Lease(randomAccessFile, channel, fileLock)
        } catch (_: OverlappingFileLockException) {
            closeFailedAcquire(randomAccessFile, channel)
            null
        } catch (e: Exception) {
            closeFailedAcquire(randomAccessFile, channel)
            AppLogger.e(TAG, "Failed to acquire storage lease for $owner", e)
            throw IOException("Failed to acquire the Operit storage lease", e)
        }
    }

    private fun closeFailedAcquire(
        randomAccessFile: RandomAccessFile,
        channel: FileChannel
    ) {
        try {
            channel.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to close storage lease channel", e)
        }
        try {
            randomAccessFile.close()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to close storage lease file", e)
        }
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return try {
            File("/proc/self/cmdline")
                .readBytes()
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read current process name", e)
            null
        }
    }

    private class Lease(
        private val randomAccessFile: RandomAccessFile,
        private val channel: FileChannel,
        private val fileLock: FileLock
    ) : Closeable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            try {
                if (fileLock.isValid) fileLock.release()
            } finally {
                try {
                    channel.close()
                } finally {
                    randomAccessFile.close()
                }
            }
        }
    }

    private class OperationLease(
        private val processLease: Closeable
    ) : Closeable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            try {
                processLease.close()
            } finally {
                StorageProcessLock.operationPermit.release()
            }
        }
    }
}

/**
 * Prevents ordinary main-process callers from opening a storage owner during startup recovery or
 * raw file replacement. The authorized coroutine carries access across dispatcher changes.
 */
internal object StorageReplacementGate {
    private val boundary = Any()
    private val active = AtomicBoolean(false)
    private val replacementAccess = ThreadLocal<Boolean>()

    fun acquire(): ReplacementLease =
        synchronized(boundary) {
            check(active.compareAndSet(false, true)) {
                "An exclusive storage recovery is already active in this process"
            }
            ReplacementLease()
        }

    fun <T> withStorageAccess(block: () -> T): T =
        synchronized(boundary) {
            checkAccessLocked()
            block()
        }

    private fun checkAccessLocked() {
        check(!active.get() || replacementAccess.get() == true) {
            "Storage is temporarily unavailable during exclusive recovery"
        }
    }

    class ReplacementLease internal constructor() : Closeable {
        private val closed = AtomicBoolean(false)

        suspend fun <T> withAccess(block: suspend () -> T): T {
            check(!closed.get()) { "Storage replacement lease is closed" }
            return withContext(
                StorageReplacementGate.replacementAccess.asContextElement(true)
            ) {
                block()
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(StorageReplacementGate.boundary) {
                check(StorageReplacementGate.active.compareAndSet(true, false)) {
                    "Storage replacement gate was not active"
                }
            }
        }
    }
}
