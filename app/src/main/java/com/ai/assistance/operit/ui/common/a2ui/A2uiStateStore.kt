package com.ai.assistance.operit.ui.common.a2ui

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*

/**
 * Private, atomic sidecars. A conflated writer survives a card scrolling out of composition.
 * Never store protocol actions for replay, credentials, or a copy of the whole conversation.
 * 同步发布、异步保存和恢复共用 AndroidX 原子文件协议。
 */
internal object A2uiStateStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val pending = linkedMapOf<File, String>()
    private val ioMutex = Mutex()
    private val deleted = mutableSetOf<File>()
    private val deletedFiles = mutableSetOf<File>()
    private val tracked = mutableSetOf<File>()
    val failures = MutableStateFlow<Set<File>>(emptySet())
    private var started = false

    // 功能尚未正式发布，普通卡片与 live 快照统一使用 files/a2ui。
    fun file(context: Context, owner: A2uiOwner, source: String, surface: String): File {
        val chat = A2uiProtocol.fingerprint(owner.chatId)
        val key = A2uiProtocol.fingerprint("$surface:$source")
        return File(context.filesDir, "a2ui/$chat/${owner.timestamp}_${owner.variant}_$key.json").also {
            synchronized(pending) { tracked.add(it); deletedFiles.remove(it) }
        }
    }

    suspend fun read(file: File): JsonObject? = withContext(Dispatchers.IO) {
        val queued = synchronized(pending) { pending[file] }
        runCatching {
            val text = queued ?: ioMutex.withLock {
                AtomicFile(file).openRead().use {
                    // State limit is in UTF-16 characters; UTF-8 may take up to 4 bytes each.
                    require(it.channel.size() <= A2uiProtocol.MAX_STATE * 12L)
                    it.bufferedReader().readText()
                }
            }
            Json.parseToJsonElement(text).jsonObject
        }.getOrNull()
    }

    fun save(file: File, record: JsonObject) {
        val text = record.toString()
        require(text.length <= A2uiProtocol.MAX_STATE * 3)
        synchronized(pending) {
            if (file.parentFile in deleted || file in deletedFiles) return
            pending[file] = text
            if (!started) {
                started = true
                scope.launch {
                    for (ignored in signal) {
                        while (true) {
                            val next = synchronized(pending) {
                                pending.entries.firstOrNull()?.let { (key, value) -> key to value }
                            } ?: break
                            val atomic = AtomicFile(next.first)
                            var stream: java.io.FileOutputStream? = null
                            try {
                                ioMutex.withLock {
                                if (synchronized(pending) { next.first.parentFile in deleted || next.first in deletedFiles }) return@withLock
                                next.first.parentFile?.mkdirs()
                                stream = atomic.startWrite()
                                stream.write(next.second.toByteArray(Charsets.UTF_8))
                                atomic.finishWrite(stream)
                                failures.update { it - next.first }
                                }
                            } catch (e: Exception) {
                                atomic.failWrite(stream)
                                failures.update { (it + next.first).toList().takeLast(64).toSet() }
                                com.ai.assistance.operit.util.logging.AppLogger.w("A2ui", "Unable to persist card state", e)
                            } finally {
                                synchronized(pending) {
                                    if (pending[next.first] == next.second) pending.remove(next.first)
                                }
                            }
                        }
                    }
                }
            }
        }
        signal.trySend(Unit)
    }

    /** Live tool updates acknowledge only after an atomic write, serialized with chat deletion. */
    fun writeNow(file: File, record: JsonObject) = runBlocking(Dispatchers.IO) {
        val text = record.toString()
        require(text.length <= A2uiProtocol.MAX_STATE * 3)
        ioMutex.withLock {
            check(synchronized(pending) { file.parentFile !in deleted && file !in deletedFiles }) { "Chat was deleted" }
            file.parentFile?.mkdirs()
            val atomic = AtomicFile(file)
            var stream: java.io.FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(text.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
        }
    }

    private fun directory(context: Context, chatId: String) =
        File(context.filesDir, "a2ui/${A2uiProtocol.fingerprint(chatId)}")

    /**
     * Branches copy messages with their original timestamps, so the same state file names resolve
     * in the copy. Queued writes are included so a branch made right after a tap keeps it.
     */
    suspend fun copyChat(context: Context, sourceChatId: String, targetChatId: String, upToTimestamp: Long? = null) = withContext(Dispatchers.IO) {
        val source = directory(context, sourceChatId)
        val target = directory(context, targetChatId)
        fun copied(name: String): Boolean = upToTimestamp == null ||
            name.substringBefore('_').toLongOrNull()?.let { it <= upToTimestamp } != false
        val queued = synchronized(pending) {
            if (source in deleted) return@withContext
            pending.filterKeys { it.parentFile == source && copied(it.name) }.map { (file, text) -> File(target, file.name) to text }
        }
        ioMutex.withLock {
            // AtomicFile reads recover a write interrupted on the source side (name.json.bak).
            val names = source.list().orEmpty().mapNotNullTo(linkedSetOf()) { name ->
                when {
                    name.endsWith(".json") -> name
                    name.endsWith(".json.bak") -> name.removeSuffix(".bak")
                    else -> null
                }
            }
            names.filter(::copied).forEach { name ->
                // One unreadable card must not drop the rest of the branch's state.
                runCatching { writeAtomic(File(target, name), AtomicFile(File(source, name)).readFully()) }
                    .onFailure { com.ai.assistance.operit.util.logging.AppLogger.w("A2ui", "Unable to copy card state $name", it) }
            }
            queued.forEach { (file, text) ->
                runCatching { writeAtomic(file, text.toByteArray(Charsets.UTF_8)) }
                    .onFailure { com.ai.assistance.operit.util.logging.AppLogger.w("A2ui", "Unable to copy queued card state ${file.name}", it) }
            }
        }
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    /** 编辑、删除和截断按消息清理；正在离屏的旧组件不能再写回被清理的文件。 */
    suspend fun deleteMessages(context: Context, chatId: String, timestamp: Long, from: Boolean = false) = withContext(Dispatchers.IO) {
        val dir = directory(context, chatId)
        fun matches(file: File): Boolean {
            val value = file.name.substringBefore('_').toLongOrNull() ?: return false
            return file.parentFile == dir && if (from) value >= timestamp else value == timestamp
        }
        ioMutex.withLock {
            val targets = synchronized(pending) {
                (tracked + pending.keys + dir.listFiles().orEmpty()).filter(::matches).toSet().also { files ->
                    deletedFiles.addAll(files)
                    pending.keys.removeAll(files)
                    tracked.removeAll(files)
                }
            }
            targets.forEach { AtomicFile(it).delete() }
            failures.update { it - targets }
        }
    }

    suspend fun clearChat(context: Context, chatId: String) = withContext(Dispatchers.IO) {
        deleteMessages(context, chatId, Long.MIN_VALUE, from = true)
        // Live 快照不含消息序号，清空整段聊天时一起清理。
        val dir = directory(context, chatId)
        ioMutex.withLock {
            dir.listFiles().orEmpty().filter { it.name.startsWith("live_") }.forEach { AtomicFile(it).delete() }
        }
    }

    /** 批量删除仅扫描卡片目录和聊天元数据，不读取消息全文。 */
    suspend fun deleteMissingChats(context: Context, chatIds: Set<String>) = withContext(Dispatchers.IO) {
        val valid = chatIds.mapTo(mutableSetOf(), A2uiProtocol::fingerprint)
        val root = File(context.filesDir, "a2ui")
        root.listFiles().orEmpty().filter { it.isDirectory && it.name !in valid }.forEach { dir ->
            synchronized(pending) {
                deleted.add(dir)
                pending.keys.removeAll { it.parentFile == dir }
                tracked.removeAll { it.parentFile == dir }
            }
            ioMutex.withLock { check(dir.deleteRecursively()) { "Unable to delete card state" } }
        }
    }

    suspend fun deleteChat(context: Context, chatId: String) = withContext(Dispatchers.IO) {
        val directory = directory(context, chatId)
        synchronized(pending) {
            deleted.add(directory)
            pending.keys.removeAll { it.parentFile == directory }
            tracked.removeAll { it.parentFile == directory }
        }
        ioMutex.withLock {
            if (directory.exists()) check(directory.deleteRecursively()) { "Unable to delete card state" }
        }
        failures.update { files -> files.filterNot { it.parentFile == directory }.toSet() }
    }
}
