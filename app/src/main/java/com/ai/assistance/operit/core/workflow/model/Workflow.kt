package com.ai.assistance.operit.core.workflow.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 工作流模型
 * 代表一个完整的自动化工作流程
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.Workflow")
@Serializable
data class Workflow(
    val id: String,
    var name: String = "",
    var description: String = "",
    var nodes: List<WorkflowNode> = emptyList(),
    var connections: List<WorkflowNodeConnection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var enabled: Boolean = true,
    // 执行统计信息
    var lastExecutionTime: Long? = null,  // 上次执行时间
    var lastExecutionStatus: ExecutionStatus? = null,  // 上次执行状态
    var totalExecutions: Int = 0,  // 总执行次数
    var successfulExecutions: Int = 0,  // 成功执行次数
    var failedExecutions: Int = 0  // 失败执行次数
)

/**
 * 执行状态枚举
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ExecutionStatus")
@Serializable
enum class ExecutionStatus {
    SUCCESS,  // 成功
    FAILED,   // 失败
    RUNNING   // 运行中
}

/**
 * 工作流节点基类
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowNode")
@Serializable
sealed class WorkflowNode {
    abstract val id: String
    abstract val type: String
    abstract var name: String
    abstract var description: String
    abstract var position: NodePosition
}

/**
 * 触发节点
 * 定义工作流的触发条件（暂时为占位符）
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.TriggerNode")
@Serializable
data class TriggerNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "trigger",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var triggerType: String = "manual", // manual, schedule, tasker, intent, speech, app_open(cold start)
    var triggerConfig: Map<String, String> = emptyMap() // 触发器配置参数
) : WorkflowNode()

/**
 * 执行节点
 * 定义工作流的执行动作
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ExecuteNode")
@Serializable
data class ExecuteNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "execute",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var actionType: String = "", // 工具名称，如 "http_request", "list_files", "click_element"
    var actionConfig: Map<String, ParameterValue> = emptyMap(), // 工具参数：支持静态值或节点引用
    var jsCode: String? = null // 可选：直接执行 JavaScript 代码
) : WorkflowNode()

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ConditionOperator")
@Serializable
enum class ConditionOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    CONTAINS,
    NOT_CONTAINS,
    IN,
    NOT_IN
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ConditionNode")
@Serializable
data class ConditionNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "condition",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var left: ParameterValue = ParameterValue.StaticValue(""),
    var operator: ConditionOperator = ConditionOperator.EQ,
    var right: ParameterValue = ParameterValue.StaticValue("")
) : WorkflowNode()

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.LogicOperator")
@Serializable
enum class LogicOperator {
    AND,
    OR
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.LogicNode")
@Serializable
data class LogicNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "logic",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var operator: LogicOperator = LogicOperator.AND
) : WorkflowNode()

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ExtractMode")
@Serializable
enum class ExtractMode {
    REGEX,
    JSON,
    SUB,
    CONCAT,
    RANDOM_INT,
    RANDOM_STRING
}

@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ExtractNode")
@Serializable
data class ExtractNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "extract",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var source: ParameterValue = ParameterValue.StaticValue(""),
    var mode: ExtractMode = ExtractMode.REGEX,
    var expression: String = "",
    var group: Int = 0,
    var defaultValue: String = "",
    var others: List<ParameterValue> = emptyList(),
    var startIndex: Int = 0,
    var length: Int = -1,
    var randomMin: Int = 0,
    var randomMax: Int = 100,
    var randomStringLength: Int = 8,
    var randomStringCharset: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
    var useFixed: Boolean = false,
    var fixedValue: String = ""
) : WorkflowNode()

/**
 * 参数值类型
 * 支持静态值或引用其他节点的输出
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ParameterValue")
@Serializable
sealed class ParameterValue {
    @kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ParameterValue.StaticValue")
    @Serializable
    data class StaticValue(val value: String) : ParameterValue()

    @kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.ParameterValue.NodeReference")
    @Serializable
    data class NodeReference(val nodeId: String) : ParameterValue()
}

/**
 * 节点位置信息（用于将来的可视化编辑器）
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.NodePosition")
@Serializable
data class NodePosition(
    var x: Float = 0f,
    var y: Float = 0f
)

/**
 * 工作流节点连接
 * 定义节点之间的连接关系
 */
@kotlinx.serialization.SerialName("com.ai.assistance.operit.data.model.WorkflowNodeConnection")
@Serializable
data class WorkflowNodeConnection(
    val id: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val targetNodeId: String,
    var condition: String? = null // 连接条件（可选）
)
