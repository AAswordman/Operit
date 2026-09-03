package com.ai.assistance.operit.ui.features.workflow.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.ConditionNode
import com.ai.assistance.operit.data.model.ConditionOperator
import com.ai.assistance.operit.data.model.ExecuteNode
import com.ai.assistance.operit.data.model.ExtractMode
import com.ai.assistance.operit.data.model.ExtractNode
import com.ai.assistance.operit.data.model.LogicNode
import com.ai.assistance.operit.data.model.LogicOperator
import com.ai.assistance.operit.data.model.NodePosition
import com.ai.assistance.operit.data.model.ParameterValue
import com.ai.assistance.operit.data.model.TriggerNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.WorkflowExecutionRecord
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 工作流ViewModel
 * 管理工作流的状态和业务逻辑
 */
class WorkflowViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WorkflowRepository(application)
    private val packageManager = PackageManager.getInstance(application, AIToolHandler.getInstance(application))
    private val app = application

    var workflows by mutableStateOf<List<Workflow>>(emptyList())
        private set

    var toolPkgWorkflowTemplates by mutableStateOf<List<PackageManager.ToolPkgWorkflowTemplate>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var currentWorkflow by mutableStateOf<Workflow?>(null)
        private set

    var latestExecutionRecord by mutableStateOf<WorkflowExecutionRecord?>(null)
        private set
    
    // 节点执行状态 Map
    private val _nodeExecutionStates = MutableStateFlow<Map<String, NodeExecutionState>>(emptyMap())
    val nodeExecutionStates: StateFlow<Map<String, NodeExecutionState>> = _nodeExecutionStates.asStateFlow()
    val runningWorkflowIds: StateFlow<Set<String>> = WorkflowRepository.runningWorkflowIds

    private var workflowsLoadRequestId = 0L
    private var workflowLoadRequestId = 0L
    private var activeVisibleLoadCount = 0
    
    init {
        viewModelScope.launch {
            WorkflowRepository.workflowStorageWarnings.collectLatest { warning ->
                error = warning
            }
        }

        loadWorkflows()
        loadToolPkgWorkflowTemplates()

        viewModelScope.launch {
            WorkflowRepository.workflowUpdateEvents.collectLatest {
                val workflowsRequestId = nextWorkflowsLoadRequestId()
                loadWorkflowsInternal(showLoading = false, requestId = workflowsRequestId)
                currentWorkflow?.id?.let { workflowId ->
                    val workflowRequestId = nextWorkflowLoadRequestId()
                    loadWorkflowInternal(
                        id = workflowId,
                        showLoading = false,
                        requestId = workflowRequestId
                    )
                }
            }
        }
    }

    fun loadToolPkgWorkflowTemplates() {
        toolPkgWorkflowTemplates =
            runCatching { packageManager.getToolPkgWorkflowTemplates(app) }
                .getOrElse {
                    error = it.message
                    emptyList()
                }
    }
    
    /**
     * 加载所有工作流
     */
    fun loadWorkflows(showLoading: Boolean = true) {
        val requestId = nextWorkflowsLoadRequestId()
        viewModelScope.launch {
            loadWorkflowsInternal(showLoading = showLoading, requestId = requestId)
        }
    }

    private fun nextWorkflowsLoadRequestId(): Long {
        workflowsLoadRequestId += 1
        return workflowsLoadRequestId
    }

    private fun nextWorkflowLoadRequestId(): Long {
        workflowLoadRequestId += 1
        return workflowLoadRequestId
    }

    private fun beginVisibleLoading(showLoading: Boolean, requestIsCurrent: Boolean): Boolean {
        if (!showLoading || !requestIsCurrent) {
            return false
        }

        activeVisibleLoadCount += 1
        isLoading = true
        return true
    }

    private fun finishVisibleLoading(started: Boolean) {
        if (!started) {
            return
        }

        if (activeVisibleLoadCount > 0) {
            activeVisibleLoadCount -= 1
        }

        if (activeVisibleLoadCount == 0) {
            isLoading = false
        }
    }

    private suspend fun loadWorkflowsInternal(showLoading: Boolean, requestId: Long) {
        val requestIsCurrent = requestId == workflowsLoadRequestId
        val visibleLoadingStarted = beginVisibleLoading(showLoading, requestIsCurrent)

        try {
            if (requestIsCurrent) {
                error = null
            }

            repository.getAllWorkflows().fold(
                onSuccess = {
                    if (requestId == workflowsLoadRequestId) {
                        workflows = it
                    }
                },
                onFailure = {
                    if (requestId == workflowsLoadRequestId) {
                        error = it.message ?: app.getString(R.string.workflow_load_failed)
                    }
                }
            )
        } finally {
            finishVisibleLoading(visibleLoadingStarted)
        }
    }

    fun createIntentChatBroadcastTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildIntentChatBroadcastTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_intent, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createSpeechTriggerTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildSpeechTriggerTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_voice, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createErrorBranchTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildErrorBranchTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_failure, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    /**
     * 更新连接条件
     */
    fun updateConnectionCondition(
        workflowId: String,
        connectionId: String,
        condition: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null

            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold

                    val updatedConnections = workflow.connections.map { connection ->
                        if (connection.id == connectionId) {
                            connection.copy(condition = condition)
                        } else {
                            connection
                        }
                    }

                    val updatedWorkflow = workflow.copy(
                        connections = updatedConnections,
                        updatedAt = System.currentTimeMillis()
                    )

                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_update_condition_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )

            isLoading = false
        }
    }

    fun createChatTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildChatTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_chat, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createConditionTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildConditionTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_judgment, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createLogicAndTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildLogicTemplateWorkflow(
                operator = LogicOperator.AND,
                context = context,
                name = context.getString(R.string.workflow_template_logic_and, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createLogicOrTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildLogicTemplateWorkflow(
                operator = LogicOperator.OR,
                context = context,
                name = context.getString(R.string.workflow_template_logic_or, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun createExtractTemplateWorkflow(context: Context, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null

            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val workflow = buildExtractTemplateWorkflow(
                context = context,
                name = context.getString(R.string.workflow_template_calculate, time),
                description = ""
            )

            repository.createWorkflow(workflow).fold(
                onSuccess = {
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )

            isLoading = false
        }
    }

    fun importToolPkgWorkflowTemplate(
        containerPackageName: String,
        templateId: String,
        onSuccess: (Workflow) -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null

            withContext(Dispatchers.IO) {
                packageManager.importToolPkgWorkflowTemplate(containerPackageName, templateId)
            }.fold(
                onSuccess = {
                    loadWorkflows(showLoading = false)
                    onSuccess(it)
                },
                onFailure = {
                    error = it.message ?: app.getString(R.string.workflow_create_failed)
                }
            )

            isLoading = false
        }
    }

    private fun templateNodePosition(index: Int): NodePosition {
        val maxPerColumn = 5
        val startX = 120f
        val startY = 120f
        val columnSpacing = 720f
        val rowSpacing = 420f
        val column = index / maxPerColumn
        val row = index % maxPerColumn
        return NodePosition(
            x = startX + column * columnSpacing,
            y = startY + row * rowSpacing
        )
    }

    private fun buildIntentChatBroadcastTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val startId = UUID.randomUUID().toString()
        val createChatId = UUID.randomUUID().toString()
        val extractMessageId = UUID.randomUUID().toString()
        val sendId = UUID.randomUUID().toString()
        val stopId = UUID.randomUUID().toString()
        val broadcastId = UUID.randomUUID().toString()
        val closeDisplaysId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_intent),
            triggerType = "intent",
            triggerConfig = mapOf(
                "action" to "com.ai.assistance.operit.TRIGGER_WORKFLOW"
            ),
            position = templateNodePosition(0)
        )

        val startChat = ExecuteNode(
            id = startId,
            name = context.getString(R.string.workflow_action_start_chat),
            actionType = "start_chat_service",
            position = templateNodePosition(1)
        )

        val createChat = ExecuteNode(
            id = createChatId,
            name = context.getString(R.string.workflow_action_create_chat),
            actionType = "create_new_chat",
            actionConfig = mapOf(
                "group" to ParameterValue.StaticValue("workflow")
            ),
            position = templateNodePosition(2)
        )

        val extractMessage = ExtractNode(
            id = extractMessageId,
            name = context.getString(R.string.workflow_action_extract_intent),
            mode = ExtractMode.JSON,
            source = ParameterValue.NodeReference(triggerId),
            expression = "message",
            defaultValue = "",
            position = templateNodePosition(3)
        )

        val sendMessage = ExecuteNode(
            id = sendId,
            name = context.getString(R.string.workflow_action_send_intent),
            actionType = "send_message_to_ai",
            actionConfig = mapOf(
                "message" to ParameterValue.NodeReference(extractMessageId)
            ),
            position = templateNodePosition(4)
        )

        val stopChat = ExecuteNode(
            id = stopId,
            name = context.getString(R.string.workflow_action_stop_chat),
            actionType = "stop_chat_service",
            position = templateNodePosition(5)
        )

        val sendBroadcast = ExecuteNode(
            id = broadcastId,
            name = context.getString(R.string.workflow_action_send_broadcast),
            actionType = "send_broadcast",
            actionConfig = mapOf(
                "action" to ParameterValue.StaticValue("com.ai.assistance.operit.WORKFLOW_RESULT"),
                "extra_key" to ParameterValue.StaticValue("result"),
                "extra_value" to ParameterValue.NodeReference(sendId)
            ),
            position = templateNodePosition(6)
        )

        val closeAllDisplays = ExecuteNode(
            id = closeDisplaysId,
            name = context.getString(R.string.workflow_action_close_displays),
            actionType = "close_all_virtual_displays",
            position = templateNodePosition(7)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = startId),
            WorkflowNodeConnection(sourceNodeId = startId, targetNodeId = createChatId),
            WorkflowNodeConnection(sourceNodeId = createChatId, targetNodeId = extractMessageId),
            WorkflowNodeConnection(sourceNodeId = extractMessageId, targetNodeId = sendId),
            WorkflowNodeConnection(sourceNodeId = sendId, targetNodeId = stopId),
            WorkflowNodeConnection(sourceNodeId = stopId, targetNodeId = broadcastId),
            WorkflowNodeConnection(sourceNodeId = broadcastId, targetNodeId = closeDisplaysId)
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(trigger, startChat, createChat, extractMessage, sendMessage, stopChat, sendBroadcast, closeAllDisplays),
            connections = connections
        )
    }

    private fun buildChatTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val startId = UUID.randomUUID().toString()
        val createChatId = UUID.randomUUID().toString()
        val sendId = UUID.randomUUID().toString()
        val stopId = UUID.randomUUID().toString()
        val closeDisplaysId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_manual),
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val startChat = ExecuteNode(
            id = startId,
            name = context.getString(R.string.workflow_action_start_chat),
            actionType = "start_chat_service",
            actionConfig = mapOf(
                "keep_if_exists" to ParameterValue.StaticValue("true"),
                "initial_mode" to ParameterValue.StaticValue("WINDOW")
            ),
            position = templateNodePosition(1)
        )

        val createChat = ExecuteNode(
            id = createChatId,
            name = context.getString(R.string.workflow_action_create_chat),
            actionType = "create_new_chat",
            actionConfig = mapOf(
                "group" to ParameterValue.StaticValue("workflow")
            ),
            position = templateNodePosition(2)
        )

        val sendMessage = ExecuteNode(
            id = sendId,
            name = context.getString(R.string.workflow_action_send),
            actionType = "send_message_to_ai",
            actionConfig = mapOf(
                "message" to ParameterValue.StaticValue(context.getString(R.string.workflow_param_message_hello))
            ),
            position = templateNodePosition(3)
        )

        val stopChat = ExecuteNode(
            id = stopId,
            name = context.getString(R.string.workflow_action_stop_chat),
            actionType = "stop_chat_service",
            position = templateNodePosition(4)
        )

        val closeAllDisplays = ExecuteNode(
            id = closeDisplaysId,
            name = context.getString(R.string.workflow_action_close_displays),
            actionType = "close_all_virtual_displays",
            position = templateNodePosition(5)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = startId),
            WorkflowNodeConnection(sourceNodeId = startId, targetNodeId = createChatId),
            WorkflowNodeConnection(sourceNodeId = createChatId, targetNodeId = sendId),
            WorkflowNodeConnection(sourceNodeId = sendId, targetNodeId = stopId),
            WorkflowNodeConnection(sourceNodeId = stopId, targetNodeId = closeDisplaysId)
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(trigger, startChat, createChat, sendMessage, stopChat, closeAllDisplays),
            connections = connections
        )
    }

    private fun buildConditionTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val visitId = UUID.randomUUID().toString()
        val extractVisitKeyId = UUID.randomUUID().toString()
        val conditionId = UUID.randomUUID().toString()
        val followLinkId = UUID.randomUUID().toString()
        val fallbackVisitId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_manual),
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val visitWeb = ExecuteNode(
            id = visitId,
            name = context.getString(R.string.workflow_action_visit_web),
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.com")
            ),
            position = templateNodePosition(1)
        )

        val extractVisitKey = ExtractNode(
            id = extractVisitKeyId,
            name = context.getString(R.string.workflow_action_calculate_visit_key),
            source = ParameterValue.NodeReference(visitId),
            mode = ExtractMode.REGEX,
            expression = "Visit key:\\s*([0-9a-fA-F-]+)",
            group = 1,
            defaultValue = "",
            position = templateNodePosition(2)
        )

        val condition = ConditionNode(
            id = conditionId,
            name = context.getString(R.string.workflow_condition_check_keywords),
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("Example Domain"),
            position = templateNodePosition(3)
        )

        val followFirstLink = ExecuteNode(
            id = followLinkId,
            name = context.getString(R.string.workflow_action_match_open_link),
            actionType = "visit_web",
            actionConfig = mapOf(
                "visit_key" to ParameterValue.NodeReference(extractVisitKeyId),
                "link_number" to ParameterValue.StaticValue("1")
            ),
            position = templateNodePosition(4)
        )

        val fallbackVisit = ExecuteNode(
            id = fallbackVisitId,
            name = context.getString(R.string.workflow_action_no_match_backup),
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.org")
            ),
            position = templateNodePosition(5)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = visitId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = extractVisitKeyId),
            WorkflowNodeConnection(sourceNodeId = extractVisitKeyId, targetNodeId = conditionId),
            WorkflowNodeConnection(sourceNodeId = conditionId, targetNodeId = followLinkId),
            WorkflowNodeConnection(sourceNodeId = conditionId, targetNodeId = fallbackVisitId, condition = "false")
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(
                trigger,
                visitWeb,
                extractVisitKey,
                condition,
                followFirstLink,
                fallbackVisit
            ),
            connections = connections
        )
    }

    private fun buildLogicTemplateWorkflow(operator: LogicOperator, context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val visitId = UUID.randomUUID().toString()
        val conditionAId = UUID.randomUUID().toString()
        val conditionBId = UUID.randomUUID().toString()
        val logicId = UUID.randomUUID().toString()
        val extractVisitKeyId = UUID.randomUUID().toString()
        val followLinkId = UUID.randomUUID().toString()
        val fallbackVisitId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_manual),
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val visitWeb = ExecuteNode(
            id = visitId,
            name = context.getString(R.string.workflow_action_visit_web),
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.com")
            ),
            position = templateNodePosition(1)
        )

        val conditionA = ConditionNode(
            id = conditionAId,
            name = context.getString(R.string.workflow_condition_a),
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("Example"),
            position = templateNodePosition(2)
        )

        val conditionB = ConditionNode(
            id = conditionBId,
            name = context.getString(R.string.workflow_condition_b),
            left = ParameterValue.NodeReference(visitId),
            operator = ConditionOperator.CONTAINS,
            right = ParameterValue.StaticValue("Domain"),
            position = templateNodePosition(3)
        )

        val logic = LogicNode(
            id = logicId,
            name = context.getString(R.string.workflow_action_logic_judgment),
            operator = operator,
            position = templateNodePosition(4)
        )

        val extractVisitKey = ExtractNode(
            id = extractVisitKeyId,
            name = context.getString(R.string.workflow_action_calculate_visit_key),
            source = ParameterValue.NodeReference(visitId),
            mode = ExtractMode.REGEX,
            expression = "Visit key:\\s*([0-9a-fA-F-]+)",
            group = 1,
            defaultValue = "",
            position = templateNodePosition(5)
        )

        val followFirstLink = ExecuteNode(
            id = followLinkId,
            name = context.getString(R.string.workflow_action_logic_true),
            actionType = "visit_web",
            actionConfig = mapOf(
                "visit_key" to ParameterValue.NodeReference(extractVisitKeyId),
                "link_number" to ParameterValue.StaticValue("1")
            ),
            position = templateNodePosition(6)
        )

        val fallbackVisit = ExecuteNode(
            id = fallbackVisitId,
            name = context.getString(R.string.workflow_action_logic_false),
            actionType = "visit_web",
            actionConfig = mapOf(
                "url" to ParameterValue.StaticValue("https://example.org")
            ),
            position = templateNodePosition(7)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = visitId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = conditionAId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = conditionBId),
            WorkflowNodeConnection(sourceNodeId = conditionAId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = conditionBId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = visitId, targetNodeId = extractVisitKeyId),
            WorkflowNodeConnection(sourceNodeId = extractVisitKeyId, targetNodeId = logicId),
            WorkflowNodeConnection(sourceNodeId = logicId, targetNodeId = followLinkId),
            WorkflowNodeConnection(sourceNodeId = logicId, targetNodeId = fallbackVisitId, condition = "false")
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(
                trigger,
                logic,
                visitWeb,
                conditionA,
                conditionB,
                extractVisitKey,
                followFirstLink,
                fallbackVisit
            ),
            connections = connections
        )
    }

    private fun buildExtractTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val fixedIntId = UUID.randomUUID().toString()
        val randomIntId = UUID.randomUUID().toString()
        val fixedStrId = UUID.randomUUID().toString()
        val randomStrId = UUID.randomUUID().toString()
        val concatId = UUID.randomUUID().toString()
        val subId = UUID.randomUUID().toString()
        val compareId = UUID.randomUUID().toString()
        val showId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_manual),
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val fixedInt = ExtractNode(
            id = fixedIntId,
            name = context.getString(R.string.workflow_action_calculate_fixed),
            mode = ExtractMode.RANDOM_INT,
            useFixed = true,
            fixedValue = "42",
            randomMin = 0,
            randomMax = 100,
            position = templateNodePosition(1)
        )

        val randomInt = ExtractNode(
            id = randomIntId,
            name = context.getString(R.string.workflow_action_calculate_random),
            mode = ExtractMode.RANDOM_INT,
            useFixed = false,
            randomMin = 1,
            randomMax = 100,
            position = templateNodePosition(2)
        )

        val fixedStr = ExtractNode(
            id = fixedStrId,
            name = context.getString(R.string.workflow_action_calculate_fixed_string),
            mode = ExtractMode.RANDOM_STRING,
            useFixed = true,
            fixedValue = "hello",
            randomStringLength = 8,
            randomStringCharset = "abcdefghijklmnopqrstuvwxyz",
            position = templateNodePosition(3)
        )

        val randomStr = ExtractNode(
            id = randomStrId,
            name = context.getString(R.string.workflow_action_calculate_random_string),
            mode = ExtractMode.RANDOM_STRING,
            useFixed = false,
            randomStringLength = 8,
            randomStringCharset = "abcdefghijklmnopqrstuvwxyz0123456789",
            position = templateNodePosition(4)
        )

        val concat = ExtractNode(
            id = concatId,
            name = context.getString(R.string.workflow_action_calculate_concat),
            mode = ExtractMode.CONCAT,
            source = ParameterValue.NodeReference(fixedStrId),
            others = listOf(
                ParameterValue.StaticValue("-"),
                ParameterValue.NodeReference(randomStrId),
                ParameterValue.StaticValue("-"),
                ParameterValue.NodeReference(randomIntId)
            ),
            position = templateNodePosition(5)
        )

        val sub = ExtractNode(
            id = subId,
            name = context.getString(R.string.workflow_action_calculate_substring),
            mode = ExtractMode.SUB,
            source = ParameterValue.NodeReference(concatId),
            startIndex = 0,
            length = 12,
            defaultValue = "",
            position = templateNodePosition(6)
        )

        val compare = ConditionNode(
            id = compareId,
            name = context.getString(R.string.workflow_condition_random),
            left = ParameterValue.NodeReference(randomIntId),
            operator = ConditionOperator.GT,
            right = ParameterValue.StaticValue("50"),
            position = templateNodePosition(7)
        )

        val showResult = ExecuteNode(
            id = showId,
            name = context.getString(R.string.workflow_action_show_result),
            actionType = "toast",
            actionConfig = mapOf(
                "message" to ParameterValue.NodeReference(subId)
            ),
            position = templateNodePosition(8)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = fixedIntId),
            WorkflowNodeConnection(sourceNodeId = fixedIntId, targetNodeId = randomIntId),
            WorkflowNodeConnection(sourceNodeId = randomIntId, targetNodeId = fixedStrId),
            WorkflowNodeConnection(sourceNodeId = fixedStrId, targetNodeId = randomStrId),
            WorkflowNodeConnection(sourceNodeId = randomStrId, targetNodeId = concatId),
            WorkflowNodeConnection(sourceNodeId = concatId, targetNodeId = subId),
            WorkflowNodeConnection(sourceNodeId = randomIntId, targetNodeId = compareId),
            WorkflowNodeConnection(sourceNodeId = compareId, targetNodeId = showId, condition = "true")
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(
                trigger,
                fixedInt,
                randomInt,
                fixedStr,
                randomStr,
                concat,
                sub,
                compare,
                showResult
            ),
            connections = connections
        )
    }

    private fun buildErrorBranchTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val mainId = UUID.randomUUID().toString()
        val successId = UUID.randomUUID().toString()
        val errorId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_manual),
            triggerType = "manual",
            position = templateNodePosition(0)
        )

        val mainAction = ExecuteNode(
            id = mainId,
            name = context.getString(R.string.workflow_action_main),
            actionType = "",
            position = templateNodePosition(1)
        )

        val onSuccess = ConditionNode(
            id = successId,
            name = context.getString(R.string.workflow_action_success_branch),
            left = ParameterValue.StaticValue("1"),
            operator = ConditionOperator.EQ,
            right = ParameterValue.StaticValue("1"),
            position = templateNodePosition(2)
        )

        val onError = ConditionNode(
            id = errorId,
            name = context.getString(R.string.workflow_action_error_branch),
            left = ParameterValue.StaticValue("1"),
            operator = ConditionOperator.EQ,
            right = ParameterValue.StaticValue("1"),
            position = templateNodePosition(3)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = mainId),
            WorkflowNodeConnection(sourceNodeId = mainId, targetNodeId = successId, condition = "on_success"),
            WorkflowNodeConnection(sourceNodeId = mainId, targetNodeId = errorId, condition = "on_error")
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(trigger, mainAction, onSuccess, onError),
            connections = connections
        )
    }

    private fun buildSpeechTriggerTemplateWorkflow(context: Context, name: String, description: String): Workflow {
        val triggerId = UUID.randomUUID().toString()
        val startChatId = UUID.randomUUID().toString()

        val trigger = TriggerNode(
            id = triggerId,
            name = context.getString(R.string.workflow_trigger_voice),
            triggerType = "speech",
            triggerConfig = mapOf(
                "pattern" to ".*(打开|启动).*(对话|聊天|悬浮窗).*",
                "ignore_case" to "true",
                "require_final" to "true",
                "cooldown_ms" to "3000"
            ),
            position = templateNodePosition(0)
        )

        val startChat = ExecuteNode(
            id = startChatId,
            name = context.getString(R.string.workflow_action_start_chat),
            actionType = "start_chat_service",
            position = templateNodePosition(1)
        )

        val connections = listOf(
            WorkflowNodeConnection(sourceNodeId = triggerId, targetNodeId = startChatId)
        )

        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            nodes = listOf(trigger, startChat),
            connections = connections
        )
    }

    /**
     * 根据ID加载工作流
     */
    fun loadWorkflow(id: String, showLoading: Boolean = true) {
        val requestId = nextWorkflowLoadRequestId()
        viewModelScope.launch {
            loadWorkflowInternal(id = id, showLoading = showLoading, requestId = requestId)
        }
    }

    private suspend fun loadWorkflowInternal(id: String, showLoading: Boolean, requestId: Long) {
        if (requestId == workflowLoadRequestId) {
            if (showLoading) {
                isLoading = true
            }
            error = null
        }

        repository.getWorkflowById(id).fold(
            onSuccess = {
                if (requestId == workflowLoadRequestId) {
                    currentWorkflow = it
                    if (it == null) {
                        latestExecutionRecord = null
                    } else {
                        loadLatestExecutionRecordInternal(id, requestId)
                    }
                }
            },
            onFailure = {
                if (requestId == workflowLoadRequestId) {
                    currentWorkflow = null
                    latestExecutionRecord = null
                    error = it.message ?: app.getString(R.string.workflow_load_failed)
                }
            }
        )

        if (showLoading && requestId == workflowLoadRequestId) {
            isLoading = false
        }
    }

    private suspend fun loadLatestExecutionRecordInternal(
        workflowId: String,
        workflowRequestId: Long? = null
    ) {
        repository.getLatestExecutionRecord(workflowId).fold(
            onSuccess = {
                if (workflowRequestId == null || workflowRequestId == workflowLoadRequestId) {
                    latestExecutionRecord = it
                }
            },
            onFailure = {
                if (workflowRequestId == null || workflowRequestId == workflowLoadRequestId) {
                    latestExecutionRecord = null
                }
            }
        )
    }

    fun loadLatestExecutionRecord(workflowId: String) {
        viewModelScope.launch {
            loadLatestExecutionRecordInternal(workflowId)
        }
    }
    
    /**
     * 创建工作流
     */
    fun createWorkflow(name: String, description: String, onSuccess: (Workflow) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val workflow = Workflow(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description
            )
            
            repository.createWorkflow(workflow).fold(
                onSuccess = { 
                    loadWorkflows()
                    onSuccess(it)
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_create_failed) }
            )
            
            isLoading = false
        }
    }

    private fun replaceWorkflowInState(updatedWorkflow: Workflow) {
        workflows = workflows.map { workflow ->
            if (workflow.id == updatedWorkflow.id) {
                updatedWorkflow
            } else {
                workflow
            }
        }

        if (currentWorkflow?.id == updatedWorkflow.id) {
            currentWorkflow = updatedWorkflow
        }
    }

    fun setWorkflowEnabled(workflowId: String, enabled: Boolean, onSuccess: () -> Unit = {}) {
        val previousWorkflow = workflows.find { it.id == workflowId } ?: return
        if (previousWorkflow.enabled == enabled) {
            onSuccess()
            return
        }

        replaceWorkflowInState(previousWorkflow.copy(enabled = enabled))

        viewModelScope.launch {
            error = null

            repository.setWorkflowEnabled(workflowId, enabled).fold(
                onSuccess = { savedWorkflow ->
                    replaceWorkflowInState(savedWorkflow)
                    onSuccess()
                },
                onFailure = {
                    replaceWorkflowInState(previousWorkflow)
                    error = it.message ?: app.getString(R.string.workflow_error_update_failed)
                }
            )
        }
    }
    
    /**
     * 更新工作流
     */
    fun updateWorkflow(workflow: Workflow, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.updateWorkflow(workflow).fold(
                onSuccess = { 
                    currentWorkflow = it
                    loadWorkflows()
                    onSuccess()
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_error_update_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除工作流
     */
    fun deleteWorkflow(id: String, onSuccess: () -> Unit = {}) {
        deleteWorkflows(listOf(id), onSuccess)
    }

    /**
     * 批量删除工作流
     */
    fun deleteWorkflows(ids: List<String>, onSuccess: () -> Unit = {}) {
        if (ids.isEmpty()) {
            onSuccess()
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null

            var hasFailure = false
            var failureMessage: String? = null
            ids.forEach { id ->
                repository.deleteWorkflow(id).fold(
                    onSuccess = { deleted ->
                        if (!deleted) {
                            hasFailure = true
                            if (failureMessage == null) {
                                failureMessage = app.getString(R.string.workflow_error_delete_failed)
                            }
                        }
                    },
                    onFailure = {
                        hasFailure = true
                        if (failureMessage == null) {
                            failureMessage = it.message
                        }
                    }
                )
            }

            loadWorkflows(showLoading = false)
            if (hasFailure) {
                error = failureMessage ?: app.getString(R.string.workflow_error_delete_failed)
            } else {
                onSuccess()
            }

            isLoading = false
        }
    }
    
    /**
     * 触发工作流
     */
    fun triggerWorkflow(id: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            error = null
            _nodeExecutionStates.value = emptyMap()

            try {
                repository.triggerWorkflowWithCallback(id) { nodeId, state ->
                    _nodeExecutionStates.value = _nodeExecutionStates.value + (nodeId to state)
                }.fold(
                    onSuccess = { message ->
                        loadWorkflows()
                        if (currentWorkflow?.id == id) {
                            loadWorkflow(id)
                        }
                        onComplete(message)
                    },
                    onFailure = { error ->
                        loadWorkflows()
                        if (currentWorkflow?.id == id) {
                            loadWorkflow(id)
                        }
                        this@WorkflowViewModel.error = error.message ?: app.getString(R.string.workflow_error_trigger_failed)
                        onComplete(app.getString(R.string.workflow_error_execute_failed, error.message ?: ""))
                    }
                )
            } catch (_: CancellationException) {
                clearNodeExecutionStates()
                loadWorkflows(showLoading = false)
                if (currentWorkflow?.id == id) {
                    loadWorkflow(id, showLoading = false)
                }
            }
        }
    }

    fun cancelWorkflow(id: String, onComplete: (String) -> Unit = {}) {
        viewModelScope.launch {
            error = null

            repository.cancelWorkflow(id).fold(
                onSuccess = { cancelled ->
                    onComplete(
                        if (cancelled) {
                            app.getString(R.string.workflow_cancel_requested)
                        } else {
                            app.getString(R.string.workflow_not_running)
                        }
                    )
                },
                onFailure = {
                    error = it.message ?: app.getString(R.string.workflow_error_cancel_failed)
                }
            )
        }
    }
    
    /**
     * 清除节点执行状态
     */
    fun clearNodeExecutionStates() {
        _nodeExecutionStates.value = emptyMap()
    }
    
    /**
     * 添加节点到工作流
     */
    fun addNode(workflowId: String, node: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes + node,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_error_add_node_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除节点
     */
    fun deleteNode(workflowId: String, nodeId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.filter { it.id != nodeId },
                        connections = workflow.connections.filter { 
                            it.sourceNodeId != nodeId && it.targetNodeId != nodeId 
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_error_delete_node_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点
     */
    fun updateNode(workflowId: String, nodeId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        nodes = workflow.nodes.map { if (it.id == nodeId) updatedNode else it },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_error_update_node_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点（重载方法，直接接受节点对象）
     */
    fun updateNode(workflowId: String, updatedNode: com.ai.assistance.operit.data.model.WorkflowNode, onSuccess: () -> Unit = {}) {
        updateNode(workflowId, updatedNode.id, updatedNode, onSuccess)
    }
    
    /**
     * 创建连接
     */
    fun createConnection(
        workflowId: String,
        sourceId: String,
        targetId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    
                    // 检查连接是否已存在
                    val connectionExists = workflow.connections.any {
                        it.sourceNodeId == sourceId && it.targetNodeId == targetId
                    }
                    
                    if (connectionExists) {
                        error = app.getString(R.string.workflow_error_connection_exists)
                        isLoading = false
                        return@fold
                    }
                    
                    val newConnection = com.ai.assistance.operit.data.model.WorkflowNodeConnection(
                        sourceNodeId = sourceId,
                        targetNodeId = targetId
                    )
                    
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections + newConnection,
                        updatedAt = System.currentTimeMillis()
                    )
                    
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_error_create_connection_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 删除连接
     */
    fun deleteConnection(
        workflowId: String,
        connectionId: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedWorkflow = workflow.copy(
                        connections = workflow.connections.filter { it.id != connectionId },
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            loadWorkflows()
                            onSuccess()
                        },
                        onFailure = { error = it.message ?: app.getString(R.string.workflow_error_delete_connection_failed) }
                    )
                },
                onFailure = { error = it.message ?: app.getString(R.string.workflow_load_failed) }
            )
            
            isLoading = false
        }
    }
    
    /**
     * 更新节点位置
     */
    fun updateNodePosition(
        workflowId: String,
        nodeId: String,
        x: Float,
        y: Float,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.getWorkflowById(workflowId).fold(
                onSuccess = { workflow ->
                    workflow ?: return@fold
                    val updatedNodes = workflow.nodes.map { node ->
                        if (node.id == nodeId) {
                            node.position.x = x
                            node.position.y = y
                            node
                        } else {
                            node
                        }
                    }
                    val updatedWorkflow = workflow.copy(
                        nodes = updatedNodes,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateWorkflow(updatedWorkflow).fold(
                        onSuccess = {
                            currentWorkflow = it
                            onSuccess()
                        },
                        onFailure = { /* 静默失败，位置更新不是关键操作 */ }
                    )
                },
                onFailure = { /* 静默失败 */ }
            )
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        error = null
    }
    
    /**
     * Schedule a workflow
     */
    fun scheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val success = repository.scheduleWorkflow(workflowId)
                if (success) {
                    loadWorkflows()
                    onSuccess()
                } else {
                    onFailure(app.getString(R.string.workflow_error_cannot_schedule))
                }
            } catch (e: Exception) {
                onFailure(e.message ?: app.getString(R.string.workflow_error_schedule_failed))
            }
        }
    }
    
    /**
     * Unschedule a workflow
     */
    fun unscheduleWorkflow(workflowId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.unscheduleWorkflow(workflowId)
                loadWorkflows()
                onSuccess()
            } catch (e: Exception) {
                error = e.message ?: app.getString(R.string.workflow_error_cancel_schedule_failed)
            }
        }
    }
    
    /**
     * Check if workflow is scheduled
     */
    fun isWorkflowScheduled(workflowId: String): Boolean {
        return kotlinx.coroutines.runBlocking {
            repository.isWorkflowScheduled(workflowId)
        }
    }
    
    /**
     * Get next execution time for workflow
     */
    fun getNextExecutionTime(workflowId: String, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            val nextTime = repository.getNextExecutionTime(workflowId)
            onResult(nextTime)
        }
    }
}
