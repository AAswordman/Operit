package com.ai.assistance.operit.ui.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import androidx.compose.material3.ColorScheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Define DataStore
private val Context.toolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

/**
 * Permission levels for tool operations
 */
enum class PermissionLevel {
    ALLOW,      // Allow automatically without asking
    ASK,        // Always ask
    LLM,        // Approval model decides; hands over to the user when it cannot decide
    FORBID;     // Never allow

    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> ASK
                "ASK" -> ASK
                "LLM" -> LLM
                "FORBID" -> FORBID
                else -> ASK  // Default to ASK
            }
        }
    }
}

/**
 * Centralized tool permission system that manages both permission storage and checking
 */
class ToolPermissionSystem private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolPermissionSystem"
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // 60 seconds timeout

        // DataStore keys
        private val MASTER_SWITCH = stringPreferencesKey("master_switch")

        // Default permission setting
        private val DEFAULT_MASTER_SWITCH = PermissionLevel.ASK.name

        @Volatile
        private var INSTANCE: ToolPermissionSystem? = null

        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolPermissionSystem(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 工具权限存储：使用 "tool_permission_<tool_name>" 作为key
    private fun toolPermissionKey(toolName: String) = stringPreferencesKey("tool_permission_$toolName")
    
    // Permission request management
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRequestOverlay = PermissionRequestOverlay(context)
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    // 存储当前颜色方案
    private var currentColorScheme: ColorScheme? = null
    
    /**
     * 设置当前使用的颜色方案
     */
    fun setColorScheme(colorScheme: ColorScheme?) {
        this.currentColorScheme = colorScheme
        permissionRequestOverlay.setColorScheme(colorScheme)
    }
    
    // Permission request state flow
    private val _permissionRequestState = MutableStateFlow<Pair<AITool, String>?>(null)
    val permissionRequestState = _permissionRequestState.asStateFlow()
    
    // Permission level flows
    val masterSwitchFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
    }
    
    /**
     * Get permission level flow for a specific tool
     * If no permission is set for the tool, returns ASK as default
     */
    fun getToolPermissionFlow(toolName: String): Flow<PermissionLevel> {
        return context.toolPermissionsDataStore.data.map { preferences ->
            val key = toolPermissionKey(toolName)
            PermissionLevel.fromString(preferences[key] ?: PermissionLevel.ASK.name)
        }
    }
    
    // Registry of operation descriptions by tool name
    private val operationDescriptionRegistry = mutableMapOf<String, (AITool) -> String>()
    
    /**
     * Register a description generator for a tool
     */
    fun registerOperationDescription(toolName: String, descriptionGenerator: (AITool) -> String) {
        operationDescriptionRegistry[toolName] = descriptionGenerator
    }
    
    /**
     * Save permission level settings
     */
    suspend fun saveMasterSwitch(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = level.name
        }
    }
    
    /**
     * Save permission level for a specific tool
     */
    suspend fun saveToolPermission(toolName: String, level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            val key = toolPermissionKey(toolName)
            preferences[key] = level.name
        }
    }
    
    suspend fun clearToolPermission(toolName: String) {
        context.toolPermissionsDataStore.edit { preferences ->
            val key = toolPermissionKey(toolName)
            preferences.remove(key)
        }
    }
    
    /**
     * Save permission levels for multiple tools at once
     */
    suspend fun saveToolPermissions(toolPermissions: Map<String, PermissionLevel>) {
        context.toolPermissionsDataStore.edit { preferences ->
            toolPermissions.forEach { (toolName, level) ->
                val key = toolPermissionKey(toolName)
                preferences[key] = level.name
            }
        }
    }
    
    /**
     * Get permission level for a specific tool (synchronous, for one-time reads)
     * If no permission is set for the tool, returns ASK as default
     */
    suspend fun getToolPermission(toolName: String): PermissionLevel {
        val preferences = context.toolPermissionsDataStore.data.first()
        val key = toolPermissionKey(toolName)
        return PermissionLevel.fromString(preferences[key] ?: PermissionLevel.ASK.name)
    }
    
    suspend fun getToolPermissionOverride(toolName: String): PermissionLevel? {
        val preferences = context.toolPermissionsDataStore.data.first()
        val key = toolPermissionKey(toolName)
        val stored = preferences[key]
        return stored?.let { PermissionLevel.fromString(it) }
    }
    
    /**
     * Get human-readable description of an operation
     */
    fun getOperationDescription(tool: AITool): String {
        return operationDescriptionRegistry[tool.name]?.invoke(tool) ?: context.getString(R.string.tool_permission_operation, tool.name)
    }
    
    /**
     * Check if a tool is allowed to execute.
     * 供同步 / 无父会话上下文路径使用 (如 CLI 代理), 只返回是否放行。
     */
    suspend fun checkToolPermission(tool: AITool): Boolean {
        return when (checkToolPermissionDetailed(tool)) {
            ToolPermissionDecision.Allowed -> true
            is ToolPermissionDecision.Denied -> false
        }
    }

    /**
     * 带完整上下文的结构化权限检查:
     * - ALLOW / FORBID 直接由设置决定
     * - ASK 弹出用户确认
     * - LLM 由审批模型基于父会话证据裁决, 并受本轮熔断器约束
     */
    internal suspend fun checkToolPermissionDetailed(
        tool: AITool,
        reviewContext: ToolPermissionReviewContext? = null,
        circuitBreaker: PermissionReviewCircuitBreaker? = null,
    ): ToolPermissionDecision {
        AppLogger.d(TAG, "Starting permission check: ${tool.name}")

        circuitBreaker?.rejectionAfterLock()?.let { rejection ->
            AppLogger.w(TAG, "Permission review lock denied ${tool.name}: ${rejection.reason}")
            return permissionDeniedByCircuitBreaker(
                reason = rejection.reason,
                reviewLocked = true,
            )
        }

        val preferences = context.toolPermissionsDataStore.data.first()
        val masterSwitch = PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
        val key = toolPermissionKey(tool.name)
        val overrideLevel = preferences[key]?.let { PermissionLevel.fromString(it) }

        val permissionLevel = overrideLevel ?: masterSwitch

        return when (permissionLevel) {
            PermissionLevel.ALLOW -> ToolPermissionDecision.Allowed
            PermissionLevel.ASK ->
                if (requestPermission(tool)) {
                    circuitBreaker?.recordApproval()
                    ToolPermissionDecision.Allowed
                } else {
                    permissionDeniedByUser()
                }
            PermissionLevel.FORBID -> permissionDeniedBySettings()
            PermissionLevel.LLM -> requestLlmApprovalDetailed(tool, reviewContext, circuitBreaker)
        }
    }

    /**
     * 调用审批模型决定是否允许工具调用。
     * 审批请求由 ToolApprovalReviewPolicy 基于父会话证据构建;
     * 同动作重复尝试或本回合拒绝过多由熔断器在请求前拦截。
     * ASK 与 null (API 错误 / 输出不符合约定) 按产品设计都转交用户手动确认。
     */
    private suspend fun requestLlmApprovalDetailed(
        tool: AITool,
        reviewContext: ToolPermissionReviewContext?,
        circuitBreaker: PermissionReviewCircuitBreaker?,
    ): ToolPermissionDecision {
        val effectiveContext =
            reviewContext ?: ToolPermissionReviewContext(
                conversationHistory = emptyList(),
                liveAssistantContent = "",
                workspacePath = null,
                workspaceEnv = null,
                conversationLabel = null,
            )
        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val request =
            ToolApprovalReviewPolicy.buildRequest(
                tool = tool,
                reviewContext = effectiveContext,
                useEnglish = useEnglish,
                priorDenials = circuitBreaker?.denialHistory().orEmpty(),
            )

        circuitBreaker?.rejectionBeforeReview(request.actionFingerprint)?.let { rejection ->
            AppLogger.w(
                TAG,
                "Permission review circuit breaker denied ${tool.name}: ${rejection.reason}",
            )
            return permissionDeniedByCircuitBreaker(
                reason = rejection.reason,
                reviewLocked = rejection.reviewLocked,
            )
        }

        val response = sendApprovalReviewRequest(request)
        val reviewDecision =
            response?.let {
                ToolApprovalReviewPolicy.parseAndEnforce(
                    response = it,
                    expectedReviewId = request.reviewId,
                    hasUserAuthorizationEvidence = effectiveContext.hasUserAuthorizationEvidence,
                )
            }

        return when (reviewDecision?.outcome) {
            LlmApprovalReviewOutcome.APPROVE -> {
                AppLogger.d(TAG, "LLM approval approved ${tool.name}: ${reviewDecision.reason}")
                circuitBreaker?.recordApproval()
                ToolPermissionDecision.Allowed
            }

            LlmApprovalReviewOutcome.DENY -> {
                AppLogger.w(TAG, "LLM approval denied ${tool.name}: ${reviewDecision.reason}")
                val reviewLocked =
                    circuitBreaker?.recordAutomaticDenial(
                        actionFingerprint = request.actionFingerprint,
                        reason = reviewDecision.reason,
                    ) == true
                permissionDeniedByAutomaticReview(
                    reason = reviewDecision.reason,
                    reviewLocked = reviewLocked,
                )
            }

            // ASK: 模型明确表示无法自主判断; null: API 错误或输出不符合约定
            LlmApprovalReviewOutcome.ASK, null -> {
                if (requestPermission(tool)) {
                    circuitBreaker?.recordApproval()
                    ToolPermissionDecision.Allowed
                } else {
                    permissionDeniedByUser()
                }
            }
        }
    }

    /**
     * 向审批模型发送审批请求并返回原始输出; 失败时返回 null 交由调用方转人工。
     */
    private suspend fun sendApprovalReviewRequest(request: ToolApprovalReviewRequest): String? {
        return try {
            val service = EnhancedAIService.getAIServiceForFunction(context, FunctionType.TOOL_APPROVAL)
            val functionalConfigManager = FunctionalConfigManager(context)
            functionalConfigManager.initializeIfNeeded()
            val modelConfigManager = ModelConfigManager(context)
            val mapping = functionalConfigManager.getConfigMappingForFunction(FunctionType.TOOL_APPROVAL)
            val modelParameters = modelConfigManager.getModelParametersForConfig(mapping.configId)

            val sb = StringBuilder()
            service.sendMessage(
                context = context,
                chatHistory = request.messages,
                modelParameters = modelParameters,
                enableThinking = false,
                stream = false,
                availableTools = null
            ).collect { chunk -> sb.append(chunk) }

            sb.toString().trim()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "LLM approval failed for review ${request.reviewId}, handing over to user", e)
            null
        }
    }

    /**
     * Request permission from the user to execute a tool
     */
    private suspend fun requestPermission(tool: AITool): Boolean {
        // Get operation description
        val operationDescription = getOperationDescription(tool)
        
        AppLogger.d(TAG, "Requesting permission: ${tool.name}")
        
        // Clear existing request
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // Set up new request
        val requestInfo = Pair(tool, operationDescription)
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        AppLogger.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // Set callback
                currentPermissionCallback = { result ->
                    AppLogger.d(TAG, "Permission result received: $result for ${tool.name}")
                    // Clean up state
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    
                    // Handle result
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.ALWAYS_ALLOW -> {
                            // Save the permission and resume
                            tool.let {
                                val toolScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                toolScope.launch {
                                    saveToolPermission(it.name, PermissionLevel.ALLOW)
                                }
                            }
                            continuation.resume(true)
                        }
                    }
                }
                
                // Start permission request on main thread
                mainHandler.post {
                    // Use overlay to show permission request
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        AppLogger.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(PermissionRequestResult.DENY)
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            // Timeout handling
            AppLogger.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            false
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * Get current permission request info
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * Check if there is an active permission request
     */
    fun hasActivePermissionRequest(): Boolean {
        return permissionRequestInfo != null && currentPermissionCallback != null
    }
    
    /**
     * Refresh permission request state
     */
    fun refreshPermissionRequestState(): Boolean {
        return hasActivePermissionRequest()
    }
}
