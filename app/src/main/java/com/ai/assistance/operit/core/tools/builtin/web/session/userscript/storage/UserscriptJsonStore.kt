package com.ai.assistance.operit.core.tools.builtin.web.session.userscript.storage

import android.content.Context
import com.ai.assistance.operit.util.files.OperitPaths
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class UserscriptJsonStore private constructor(context: Context) {
    @Serializable
    private data class StoreState(
        val nextScriptId: Long = 1L,
        val nextResourceId: Long = 1L,
        val scripts: List<UserscriptEntity> = emptyList(),
        val resources: List<UserscriptResourceEntity> = emptyList()
    )

    @Serializable
    private data class LogState(
        val nextLogId: Long = 1L,
        val logs: List<UserscriptLogEntity> = emptyList()
    )

    @Serializable
    private data class ValueState(
        val values: List<UserscriptValueEntity> = emptyList()
    )

    companion object {
        @Volatile
        private var instance: UserscriptJsonStore? = null

        fun getInstance(context: Context): UserscriptJsonStore {
            return instance ?: synchronized(this) {
                instance ?: UserscriptJsonStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val rootDir = OperitPaths.webSessionUserscriptsDir()
    private val stateDir = File(rootDir, "state").apply { mkdirs() }
    private val valuesDir = File(stateDir, "values").apply { mkdirs() }
    private val storeFile = File(stateDir, "registry.json")
    private val logFile = File(stateDir, "logs.json")
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(readStoreState())
    private val logFlow = MutableStateFlow(readLogState())

    fun observeUserscripts(): Flow<List<UserscriptEntity>> =
        stateFlow.map { state ->
            state.scripts.sortedWith(compareBy<UserscriptEntity> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
        }

    fun observeRecentLogs(limit: Int): Flow<List<UserscriptLogEntity>> =
        logFlow.map { state ->
            state.logs.take(limit.coerceAtLeast(0))
        }

    suspend fun getUserscriptById(scriptId: Long): UserscriptEntity? =
        mutex.withLock { stateFlow.value.scripts.firstOrNull { it.id == scriptId } }

    suspend fun getUserscriptByScope(name: String, namespace: String?): UserscriptEntity? =
        mutex.withLock {
            stateFlow.value.scripts.firstOrNull { script ->
                script.name == name && ((namespace == null && script.namespace == null) || script.namespace == namespace)
            }
        }

    suspend fun getAllUserscripts(): List<UserscriptEntity> =
        mutex.withLock { stateFlow.value.scripts }

    suspend fun getResourcesForScript(scriptId: Long): List<UserscriptResourceEntity> =
        mutex.withLock { stateFlow.value.resources.filter { it.userscriptId == scriptId } }

    suspend fun getResourcesForScripts(scriptIds: List<Long>): List<UserscriptResourceEntity> =
        mutex.withLock { stateFlow.value.resources.filter { it.userscriptId in scriptIds } }

    suspend fun getValuesForScript(scriptId: Long): List<UserscriptValueEntity> =
        mutex.withLock { readValueState(scriptId).values }

    suspend fun getValue(
        scriptId: Long,
        key: String
    ): UserscriptValueEntity? =
        mutex.withLock { readValueState(scriptId).values.firstOrNull { it.storageKey == key } }

    suspend fun insertValue(value: UserscriptValueEntity) {
        mutex.withLock {
            val state = readValueState(value.userscriptId)
            val nextValues = state.values.filterNot { it.storageKey == value.storageKey } + value
            writeValueState(value.userscriptId, state.copy(values = nextValues))
        }
    }

    suspend fun deleteValue(
        scriptId: Long,
        key: String
    ) {
        mutex.withLock {
            val state = readValueState(scriptId)
            val nextValues = state.values.filterNot { it.storageKey == key }
            if (nextValues.isEmpty()) {
                valuesFile(scriptId).delete()
            } else {
                writeValueState(scriptId, state.copy(values = nextValues))
            }
        }
    }

    suspend fun insertUserscript(entity: UserscriptEntity): Long =
        mutex.withLock {
            val state = stateFlow.value
            val nextId = state.nextScriptId
            val inserted = entity.copy(id = nextId)
            writeStoreState(
                state.copy(
                    nextScriptId = nextId + 1L,
                    scripts = state.scripts + inserted
                )
            )
            nextId
        }

    suspend fun updateUserscript(entity: UserscriptEntity) {
        mutex.withLock {
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    scripts = state.scripts.map { if (it.id == entity.id) entity else it }
                )
            )
        }
    }

    suspend fun insertResources(resources: List<UserscriptResourceEntity>) {
        if (resources.isEmpty()) {
            return
        }
        mutex.withLock {
            var nextResourceId = stateFlow.value.nextResourceId
            val inserted =
                resources.map { resource ->
                    val id = if (resource.id > 0L) resource.id else nextResourceId++
                    resource.copy(id = id)
                }
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    nextResourceId = nextResourceId,
                    resources = state.resources + inserted
                )
            )
        }
    }

    suspend fun deleteResourcesForScript(scriptId: Long) {
        mutex.withLock {
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    resources = state.resources.filterNot { it.userscriptId == scriptId }
                )
            )
        }
    }

    suspend fun insertLog(log: UserscriptLogEntity): Long =
        mutex.withLock {
            val state = logFlow.value
            val nextId = state.nextLogId
            val inserted = log.copy(id = nextId)
            writeLogState(
                state.copy(
                    nextLogId = nextId + 1L,
                    logs = listOf(inserted) + state.logs
                )
            )
            nextId
        }

    suspend fun deleteLogsForScript(scriptId: Long) {
        mutex.withLock {
            val state = logFlow.value
            writeLogState(state.copy(logs = state.logs.filterNot { it.userscriptId == scriptId }))
        }
    }

    suspend fun trimLogs(keepCount: Int) {
        mutex.withLock {
            val state = logFlow.value
            writeLogState(state.copy(logs = state.logs.take(keepCount.coerceAtLeast(0))))
        }
    }

    suspend fun deleteUserscriptById(scriptId: Long) {
        mutex.withLock {
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    scripts = state.scripts.filterNot { it.id == scriptId },
                    resources = state.resources.filterNot { it.userscriptId == scriptId }
                )
            )
            valuesFile(scriptId).delete()
            val logs = logFlow.value
            writeLogState(logs.copy(logs = logs.logs.filterNot { it.userscriptId == scriptId }))
        }
    }

    suspend fun replaceResources(
        scriptId: Long,
        resources: List<UserscriptResourceEntity>
    ) {
        mutex.withLock {
            var nextResourceId = stateFlow.value.nextResourceId
            val inserted =
                resources.map { resource ->
                    val id = if (resource.id > 0L) resource.id else nextResourceId++
                    resource.copy(id = id)
                }
            val state = stateFlow.value
            writeStoreState(
                state.copy(
                    nextResourceId = nextResourceId,
                    resources = state.resources.filterNot { it.userscriptId == scriptId } + inserted
                )
            )
        }
    }

    private fun valuesFile(scriptId: Long): File =
        File(valuesDir, "$scriptId.json")

    private fun readStoreState(): StoreState {
        if (!storeFile.exists()) {
            val empty = StoreState()
            writeRaw(storeFile, json.encodeToString(empty))
            return empty
        }
        return json.decodeFromString(storeFile.readText())
    }

    private fun writeStoreState(state: StoreState) {
        writeRaw(storeFile, json.encodeToString(state))
        stateFlow.value = state
    }

    private fun readLogState(): LogState {
        if (!logFile.exists()) {
            val empty = LogState()
            writeRaw(logFile, json.encodeToString(empty))
            return empty
        }
        return json.decodeFromString(logFile.readText())
    }

    private fun writeLogState(state: LogState) {
        writeRaw(logFile, json.encodeToString(state))
        logFlow.value = state
    }

    private fun readValueState(scriptId: Long): ValueState {
        val file = valuesFile(scriptId)
        if (!file.exists()) {
            return ValueState()
        }
        return json.decodeFromString(file.readText())
    }

    private fun writeValueState(
        scriptId: Long,
        state: ValueState
    ) {
        writeRaw(valuesFile(scriptId), json.encodeToString(state))
    }

    private fun writeRaw(
        file: File,
        content: String
    ) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
