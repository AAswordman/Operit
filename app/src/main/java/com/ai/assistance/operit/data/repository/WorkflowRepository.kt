package com.ai.assistance.operit.data.repository

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.AtomicFile
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.core.workflow.WorkflowExecutionRetryableException
import com.ai.assistance.operit.core.workflow.WorkflowExecutor
import com.ai.assistance.operit.core.workflow.WorkflowScheduler
import com.ai.assistance.operit.data.model.ExecutionStatus
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.WorkflowExecutionFailureStage
import com.ai.assistance.operit.data.model.WorkflowExecutionLogEntry
import com.ai.assistance.operit.data.model.WorkflowExecutionRecord
import com.ai.assistance.operit.data.model.WorkflowLogLevel
import com.ai.assistance.operit.data.model.TriggerNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 工作流仓库
 * 负责工作流的持久化存储和管理
 */
class WorkflowRepository(private val context: Context) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }
    
    // Lazy initialization to avoid WorkManager initialization issues during app startup
    private val scheduler by lazy { WorkflowScheduler(context) }
    
    companion object {
        private const val TAG = "WorkflowRepository"
        private const val WORKFLOW_DIR = "Operit/workflow"
        private const val EXECUTION_LOG_SUB_DIR = "_execution_logs"
        private const val MAX_EXECUTION_LOG_FILES_PER_WORKFLOW = 30

        private const val SPEECH_TRIGGER_CACHE_TTL_MS = 2000L
        private val workflowStoreMutex = Mutex()
        private val speechTriggerLastFireAtMs = ConcurrentHashMap<String, Long>()

        @Volatile
        private var speechTriggerCachedWorkflows: List<Workflow>? = null

        @Volatile
        private var speechTriggerCachedAtMs: Long = 0L

        val workflowUpdateEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val workflowStorageWarnings = MutableSharedFlow<String>(extraBufferCapacity = 8)
        private val runningWorkflowLock = Any()
        private val runningWorkflowJobs = ConcurrentHashMap<String, MutableSet<Job>>()
        private val _runningWorkflowIds = MutableStateFlow<Set<String>>(emptySet())
        val runningWorkflowIds: StateFlow<Set<String>> = _runningWorkflowIds.asStateFlow()

        fun notifyWorkflowsChanged() {
            speechTriggerCachedWorkflows = null
            speechTriggerCachedAtMs = 0L
            workflowUpdateEvents.tryEmit(Unit)
        }

        private fun publishRunningWorkflowIdsLocked() {
            val emptyWorkflowIds = mutableListOf<String>()
            runningWorkflowJobs.forEach { (workflowId, jobs) ->
                jobs.removeAll { !it.isActive }
                if (jobs.isEmpty()) {
                    emptyWorkflowIds += workflowId
                }
            }
            emptyWorkflowIds.forEach(runningWorkflowJobs::remove)
            _runningWorkflowIds.value = runningWorkflowJobs.keys.toSet()
        }

        private fun registerRunningWorkflow(workflowId: String, job: Job) {
            synchronized(runningWorkflowLock) {
                runningWorkflowJobs.getOrPut(workflowId) { mutableSetOf() }.add(job)
                publishRunningWorkflowIdsLocked()
            }
        }

        private fun unregisterRunningWorkflow(workflowId: String, job: Job) {
            synchronized(runningWorkflowLock) {
                runningWorkflowJobs[workflowId]?.remove(job)
                publishRunningWorkflowIdsLocked()
            }
        }

        private fun getRunningWorkflowJobs(
            workflowId: String,
            targetJob: Job? = null
        ): List<Job> {
            synchronized(runningWorkflowLock) {
                publishRunningWorkflowIdsLocked()
                val jobs = runningWorkflowJobs[workflowId].orEmpty().filter { it.isActive }
                return if (targetJob == null) {
                    jobs
                } else {
                    jobs.filter { it === targetJob }
                }
            }
        }
    }

    private class WorkflowStorageException(
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)
    
    /**
     * 获取工作流存储目录
     */
    private fun getWorkflowDirectory(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val workflowDir = File(downloadDir, WORKFLOW_DIR)
        if (workflowDir.exists()) {
            if (!workflowDir.isDirectory) {
                throw WorkflowStorageException(
                    context.getString(R.string.workflow_storage_path_not_directory, workflowDir.absolutePath)
                )
            }
        } else if (!workflowDir.mkdirs()) {
            throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_dir_unavailable, workflowDir.absolutePath)
            )
        }

        if (!workflowDir.canRead()) {
            throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_dir_unreadable, workflowDir.absolutePath)
            )
        }
        return workflowDir
    }
    
    /**
     * 获取工作流文件
     */
    private fun getWorkflowFile(workflowId: String): File {
        return File(getWorkflowDirectory(), "$workflowId.json")
    }

    private fun getAtomicBackupFile(file: File): File {
        val parent = file.parentFile
            ?: throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_file_parent_missing, file.absolutePath)
            )
        return File(parent, "${file.name}.bak")
    }

    private fun hasAtomicReadableFile(file: File): Boolean {
        return file.exists() || getAtomicBackupFile(file).exists()
    }

    private fun listWorkflowFiles(workflowDir: File): List<File> {
        val files = workflowDir.listFiles() ?: throw WorkflowStorageException(
            context.getString(R.string.workflow_storage_dir_unreadable, workflowDir.absolutePath)
        )
        val jsonFiles = files.filter { file ->
            file.isFile && file.extension == "json"
        }
        val jsonFileNames = jsonFiles.map { it.name }.toSet()
        val backupOnlyFiles = files.mapNotNull { file ->
            if (!file.isFile || !file.name.endsWith(".json.bak")) {
                return@mapNotNull null
            }

            val baseName = file.name.removeSuffix(".bak")
            if (baseName in jsonFileNames) {
                null
            } else {
                File(workflowDir, baseName)
            }
        }
        return jsonFiles + backupOnlyFiles
    }

    private fun readWorkflowFile(file: File, workflowId: String = file.nameWithoutExtension): Workflow {
        val content = AtomicFile(file).openRead().use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
        val element = json.parseToJsonElement(content)
        val workflowObject = element as? JsonObject
            ?: throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_file_root_invalid, file.name)
            )
        val workflowElement = JsonObject(workflowObject + ("id" to JsonPrimitive(workflowId)))
        return json.decodeFromJsonElement(Workflow.serializer(), workflowElement)
    }

    private fun writeTextAtomically(file: File, content: String) {
        val parent = file.parentFile
            ?: throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_file_parent_missing, file.absolutePath)
            )
        if (!parent.exists() && !parent.mkdirs()) {
            throw WorkflowStorageException(
                context.getString(R.string.workflow_storage_dir_unavailable, parent.absolutePath)
            )
        }

        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private suspend fun <T> withWorkflowStoreLock(block: suspend () -> T): T {
        return workflowStoreMutex.withLock { block() }
    }

    private fun getExecutionLogDirectory(workflowId: String, createIfMissing: Boolean = true): File {
        val dir = File(getWorkflowDirectory(), "$EXECUTION_LOG_SUB_DIR/$workflowId")
        if (createIfMissing && !dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun saveExecutionRecord(record: WorkflowExecutionRecord) {
        try {
            val dir = getExecutionLogDirectory(record.workflowId)
            val safeRunId = record.runId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "${record.startedAt}_$safeRunId.json")
            writeTextAtomically(file, json.encodeToString(record))

            val allFiles = dir.listFiles { f -> f.isFile && f.extension == "json" }?.toList().orEmpty()
            if (allFiles.size > MAX_EXECUTION_LOG_FILES_PER_WORKFLOW) {
                allFiles.sortedBy { it.lastModified() }
                    .take(allFiles.size - MAX_EXECUTION_LOG_FILES_PER_WORKFLOW)
                    .forEach { oldFile ->
                        runCatching { oldFile.delete() }
                    }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save workflow execution record: ${record.workflowId}", e)
        }
    }

    private fun hasScheduleTrigger(workflow: Workflow): Boolean {
        return workflow.nodes.filterIsInstance<TriggerNode>().any { it.triggerType == "schedule" }
    }
    
    /**
     * 获取所有工作流
     */
    suspend fun getAllWorkflows(): Result<List<Workflow>> = withContext(Dispatchers.IO) {
        try {
            val workflows = withWorkflowStoreLock {
                val workflowDir = getWorkflowDirectory()
                val workflowFiles = listWorkflowFiles(workflowDir)
                val invalidFileNames = mutableListOf<String>()
                val loadedWorkflows = workflowFiles.mapNotNull { file ->
                    try {
                        readWorkflowFile(file)
                    } catch (e: Exception) {
                        invalidFileNames += file.name
                        AppLogger.e(TAG, "Failed to parse workflow file: ${file.name}", e)
                        null
                    }
                }

                if (invalidFileNames.isNotEmpty()) {
                    workflowStorageWarnings.tryEmit(
                        context.getString(
                            R.string.workflow_storage_invalid_files,
                            invalidFileNames.size,
                            invalidFileNames.joinToString(", ")
                        )
                    )
                }

                loadedWorkflows.sortedByDescending { it.updatedAt }
            }
            
            Result.success(workflows)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get all workflows", e)
            Result.failure(e)
        }
    }
    
    /**
     * 根据ID获取工作流
     */
    suspend fun getWorkflowById(id: String): Result<Workflow?> = withContext(Dispatchers.IO) {
        try {
            val workflow = withWorkflowStoreLock {
                val file = getWorkflowFile(id)
                if (!hasAtomicReadableFile(file)) {
                    return@withWorkflowStoreLock null
                }

                try {
                    readWorkflowFile(file, id)
                } catch (e: Exception) {
                    throw WorkflowStorageException(
                        context.getString(R.string.workflow_storage_single_file_invalid, file.name),
                        e
                    )
                }
            }
            Result.success(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get workflow by id: $id", e)
            Result.failure(e)
        }
    }

    suspend fun getLatestExecutionRecord(workflowId: String): Result<WorkflowExecutionRecord?> = withContext(Dispatchers.IO) {
        try {
            val dir = getExecutionLogDirectory(workflowId, createIfMissing = false)
            if (!dir.exists()) {
                return@withContext Result.success(null)
            }
            val latestFile =
                dir.listFiles { f -> f.isFile && f.extension == "json" }
                    ?.maxByOrNull { it.lastModified() }
                    ?: return@withContext Result.success(null)

            val content = latestFile.readText()
            val record = json.decodeFromString<WorkflowExecutionRecord>(content)
            Result.success(record)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get latest execution record for workflow: $workflowId", e)
            Result.failure(e)
        }
    }
    
    /**
     * 创建工作流
     */
    suspend fun createWorkflow(workflow: Workflow): Result<Workflow> = withContext(Dispatchers.IO) {
        try {
            require(workflow.id.isNotBlank()) { "Workflow id cannot be empty" }
            withWorkflowStoreLock {
                val file = getWorkflowFile(workflow.id)
                val content = json.encodeToString(workflow)
                writeTextAtomically(file, content)
            }
            
            AppLogger.d(TAG, "Workflow created: ${workflow.id}")
            
            // Schedule if enabled and has schedule trigger
            if (workflow.enabled && hasScheduleTrigger(workflow)) {
                scheduleWorkflow(workflow.id)
            }

            notifyWorkflowsChanged()
            
            Result.success(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新工作流
     */
    suspend fun updateWorkflow(workflow: Workflow): Result<Workflow> = withContext(Dispatchers.IO) {
        try {
            require(workflow.id.isNotBlank()) { "Workflow id cannot be empty" }
            val updatedWorkflow = workflow.copy(updatedAt = System.currentTimeMillis())
            withWorkflowStoreLock {
                val file = getWorkflowFile(updatedWorkflow.id)
                val content = json.encodeToString(updatedWorkflow)
                writeTextAtomically(file, content)
            }
            
            AppLogger.d(TAG, "Workflow updated: ${updatedWorkflow.id}")
            
            // Keep WorkManager in sync with the latest workflow state.
            if (updatedWorkflow.enabled && hasScheduleTrigger(updatedWorkflow)) {
                rescheduleWorkflow(updatedWorkflow.id)
            } else {
                unscheduleWorkflow(updatedWorkflow.id)
            }

            notifyWorkflowsChanged()
            
            Result.success(updatedWorkflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update workflow", e)
            Result.failure(e)
        }
    }

    private fun buildPreExecutionFailureRecord(
        workflow: Workflow,
        triggerNodeId: String?,
        failureStage: WorkflowExecutionFailureStage,
        failureReason: String,
        logLevel: WorkflowLogLevel
    ): WorkflowExecutionRecord {
        val now = System.currentTimeMillis()
        val message = when (failureStage) {
            WorkflowExecutionFailureStage.CANCELLATION ->
                context.getString(R.string.workflow_log_execution_cancelled_reason, failureReason)
            else -> context.getString(R.string.workflow_log_startup_failed, failureReason)
        }
        return WorkflowExecutionRecord(
            runId = UUID.randomUUID().toString(),
            workflowId = workflow.id,
            workflowName = workflow.name,
            triggerNodeId = triggerNodeId,
            startedAt = now,
            finishedAt = now,
            success = false,
            message = message,
            logs = listOf(
                WorkflowExecutionLogEntry(
                    level = logLevel,
                    message = message
                )
            ),
            failureStage = failureStage,
            failureReason = failureReason
        )
    }

    private suspend fun persistPreExecutionFailure(
        workflow: Workflow,
        triggerNodeId: String?,
        failureStage: WorkflowExecutionFailureStage,
        failureReason: String,
        logLevel: WorkflowLogLevel
    ): WorkflowExecutionRecord {
        val record = buildPreExecutionFailureRecord(
            workflow = workflow,
            triggerNodeId = triggerNodeId,
            failureStage = failureStage,
            failureReason = failureReason,
            logLevel = logLevel
        )
        saveExecutionRecord(record)
        updateExecutionStatistics(workflow.id, ExecutionStatus.FAILED, record.finishedAt)
        return record
    }

    suspend fun setWorkflowEnabled(id: String, enabled: Boolean): Result<Workflow> = withContext(Dispatchers.IO) {
        try {
            require(id.isNotBlank()) { "Workflow id cannot be empty" }
            val updatedWorkflow = withWorkflowStoreLock {
                val file = getWorkflowFile(id)
                if (!hasAtomicReadableFile(file)) {
                    return@withWorkflowStoreLock null
                }

                val workflow = readWorkflowFile(file, id)
                val nextWorkflow = workflow.copy(enabled = enabled)
                val content = json.encodeToString(nextWorkflow)
                writeTextAtomically(file, content)
                nextWorkflow
            }
                ?: return@withContext Result.failure(Exception(context.getString(R.string.workflow_not_found)))

            AppLogger.d(TAG, "Workflow enabled state updated: ${updatedWorkflow.id} -> $enabled")

            if (updatedWorkflow.enabled && hasScheduleTrigger(updatedWorkflow)) {
                rescheduleWorkflow(updatedWorkflow.id)
            } else {
                unscheduleWorkflow(updatedWorkflow.id)
            }

            notifyWorkflowsChanged()

            Result.success(updatedWorkflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update workflow enabled state", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除工作流
     */
    suspend fun deleteWorkflow(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Cancel schedule first
            unscheduleWorkflow(id)

            val deleted = withWorkflowStoreLock {
                val file = getWorkflowFile(id)
                val backupFile = getAtomicBackupFile(file)
                val baseDeleted = if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
                val backupDeleted = if (backupFile.exists()) {
                    backupFile.delete()
                } else {
                    false
                }

                runCatching {
                    val logDir = getExecutionLogDirectory(id, createIfMissing = false)
                    if (logDir.exists()) {
                        logDir.deleteRecursively()
                    }
                }
                baseDeleted || backupDeleted
            }
            
            AppLogger.d(TAG, "Workflow deleted: $id, success: $deleted")
            if (deleted) {
                notifyWorkflowsChanged()
            }
            Result.success(deleted)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete workflow", e)
            Result.failure(e)
        }
    }
    
    /**
     * 触发工作流执行
     * @param id 工作流ID
     * @param triggerNodeId 指定要触发的节点ID，如果为null则触发所有触发节点
     */
    suspend fun triggerWorkflow(
        id: String,
        triggerNodeId: String? = null,
        triggerExtras: Map<String, String> = emptyMap()
    ): Result<String> = triggerWorkflowInternal(
        id = id,
        triggerNodeId = triggerNodeId,
        triggerExtras = triggerExtras
    ) { nodeId, state ->
        AppLogger.d(TAG, "Node $nodeId state: $state")
    }
    
    /**
     * 触发工作流执行（带状态回调）
     * @param id 工作流ID
     * @param triggerNodeId 指定要触发的节点ID，如果为null则触发所有触发节点
     * @param onNodeStateChange 节点状态变化回调
     */
    suspend fun triggerWorkflowWithCallback(
        id: String,
        triggerNodeId: String? = null,
        triggerExtras: Map<String, String> = emptyMap(),
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): Result<String> = triggerWorkflowInternal(
        id = id,
        triggerNodeId = triggerNodeId,
        triggerExtras = triggerExtras,
        onNodeStateChange = onNodeStateChange
    )

    suspend fun cancelWorkflow(
        id: String,
        targetJob: Job? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jobs = getRunningWorkflowJobs(id, targetJob)
            if (jobs.isEmpty()) {
                return@withContext Result.success(false)
            }

            AppLogger.d(TAG, "Cancelling workflow execution: $id, jobs=${jobs.size}")
            jobs.forEach { job ->
                job.cancel(CancellationException(context.getString(R.string.workflow_execution_cancelled)))
            }
            Result.success(true)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to cancel workflow: $id", e)
            Result.failure(e)
        }
    }

    private suspend fun triggerWorkflowInternal(
        id: String,
        triggerNodeId: String? = null,
        triggerExtras: Map<String, String> = emptyMap(),
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val workflowResult = getWorkflowById(id)
        val workflow = workflowResult.getOrNull()

        if (workflow == null) {
            return@withContext Result.failure(Exception(context.getString(R.string.workflow_not_exist, id)))
        }

        if (!workflow.enabled) {
            return@withContext Result.failure(Exception(context.getString(R.string.workflow_disabled_message, workflow.name)))
        }

        if (getRunningWorkflowJobs(id).isNotEmpty()) {
            return@withContext Result.failure(Exception(context.getString(R.string.workflow_already_running, workflow.name)))
        }

        val workflowJob = currentCoroutineContext().job
        registerRunningWorkflow(id, workflowJob)

        try {
            AppLogger.d(TAG, "Triggering workflow: ${workflow.name} (${workflow.id})")
            if (triggerNodeId != null) {
                AppLogger.d(TAG, "With specific trigger node: $triggerNodeId")
            }

            updateExecutionStatus(id, ExecutionStatus.RUNNING, System.currentTimeMillis())

            val executor = WorkflowExecutor(context)
            val result = executor.executeWorkflow(workflow, triggerNodeId, triggerExtras, onNodeStateChange)
            val executionStatus = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED
            withContext(NonCancellable) {
                saveExecutionRecord(result.executionRecord)
                updateExecutionStatistics(id, executionStatus, result.executionTime)
            }

            if (result.success) {
                Result.success(context.getString(R.string.workflow_execute_success, workflow.name))
            } else {
                val exception = if (result.shouldRetry) {
                    WorkflowExecutionRetryableException(result.message)
                } else {
                    Exception(result.message)
                }
                Result.failure(exception)
            }
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Workflow execution cancelled: $id")
            withContext(NonCancellable) {
                // Cancellation can happen before the executor creates its run record. Persist it here
                // so workflow status never points to an execution that has no diagnostic evidence.
                persistPreExecutionFailure(
                    workflow = workflow,
                    triggerNodeId = triggerNodeId,
                    failureStage = WorkflowExecutionFailureStage.CANCELLATION,
                    failureReason = e.toString(),
                    logLevel = WorkflowLogLevel.WARN
                )
            }
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger workflow", e)
            val record = withContext(NonCancellable) {
                persistPreExecutionFailure(
                    workflow = workflow,
                    triggerNodeId = triggerNodeId,
                    failureStage = WorkflowExecutionFailureStage.WORKFLOW_STARTUP,
                    failureReason = e.toString(),
                    logLevel = WorkflowLogLevel.ERROR
                )
            }
            Result.failure(WorkflowExecutionRetryableException(record.message))
        } finally {
            unregisterRunningWorkflow(id, workflowJob)
        }
    }
    
    /**
     * 更新工作流执行状态（仅状态和时间）
     */
    private suspend fun updateExecutionStatus(
        id: String,
        status: ExecutionStatus,
        executionTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            withWorkflowStoreLock {
                val file = getWorkflowFile(id)
                if (!hasAtomicReadableFile(file)) {
                    return@withWorkflowStoreLock
                }

                val workflow = readWorkflowFile(file, id)
                val updatedWorkflow = workflow.copy(
                    lastExecutionStatus = status,
                    lastExecutionTime = executionTime
                )
                val content = json.encodeToString(updatedWorkflow)
                writeTextAtomically(file, content)
            }
            
            AppLogger.d(TAG, "Workflow execution status updated: $id -> $status")
            notifyWorkflowsChanged()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update execution status", e)
        }
    }
    
    /**
     * 更新工作流执行统计信息
     */
    private suspend fun updateExecutionStatistics(
        id: String,
        status: ExecutionStatus,
        executionTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val updatedWorkflow = withWorkflowStoreLock {
                val file = getWorkflowFile(id)
                if (!hasAtomicReadableFile(file)) {
                    return@withWorkflowStoreLock null
                }

                val workflow = readWorkflowFile(file, id)
                val nextWorkflow = workflow.copy(
                    lastExecutionStatus = status,
                    lastExecutionTime = executionTime,
                    totalExecutions = workflow.totalExecutions + 1,
                    successfulExecutions = if (status == ExecutionStatus.SUCCESS) {
                        workflow.successfulExecutions + 1
                    } else {
                        workflow.successfulExecutions
                    },
                    failedExecutions = if (status == ExecutionStatus.FAILED) {
                        workflow.failedExecutions + 1
                    } else {
                        workflow.failedExecutions
                    }
                )
                val content = json.encodeToString(nextWorkflow)
                writeTextAtomically(file, content)
                nextWorkflow
            } ?: return@withContext
            
            AppLogger.d(TAG, "Workflow execution statistics updated: $id (total: ${updatedWorkflow.totalExecutions}, success: ${updatedWorkflow.successfulExecutions})")
            notifyWorkflowsChanged()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update execution statistics", e)
        }
    }
    
    /**
     * Schedule a workflow
     */
    fun scheduleWorkflow(id: String): Boolean {
        return try {
            val workflowResult = kotlinx.coroutines.runBlocking { getWorkflowById(id) }
            val workflow = workflowResult.getOrNull()
            
            if (workflow == null) {
                AppLogger.w(TAG, "Workflow not found for scheduling: $id")
                return false
            }
            
            if (!workflow.enabled) {
                AppLogger.d(TAG, "Workflow is disabled, not scheduling: $id")
                return false
            }

            if (!hasScheduleTrigger(workflow)) {
                return false
            }
            
            scheduler.scheduleWorkflow(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to schedule workflow: $id", e)
            false
        }
    }
    
    /**
     * Unschedule a workflow
     */
    fun unscheduleWorkflow(id: String) {
        try {
            scheduler.cancelWorkflow(id)
            AppLogger.d(TAG, "Workflow unscheduled: $id")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unschedule workflow: $id", e)
        }
    }
    
    /**
     * Reschedule a workflow (cancel + schedule)
     */
    fun rescheduleWorkflow(id: String): Boolean {
        unscheduleWorkflow(id)
        return scheduleWorkflow(id)
    }
    
    /**
     * Check if workflow is scheduled
     */
    suspend fun isWorkflowScheduled(id: String): Boolean {
        return try {
            scheduler.isWorkflowScheduled(id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check schedule status: $id", e)
            false
        }
    }
    
    /**
     * Get next execution time for a workflow
     */
    suspend fun getNextExecutionTime(id: String): Long? = withContext(Dispatchers.IO) {
        try {
            val workflowResult = getWorkflowById(id)
            val workflow = workflowResult.getOrNull() ?: return@withContext null
            scheduler.getNextExecutionTime(workflow)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get next execution time: $id", e)
            null
        }
    }


    /**
     * Finds and triggers workflows based on a Tasker event.
     * It checks all enabled workflows for a Tasker trigger node whose configuration matches the event data.
     *
     * @param params The list of parameters received from Tasker.
     */
    suspend fun triggerWorkflowsByTaskerEvent(params: List<String>?) = withContext(Dispatchers.IO) {
        if (params.isNullOrEmpty()) return@withContext

        AppLogger.d(TAG, "Checking for Tasker-triggered workflows with params: $params")
        val workflows = getAllWorkflows().getOrNull() ?: return@withContext

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node is TriggerNode && node.triggerType == "tasker") {
                        // Matching logic: The node's config expects a "command".
                        // It checks if any of the parameters from Tasker exactly matches this command.
                        // Example config: `{"command": "start_meeting"}`.
                        // This will match if any of the params from Tasker is "start_meeting" (case-insensitive).
                        val command = node.triggerConfig["command"]
                        if (command != null && params.any { it.equals(command, ignoreCase = true) }) {
                            AppLogger.d(TAG, "Tasker trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering.")
                            launch {
                                triggerWorkflow(workflow.id, node.id)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds and triggers workflows based on a received Intent.
     * It checks all enabled workflows for an Intent trigger node whose configuration matches the Intent's action.
     *
     * @param intent The Intent received by the BroadcastReceiver.
     */
    suspend fun triggerWorkflowsByIntentEvent(intent: Intent) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Checking for Intent-triggered workflows for action: ${intent.action}")
        val workflows = getAllWorkflows().getOrNull() ?: return@withContext

        val extras: Map<String, String> = try {
            val bundle = intent.extras
            if (bundle == null) {
                emptyMap()
            } else {
                bundle.keySet().associateWith { key ->
                    bundle.get(key)?.toString() ?: ""
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node is TriggerNode && node.triggerType == "intent") {
                        // Match based on the Intent action.
                        // Example config: `{"action": "com.example.MY_ACTION"}`.
                        val expectedAction = node.triggerConfig["action"]
                        if (expectedAction != null && expectedAction.equals(intent.action, ignoreCase = true)) {
                            AppLogger.d(TAG, "Intent trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering.")
                            launch {
                                triggerWorkflow(workflow.id, node.id, extras)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun triggerWorkflowsByColdStartAppOpen(
        extras: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Checking for cold-start app-open-triggered workflows")
        val workflows = getAllWorkflows().getOrNull() ?: return@withContext
        val triggerExtras =
            buildMap {
                put("trigger_source", "cold_start_app_open")
                putAll(extras)
            }

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node is TriggerNode && node.triggerType == "app_open") {
                        AppLogger.d(
                            TAG,
                            "Cold-start app-open trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering."
                        )
                        launch {
                            triggerWorkflow(workflow.id, node.id, triggerExtras)
                        }
                    }
                }
            }
        }
    }

    suspend fun triggerWorkflowsBySpeechEvent(text: String, isFinal: Boolean) = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext

        val now = System.currentTimeMillis()
        val cached = speechTriggerCachedWorkflows
        val workflows = if (cached != null && now - speechTriggerCachedAtMs < SPEECH_TRIGGER_CACHE_TTL_MS) {
            cached
        } else {
            val loaded = getAllWorkflows().getOrNull() ?: emptyList()
            speechTriggerCachedWorkflows = loaded
            speechTriggerCachedAtMs = now
            loaded
        }

        fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
            val normalized = value?.trim()?.lowercase() ?: return defaultValue
            return when (normalized) {
                "true", "1", "yes", "y", "on" -> true
                "false", "0", "no", "n", "off" -> false
                else -> defaultValue
            }
        }

        coroutineScope {
            workflows.filter { it.enabled }.forEach { workflow ->
                workflow.nodes.forEach { node ->
                    if (node !is TriggerNode || node.triggerType != "speech") return@forEach

                    val pattern = node.triggerConfig["pattern"].orEmpty()
                    if (pattern.isBlank()) return@forEach

                    val requireFinal = parseBoolean(node.triggerConfig["require_final"], true)
                    if (requireFinal && !isFinal) return@forEach

                    val ignoreCase = parseBoolean(node.triggerConfig["ignore_case"], true)
                    val cooldownMs = node.triggerConfig["cooldown_ms"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 3000L
                    val cooldownKey = "${workflow.id}:${node.id}"

                    val lastFireAt = speechTriggerLastFireAtMs[cooldownKey] ?: 0L
                    if (cooldownMs > 0 && now - lastFireAt < cooldownMs) return@forEach

                    val matches = try {
                        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                        Regex(pattern, options).containsMatchIn(trimmed)
                    } catch (_: Exception) {
                        false
                    }

                    if (matches) {
                        speechTriggerLastFireAtMs[cooldownKey] = now
                        AppLogger.d(TAG, "Speech trigger matched for workflow '${workflow.name}' on node '${node.name}'. Triggering.")
                        launch {
                            triggerWorkflow(workflow.id, node.id)
                        }
                    }
                }
            }
        }
    }
}
