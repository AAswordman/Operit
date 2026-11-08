package com.ai.assistance.operit.core.workflow.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.UUID

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowLogLevel")
@Serializable
enum class WorkflowLogLevel {
    DEBUG,
    WARN,
    ERROR
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowExecutionFailureStage")
@Serializable
enum class WorkflowExecutionFailureStage {
    WORKFLOW_STARTUP,
    RUNTIME_INITIALIZATION,
    WORKFLOW_EXECUTION,
    CANCELLATION
}

// 时间戳必须写入，避免省略动态默认值后在读取时重新生成。
@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowExecutionLogEntry")
@Serializable
data class WorkflowExecutionLogEntry(
    @EncodeDefault
    val timestamp: Long = System.currentTimeMillis(),
    val level: WorkflowLogLevel,
    val message: String,
    val nodeId: String? = null,
    val nodeName: String? = null
)

// 运行标识和起止时间属于持久化快照，必须保留创建时的值。
@OptIn(ExperimentalSerializationApi::class)
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowExecutionRecord")
@Serializable
data class WorkflowExecutionRecord(
    @EncodeDefault
    val runId: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val workflowName: String,
    val triggerNodeId: String? = null,
    @EncodeDefault
    val startedAt: Long = System.currentTimeMillis(),
    @EncodeDefault
    val finishedAt: Long = System.currentTimeMillis(),
    val success: Boolean,
    val message: String,
    val logs: List<WorkflowExecutionLogEntry> = emptyList(),
    val failureStage: WorkflowExecutionFailureStage? = null,
    val failureReason: String? = null,
    val issue: WorkflowExecutionIssue? = null,
    val nodes: List<WorkflowNodeExecutionReport> = emptyList(),
)

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowNodeRunStatus")
@Serializable
enum class WorkflowNodeRunStatus { SUCCESS, FAILED, SKIPPED, NOT_RUN, CANCELLED }

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowExecutionIssue")
@Serializable
enum class WorkflowExecutionIssue {
    TIMEOUT, NETWORK, AUTHENTICATION, QUOTA, PERMISSION, TOOL_MISSING,
    INVALID_INPUT, REFERENCE, BUSY, CANCELLED, CYCLE, NO_TRIGGER, RUNTIME, UNKNOWN
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowNodeExecutionReport")
@Serializable
data class WorkflowNodeExecutionReport(
    val nodeId: String,
    val nodeName: String,
    val status: WorkflowNodeRunStatus,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val issue: WorkflowExecutionIssue? = null,
    val resultSummary: String? = null,
    val failureReason: String? = null,
    val actionName: String? = null,
)
