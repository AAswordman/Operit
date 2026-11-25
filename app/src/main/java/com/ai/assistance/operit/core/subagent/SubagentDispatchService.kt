package com.ai.assistance.operit.core.subagent

import android.content.Context
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.SubagentProfile
import com.ai.assistance.operit.data.model.SubagentRoster
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SubagentProfileManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 子代理调度服务：`subagent:subagent_run` 工具的唯一执行体。
 *
 * 职责：
 * - delegate：解析名单 → 组装子任务历史 → `EnhancedAIService.sendMessage(isSubTask=true)`；
 *   终态由宿主 Outcome 与模型终局声明合成（宿主终态优先）；
 *   产物 JSON 归档到 `filesDir/subagent_runs/<jobId>.json`，信封带 `evidence_path`。
 * - wait=true：挂起等终态，信封即工具结果；wait=false：立即返回 job_id，
 *   结束后向父会话投递信封卡片 + 隐藏用户消息唤醒。
 * - status/cancel/steer/resume：治理动作。
 * - 超时：不设时间护栏，子任务持续到自然结束或用户 cancel。
 *
 * 进度粒度：子任务回调通道按工具结果粒度触发，这里只更新注册表内的最近工具名/活跃时间供
 * status 查询，不向父会话流式注入中间进度；`progress_report` 档位控制完成投递里是否附带逐工具索引。
 */
class SubagentDispatchService private constructor(private val context: Context) {

    companion object {
        private val TAG = "SubagentDispatchService"

        /** 工具保留名：`*:subagent_run` 使 isSubtaskDelegationTarget 天然拒绝子任务再委派。 */
        const val TOOL_NAME = "subagent:subagent_run"

        private const val SUMMARY_INLINE_MAX = 4000

        /** 连续无进展的 status 轮询上限，超过即拒绝并以失败结果要求模型结束回合。 */
        private const val MAX_IDLE_STATUS_POLLS = 3

        @Volatile
        private var INSTANCE: SubagentDispatchService? = null

        fun getInstance(context: Context): SubagentDispatchService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubagentDispatchService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val profileManager = SubagentProfileManager.getInstance(context)
    private val modelConfigManager = ModelConfigManager(context)
    private val functionalConfigManager = FunctionalConfigManager(context)
    private val registry = AgentJobRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val enhancedAIService = EnhancedAIService.getInstance(context)

    /** 任务列表实时流：供聊天渲染层展示执行中卡片与实时详情。 */
    val jobsFlow = registry.jobsFlow

    init {
        // 任务元数据持久化到 evidence 目录旁边（registry.json）。
        // 重启时非终态任务没有进程内句柄可管理，如实标记 INTERRUPTED——
        // 这是 resume 动作的唯一合法入口，不做「重启后假装还能 cancel」的兜底。
        registry.persistenceHook = { jobs -> persistRegistry(jobs) }
        restoreRegistry()
    }

    private fun registryFile(): File = File(File(context.filesDir, "subagent_runs"), "registry.json")

    /**
     * 同一会话的连续无进展 status 轮询计数。
     *
     * 提示词约束是软性的，实测父代理仍会 sleep + status 空转，导致回合永不结束、
     * 排队的完成唤醒发不出去。这里按「状态指纹是否变化」判定是否真的有进展：
     * 连续 [MAX_IDLE_STATUS_POLLS] 次没有新进展就拒绝继续查询，强制模型结束回合。
     */
    private val idleStatusPollsByChatId = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val lastStatusFingerprintByChatId = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun persistRegistry(jobs: List<AgentJobRegistry.SubagentJob>) {
        try {
            val file = registryFile()
            file.parentFile?.mkdirs()
            val array = JSONArray()
            jobs.forEach { job -> array.put(jobToJson(job)) }
            file.writeText(array.toString())
        } catch (error: Exception) {
            AppLogger.e(TAG, "任务注册表持久化失败", error)
        }
    }

    private fun restoreRegistry() {
        try {
            val file = registryFile()
            if (!file.exists()) return
            val array = JSONArray(file.readText())
            (0 until array.length()).forEach { index ->
                val job = jobFromJson(array.optJSONObject(index) ?: return@forEach) ?: return@forEach
                val status = if (
                    job.status == AgentJobRegistry.Status.RUNNING ||
                    job.status == AgentJobRegistry.Status.QUEUED
                ) {
                    AgentJobRegistry.Status.INTERRUPTED
                } else {
                    job.status
                }
                registry.restore(job.copy(status = status))
            }
        } catch (error: Exception) {
            AppLogger.e(TAG, "任务注册表恢复失败", error)
        }
    }

    private fun jobToJson(job: AgentJobRegistry.SubagentJob): JSONObject = JSONObject()
        .put("jobId", job.jobId)
        .put("parentChatId", job.parentChatId ?: JSONObject.NULL)
        .put("profileId", job.profileId)
        .put("profileName", job.profileName)
        .put("toolTitle", job.toolTitle)
        .put("status", job.status.name)
        .put("createdAt", job.createdAt)
        .put("startedAt", job.startedAt ?: JSONObject.NULL)
        .put("finishedAt", job.finishedAt ?: JSONObject.NULL)
        .put("outcomePath", job.outcomePath ?: JSONObject.NULL)
        .put("childSessionId", job.childSessionId ?: JSONObject.NULL)
        .put("summary", job.summary ?: JSONObject.NULL)
        .put("modelConfigId", job.modelConfigId ?: JSONObject.NULL)
        .put("modelIndex", job.modelIndex ?: JSONObject.NULL)
        .put("modelLabel", job.modelLabel ?: JSONObject.NULL)
        .put("contextLimitTokens", job.contextLimitTokens)
        .put("contextTokens", job.contextTokens)
        .put("inputTokens", job.inputTokens)
        .put("outputTokens", job.outputTokens)
        .put(
            "snapshot",
            JSONObject()
                .put("task", job.snapshot.task)
                .put("agentName", job.snapshot.agentName ?: JSONObject.NULL)
                .put("context", job.snapshot.context ?: JSONObject.NULL)
                .put("progressReport", job.snapshot.progressReport)
        )

    private fun jobFromJson(obj: JSONObject): AgentJobRegistry.SubagentJob? {
        if (!obj.has("jobId")) return null
        val snapshotObj = obj.optJSONObject("snapshot") ?: return null
        return AgentJobRegistry.SubagentJob(
            jobId = obj.getString("jobId"),
            parentChatId = obj.optString("parentChatId").takeIf { it.isNotEmpty() },
            profileId = obj.optString("profileId"),
            profileName = obj.optString("profileName"),
            toolTitle = obj.optString("toolTitle"),
            snapshot = AgentJobRegistry.TaskSnapshot(
                task = snapshotObj.optString("task"),
                agentName = snapshotObj.optString("agentName").takeIf { it.isNotEmpty() },
                context = snapshotObj.optString("context").takeIf { it.isNotEmpty() },
                progressReport = snapshotObj.optString("progressReport", "moderate"),
            ),
            status = AgentJobRegistry.Status.entries.firstOrNull { it.name == obj.optString("status") }
                ?: AgentJobRegistry.Status.INTERRUPTED,
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            startedAt = obj.optLong("startedAt", -1L).takeIf { it >= 0 },
            finishedAt = obj.optLong("finishedAt", -1L).takeIf { it >= 0 },
            outcomePath = obj.optString("outcomePath").takeIf { it.isNotEmpty() },
            childSessionId = obj.optString("childSessionId").takeIf { it.isNotEmpty() },
            summary = obj.optString("summary").takeIf { it.isNotEmpty() },
            modelConfigId = obj.optString("modelConfigId").takeIf { it.isNotEmpty() },
            modelIndex = obj.optInt("modelIndex", -1).takeIf { it >= 0 },
            modelLabel = obj.optString("modelLabel").takeIf { it.isNotEmpty() },
            contextLimitTokens = obj.optLong("contextLimitTokens", 0L),
            contextTokens = obj.optLong("contextTokens", 0L),
            inputTokens = obj.optLong("inputTokens", 0L),
            outputTokens = obj.optLong("outputTokens", 0L),
        )
    }

    // ============ 工具动作入口 ============

    /** `subagent:subagent_run` 的执行体；参数校验失败直接返回失败 ToolResult。 */
    suspend fun handleToolCall(tool: AITool): ToolResult = withContext(Dispatchers.Default) {
        fun param(name: String): String? =
            tool.parameters.firstOrNull { it.name == name }?.value?.trim()?.takeIf { it.isNotEmpty() }

        try {
            if (!profileManager.isSubagentsEnabled()) {
                return@withContext failed(tool, "Subagents are disabled in settings")
            }

            // 模型偶尔把 wait=false 写成 background=true。过去未知参数被静默忽略，
            // 结果「后台」委派实际阻塞到任务结束。这里显式拒绝未知参数并指出正确写法，
            // 让模型下一轮自纠，而不是让用户看到一个不符合语义的同步调用。
            val allowedParams = setOf(
                "tool_title", "action", "task", "agent", "context", "wait",
                "progress_report", "job_id", "message", "call_purpose",
            )
            val unknownParams = tool.parameters.map { it.name }.filter { it !in allowedParams }
            if (unknownParams.isNotEmpty()) {
                return@withContext failed(
                    tool,
                    "Unknown parameters: ${unknownParams.joinToString(", ")}. " +
                        "Use wait=false for background execution; allowed parameters: " +
                        allowedParams.joinToString(", "),
                )
            }

            val action = param("action") ?: "delegate"
            val result = when (action) {
                "delegate" -> {
                    val parentChatId = ChatHistoryManager.getInstance(context).currentChatIdFlow.first()
                    // 新一轮委派意味着本会话父代理重新开始工作，只清本会话的轮询计数。
                    // 清全部会话的 map 会连带抹掉其它会话的防轮询进度，让那边的
                    // status 空转重新获得配额。
                    if (parentChatId != null) {
                        idleStatusPollsByChatId.remove(parentChatId)
                        lastStatusFingerprintByChatId.remove(parentChatId)
                    }
                    delegate(
                        parentChatId = parentChatId,
                        toolTitle = param("tool_title"),
                        task = param("task"),
                        agentName = param("agent"),
                        contextText = param("context"),
                        wait = parseWait(param("wait")),
                        progressReport = param("progress_report"),
                    )
                }
                "status" -> statusWithPollGuard(
                    chatId = ChatHistoryManager.getInstance(context).currentChatIdFlow.first(),
                    jobId = param("job_id"),
                )
                "cancel" -> cancel(jobId = param("job_id"))
                "steer" -> steer(jobId = param("job_id"), message = param("message"))
                "resume" -> resume(jobId = param("job_id"))
                else -> return@withContext failed(
                    tool,
                    "Unknown action: $action (expected delegate|status|cancel|steer|resume)",
                )
            }
            ToolResult(toolName = tool.name, success = true, result = StringResultData(result))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            AppLogger.e(TAG, "subagent_run 执行失败", error)
            failed(tool, error.message ?: "subagent_run failed")
        }
    }

    private fun failed(tool: AITool, message: String): ToolResult =
        ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = message)

    /**
     * 解析 `wait` 参数。
     *
     * 语义是「不传即同步执行，显式后台才异步」，所以缺省为 true。模型常写
     * `0`/`no`/`off` 这类等价写法，直接 `toBooleanStrictOrNull() ?: true` 会把它们
     * 静默当成同步，用户看到的是「说了后台却卡住」。
     *
     * 只接受明确列出的真假写法；其它值直接报错而不是回落缺省——把 `"maybe"` 静默
     * 当成同步执行，正是本参数最初要修的那类问题，不能再用一次默认值掩盖过去。
     */
    private fun parseWait(raw: String?): Boolean {
        val text = raw?.trim()?.lowercase()
        if (text.isNullOrEmpty()) return true
        return when (text) {
            "true", "1", "yes", "on", "y" -> true
            "false", "0", "no", "off", "n" -> false
            else -> throw IllegalArgumentException(
                "wait must be a boolean (true/false); got '$raw'. " +
                    "Omit wait for synchronous execution, or pass wait=false for background.",
            )
        }
    }

    // ============ delegate ============

    private suspend fun delegate(
        parentChatId: String?,
        toolTitle: String?,
        task: String?,
        agentName: String?,
        contextText: String?,
        wait: Boolean,
        progressReport: String?,
    ): String {
        require(!task.isNullOrBlank()) { "task is required for delegate" }
        require(!toolTitle.isNullOrBlank()) { "tool_title is required" }

        val profiles = profileManager.profileListFlow.first()
        val profile = SubagentRoster.resolve(profiles, agentName)
            ?: throw IllegalArgumentException(
                "unknown_agent: no subagent named '$agentName'. Valid names: ${
                    profiles.joinToString(", ") { it.name }
                }"
            )

        // 并发上限必须与「这个子代理实际会用的模型」绑定，而不是父会话的模型。
        // 这里先按档案解析出真正的 (configId, modelIndex)，写入任务快照并据此设置该配置的
        // 并发门径；执行体直接复用任务上的绑定，保证限流口径与真实调用完全一致。
        // 配置里 maxConcurrentRequests=0（「不限制」）按 1 处理：同一模型不并发打请求。
        val binding = resolveBindingForProfile(profile)
        val bindingConfig = modelConfigManager.getModelConfigFlow(binding.configId).first()
        registry.setConcurrencyLimit(binding.configId, bindingConfig.maxConcurrentRequests)

        // 模型显示名与上下文上限在委派时定格：卡片直接展示，不改写历史任务。
        // 上下文上限用于卡片上的「已用 / 上限」比例。
        val modelLabel = buildModelLabel(bindingConfig, binding.modelIndex)
        val contextLimitTokens = (bindingConfig.contextLength * 1024f).toLong().coerceAtLeast(0L)

        val progress = when (progressReport?.lowercase()) {
            null, "", "moderate" -> "moderate"
            "none", "frequent" -> progressReport.lowercase()
            else -> throw IllegalArgumentException("progress_report must be none|moderate|frequent")
        }

        val snapshot = AgentJobRegistry.TaskSnapshot(
            task = task,
            agentName = agentName,
            context = contextText,
            progressReport = progress,
        )
        val (job, hasSlot) = registry.register(
            parentChatId = parentChatId,
            profileId = profile.id,
            profileName = profile.name,
            toolTitle = toolTitle,
            snapshot = snapshot,
            // 阻塞委派的结果随工具结果返回，不需要再向父会话投递；后台委派才投递。
            notifyParentOnFinish = !wait,
            modelConfigId = binding.configId,
            modelIndex = binding.modelIndex,
            modelLabel = modelLabel,
            contextLimitTokens = contextLimitTokens,
        )

        if (hasSlot) {
            launchRun(job, notifyParent = !wait)
            if (wait) {
                // 同步等待：终态信封直接作为工具结果返回。
                val handle = checkNotNull(registry.findHandle(job.jobId))
                val outcome = handle.deferred.await()
                return buildEnvelope(outcome, job, includeToolIndex = false)
            }
            val queuedHint = if (job.status == AgentJobRegistry.Status.QUEUED) {
                " (queued behind ${job.queuedBehind})"
            } else {
                ""
            }
            // 实测父代理拿到 job_id 后会 sleep + status 循环轮询，回合长期不结束，
            // 排队的完成唤醒永远发不出去，界面表现为「一直卡在处理中」。
            // 后台委派的语义是「立即返回、完成时自动唤醒」，所以这里明确要求结束本回合，
            // 并把重复轮询定性为错误用法。
            return "running in background. job_id=${job.jobId} agent=${job.profileName}$queuedHint. " +
                "The result will be delivered to this chat automatically when the task finishes. " +
                "END YOUR TURN NOW with a one-line acknowledgement; do NOT sleep, poll, or loop on " +
                "action=status - that keeps the turn open and blocks the completion delivery. " +
                "Use action=status only when the user explicitly asks for progress."
        } else if (wait) {
            // 不指定后台 = 阻塞执行——即使并发已满，也不返回排队文本。
            // 登记时已创建句柄，这里挂起等待槽位释放（finish 出队时自动启动执行体），
            // 再同步等待同一 deferred 的终态。
            val handle = checkNotNull(registry.findHandle(job.jobId))
            val outcome = handle.deferred.await()
            return buildEnvelope(outcome, job, includeToolIndex = false)
        }

        // 排队任务：后台委派遇满时返回排队信息，槽位释放后自动启动。
        return "queued. job_id=${job.jobId} agent=${job.profileName} queued_behind=${job.queuedBehind}. " +
            "Concurrency is at its limit; the task starts automatically when a slot frees up. " +
            "END YOUR TURN NOW; do NOT sleep or poll on action=status."
    }

    /** 启动一次子任务执行体；结束后自动消费队列。 */
    private fun launchRun(job: AgentJobRegistry.SubagentJob, notifyParent: Boolean) {
        val handle = registry.findHandle(job.jobId) ?: return
        // 排队任务不能启动执行体：它的协程由 finish 出队后由本函数重新调度。
        if (!registry.isRunning(job.jobId)) return
        // cancel 与 launch 存在竞态——若 handle.job 已被取消，scope.launch(parent)
        // 创建的协程不会执行（也不会走 catch 分支收敛），任务会卡在 RUNNING。这里显式收敛。
        if (handle.job.isCancelled) {
            val outcome = cancelledOutcome()
            // 阻塞委派可能正 await 同一 deferred；不 complete 会永久挂起。
            handle.deferred.complete(outcome)
            if (notifyParent && job.parentChatId != null) {
                deliverToParent(job.jobId, outcome)
            }
            finishAndDrain(job.jobId, AgentJobRegistry.Status.CANCELLED, outcome.summary, null)
            return
        }
        // 执行协程以 handle.job 为父，cancel 动作才能穿透到挂起的 deferred.await()。
        scope.launch(handle.job) {
            val outcome = try {
                runSubtask(job, handle).also { handle.deferred.complete(it) }
            } catch (error: CancellationException) {
                val cancelled = cancelledOutcome()
                // 取消时 runSubtask 不会走成功分支，必须在这里完成 deferred，
                // 否则 wait=true 的委派会永远停在 await，工具调用无法返回。
                handle.deferred.complete(cancelled)
                if (notifyParent && job.parentChatId != null) {
                    deliverToParent(job.jobId, cancelled)
                }
                finishAndDrain(job.jobId, AgentJobRegistry.Status.CANCELLED, cancelled.summary, null)
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "子任务执行体异常 job=${job.jobId}", error)
                val outcome = SubagentOutcome(
                    status = "failed",
                    reason = "execution_error",
                    summary = error.message ?: "subtask crashed",
                    summaryTruncated = false,
                    executedToolCount = 0,
                    success = false,
                    evidencePath = null,
                    childSessionId = "",
                )
                // 同上：异常路径也必须唤醒阻塞委派，避免工具调用永久等待。
                handle.deferred.complete(outcome)
                finishAndDrain(job.jobId, AgentJobRegistry.Status.FAILED, outcome.summary, null)
                outcome
            }

            // 完成投递：wait=false 或排队启动的任务都向父会话回写结果。
            if (notifyParent && job.parentChatId != null) {
                deliverToParent(job.jobId, outcome)
            }

            // 槽位释放后按 FIFO 启动排队任务。
            finishAndDrain(job.jobId, statusFor(outcome), outcome.summary, outcome.evidencePath)
        }
    }

    /**
     * 结束任务并启动因此获得槽位的排队任务。
     *
     * 所有终态路径（正常完成、取消竞态、协程取消、执行异常）都必须走这里。
     * [AgentJobRegistry.finish] 会把出队任务标记为 RUNNING 并占住槽位，若调用方
     * 丢弃它的返回值，那个任务没有任何执行体在跑：`wait=true` 的委派永久停在
     * `await()`，`wait=false` 的任务永远显示 RUNNING，且槽位不释放会让同配置后续
     * 委派全部排队。把「结束 + 消费队列」收敛到一处，避免某条异常分支又漏掉。
     */
    private fun finishAndDrain(
        jobId: String,
        status: AgentJobRegistry.Status,
        summary: String?,
        outcomePath: String?,
    ) {
        registry.finish(jobId, status, summary, outcomePath).forEach { queued ->
            // 排队任务的 notifyParentOnFinish 在 register 时已确定（阻塞委派=false，
            // 后台=true）。统一传 true 会让排队的阻塞委派在工具结果之外再投递一次。
            launchRun(queued, notifyParent = queued.notifyParentOnFinish)
        }
    }

    /** 取消/异常收尾的统一结果，保证阻塞委派能拿到一个明确终态。 */
    private fun cancelledOutcome(): SubagentOutcome =
        SubagentOutcome(
            status = "failed",
            reason = "cancelled",
            // 用户从卡片点停止时，父代理看到的工具结果要直接说明「用户已取消」，
            // 而不是含糊的「任务未完成」——后者会让父代理误以为需要重试。
            summary = "用户已取消子代理任务。",
            summaryTruncated = false,
            executedToolCount = 0,
            success = false,
            evidencePath = null,
            childSessionId = "",
        )

    private fun statusFor(outcome: SubagentOutcome): AgentJobRegistry.Status = when (outcome.reason) {
        "cancelled" -> AgentJobRegistry.Status.CANCELLED
        "execution_error" -> AgentJobRegistry.Status.FAILED
        else -> AgentJobRegistry.Status.DONE
    }

    // ============ 子任务执行体 ============

    private suspend fun runSubtask(
        job: AgentJobRegistry.SubagentJob,
        handle: AgentJobRegistry.RunningHandle,
    ): SubagentOutcome {
        val profile = profileManager.getProfile(job.profileId)
            ?: throw IllegalStateException("subagent profile '${job.profileId}' no longer exists")
        val snapshot = job.snapshot

        // 模型绑定在 delegate 时已解析并写入任务（并发限流用的就是这个配置）。
        // 执行体直接复用，不再重新解析——否则角色卡/全局配置在排队期间被改动，
        // 会出现「按配置 A 限流、实际用配置 B 执行」的口径不一致。
        val configIdOverride = job.modelConfigId
        val modelIndexOverride = job.modelIndex

        // 子任务语言口径与宿主一致，不按任务文本猜语言。
        val useEnglish = !LocaleUtils.usesChineseContent(context)
        val identityBrief = buildString {
            appendLine(
                if (useEnglish) {
                    """
                    You are running as a delegated subagent inside the host app. This call environment is not your identity; you have no persona and no memory of any parent conversation - you cannot see it and must not assume its contents. Do not ask questions; nobody will answer. Do not delegate further subagent tasks (it will be denied).
                    Your final round MUST be exactly one complete JSON object with no other text:
                    {"status":"completed|blocked|failed","summary":"what was actually done","evidence_ids":["record ids of tools that produced the result"]}
                    Use empty evidence_ids only when no tool was needed. State the summary in English.
                    If the task asks you to output specific text, put that text verbatim inside the summary field. Nothing outside the JSON counts as a deliverable.
                    """.trimIndent()
                } else {
                    """
                    你作为被委派的子代理在宿主应用中运行。调用环境不是你的身份；没有人格、没有父对话的记忆——你看不到父对话，也不要假设其内容。不得提问，没有人会回答。不得再委派其他子代理（会被拒绝）。
                    最后一轮必须输出且只输出一个完整 JSON 对象，不要附带其他文本：
                    {"status":"completed|blocked|failed","summary":"实际完成的内容","evidence_ids":["支撑结果的成功工具记录 ID"]}
                    只有完全没用到工具时才允许空 evidence_ids。summary 用中文书写。
                    如果任务要求你输出某段特定文字，把那段文字完整放进 summary 字段，不要写在 JSON 外面。JSON 之外的任何文字都不算交付物。
                    """.trimIndent()
                }
            )
            if (profile.instructions.isNotBlank()) {
                appendLine()
                appendLine("=== SUBAGENT INSTRUCTIONS ===")
                appendLine(profile.instructions)
            }
        }

        val taskMessage = buildString {
            appendLine("=== TASK ===")
            appendLine(snapshot.task)
            if (!snapshot.context.isNullOrBlank()) {
                appendLine()
                appendLine("=== CONTEXT ===")
                appendLine(snapshot.context)
            }
        }

        var capturedOutcomeJson: String? = null
        val callbacks = object : EnhancedAIService.SendMessageCallbacks {
            override fun onSubtaskToolResult(recordJson: String) {
                val record = runCatching { JSONObject(recordJson) }.getOrNull()
                if (record != null) {
                    registry.recordToolResult(
                        job.jobId,
                        AgentJobRegistry.ToolRecord(
                            id = record.optString("id"),
                            toolName = record.optString("toolName"),
                            success = record.optBoolean("success", false),
                            executed = record.optBoolean("executed", true),
                            resultTruncated = record.optBoolean("resultTruncated", false),
                            error = record.optString("error").takeIf { it.isNotBlank() && it != "null" },
                            at = System.currentTimeMillis(),
                        )
                    )
                }
            }

            override fun onSubtaskCompleted(outcomeJson: String) {
                capturedOutcomeJson = outcomeJson
            }

            override fun onSubtaskUsage(
                contextTokens: Long,
                inputTokens: Long,
                outputTokens: Long,
            ) {
                registry.recordUsage(
                    jobId = job.jobId,
                    contextTokens = contextTokens,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                )
            }
        }

        val stream = enhancedAIService.sendMessage(
            EnhancedAIService.SendMessageOptions().apply {
                message = taskMessage
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = identityBrief))
                chatId = job.parentChatId
                functionType = FunctionType.CHAT
                promptFunctionType = PromptFunctionType.CHAT
                isSubTask = true
                // 子代理没有工具调用次数配额；Int.MAX_VALUE 表示不限，
                // 仍走宿主 SubtaskExecutionState（保留独立会话范围与账本）。
                subtaskMaxToolCalls = Int.MAX_VALUE
                customSystemPromptTemplate = SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE
                enableMemoryAutoUpdate = false
                chatModelConfigIdOverride = configIdOverride
                chatModelIndexOverride = modelIndexOverride
                subtaskSteerProvider = { handle.steerChannel.tryReceive().getOrNull() }
                // runSubtask 的局部 val callbacks 会遮蔽 receiver 的 var 属性
                //（Kotlin 名字解析局部变量优先），必须用 this. 显式指向 SendMessageOptions.callbacks。
                this.callbacks = callbacks
            }
        )
        stream.collect { }

        val outcome = checkNotNull(capturedOutcomeJson) {
            "sendMessage completed without a subtask outcome"
        }
        return synthesizeOutcome(job, outcome)
    }

    /**
     * 解析某个子代理档案实际使用的模型绑定。
     *
     * 并发上限与「子代理自己用的模型配置」绑定，因此必须在 delegate 阶段就解析出
     * 与 runSubtask 完全一致的 (configId, modelIndex)。两处共用本函数，杜绝口径漂移。
     */
    private suspend fun resolveBindingForProfile(
        profile: SubagentProfile
    ): AgentJobRegistry.ModelBinding {
        return when (SubagentProfile.ModelBindingMode.normalize(profile.modelBindingMode)) {
            SubagentProfile.ModelBindingMode.FIXED_CONFIG -> {
                val configId = checkNotNull(profile.modelConfigId) {
                    "subagent '${profile.name}' uses a fixed model config but has no config id"
                }
                AgentJobRegistry.ModelBinding(
                    configId = configId,
                    modelIndex = profile.modelIndex.coerceAtLeast(0),
                )
            }
            // normalize 只会产出上述两个值；else 分支满足穷尽性，语义与 FOLLOW_SESSION 一致。
            else -> {
                val (configIdOverride, modelIndexOverride) = resolveFollowSessionBinding()
                if (configIdOverride != null) {
                    AgentJobRegistry.ModelBinding(
                        configId = configIdOverride,
                        modelIndex = modelIndexOverride ?: 0,
                    )
                } else {
                    // 会话未锁定角色卡配置时，宿主实际用的就是 CHAT 功能映射，
                    // 必须连 modelIndex 一起取。只取 configId 再把 index 补成 0，
                    // 会在同一个配置里有多个模型时（如 gemini,mimo 逗号分隔）执行到
                    // 列表里的第一个模型，而不是会话窗口当前选中的那个。
                    val mapping = functionalConfigManager.getConfigMappingForFunction(FunctionType.CHAT)
                    AgentJobRegistry.ModelBinding(
                        configId = mapping.configId,
                        modelIndex = mapping.modelIndex.coerceAtLeast(0),
                    )
                }
            }
        }
    }

    /**
     * 模型显示名：配置名 · 具体模型名。
     *
     * 配置里的 modelName 可能是逗号分隔的多模型列表，必须按 modelIndex 取到真正在用的那个，
     * 否则卡片会显示列表里的第一个模型（与实际调用不符）。
     */
    private fun buildModelLabel(config: com.ai.assistance.operit.data.model.ModelConfigData, modelIndex: Int): String {
        val validIndex = com.ai.assistance.operit.data.model.getValidModelIndex(
            config.modelName,
            modelIndex,
        )
        val modelName = com.ai.assistance.operit.data.model.getModelByIndex(config.modelName, validIndex)
        return listOfNotNull(
            config.name.takeIf { it.isNotBlank() },
            modelName.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    /** 「跟随会话」：激活角色卡锁定则复用其 (configId, modelIndex)，否则走全局 CHAT 映射（null override）。 */    private suspend fun resolveFollowSessionBinding(): Pair<String?, Int?> {
        val activePrompt = ActivePromptManager.getInstance(context).activePromptFlow.first()
        if (activePrompt !is ActivePrompt.CharacterCard) {
            return Pair(null, null)
        }
        val card = CharacterCardManager.getInstance(context)
            .getCharacterCardFlow(activePrompt.id).first()
        val locked = CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode) ==
            CharacterCardChatModelBindingMode.FIXED_CONFIG &&
            !card.chatModelConfigId.isNullOrBlank()
        return if (locked) {
            Pair(card.chatModelConfigId, card.chatModelIndex.coerceAtLeast(0))
        } else {
            Pair(null, null)
        }
    }

    // ============ 终态合成 ============

    /**
     * 合成最终状态：宿主终态（Outcome.reason）优先于模型声明。
     * 协议：账本存在失败记录时 completed 被降为 blocked/tool_failure；
     * 使用工具的完成声明至少引用一个本次运行的成功证据 ID。
     */
    private fun synthesizeOutcome(
        job: AgentJobRegistry.SubagentJob,
        outcomeJson: String,
    ): SubagentOutcome {
        val outcome = JSONObject(outcomeJson)
        val reason = outcome.optString("reason", "execution_error")
        val finalText = outcome.optString("finalText", "")
        val executedToolCount = outcome.optInt("executedToolCount", 0)
        val tools = outcome.optJSONArray("tools") ?: JSONArray()
        val childSessionId = outcome.optString("toolSessionScopeId", "")
        if (childSessionId.isNotEmpty()) {
            registry.markStarted(job.jobId, childSessionId)
        }

        val hasFailureRecord = (0 until tools.length()).any { index ->
            val record = tools.optJSONObject(index) ?: return@any false
            record.optBoolean("executed", false) && !record.optBoolean("success", true)
        }

        // 协议映射：宿主非 model_finished 终态直接决定结果。
        val hostDriven: Pair<String, String>? = when (reason) {
            "model_finished" -> null
            "tool_budget_exhausted", "token_limit" -> "blocked" to reason
            "delegation_denied", "permission_denied", "cancelled", "execution_error" -> "failed" to reason
            else -> "failed" to "execution_error"
        }

        var status: String
        var reasonLabel: String
        var summary: String

        val declaration = parseFinalDeclaration(finalText)
        if (hostDriven != null) {
            status = hostDriven.first
            reasonLabel = hostDriven.second
            summary = declaration?.optString("summary", "")?.takeIf { it.isNotBlank() }
                ?: finalText.ifBlank { "Subtask ended: $reasonLabel" }
        } else if (declaration != null) {
            status = when (declaration.optString("status", "blocked")) {
                "completed" -> "completed"
                "failed" -> "failed"
                else -> "blocked"
            }
            reasonLabel = if (status == "completed") "model_finished" else "model_declaration"
            summary = declaration.optString("summary", "")
        } else {
            status = "blocked"
            reasonLabel = "no_final_declaration"
            // finalText 开头常是 think 块，直接截断会让摘要变成思考过程；
            // 剥离思考块后再取正文。
            summary = SubtaskDeclaration.stripThinkBlocks(finalText)
        }

        // 宿主终态优先的账本校验：完成声明必须引用本次成功证据。
        if (status == "completed") {
            if (hasFailureRecord) {
                status = "blocked"
                reasonLabel = "tool_failure"
            } else if (executedToolCount > 0) {
                val evidenceIds = declaration?.optJSONArray("evidence_ids") ?: JSONArray()
                val declaredIds = (0 until evidenceIds.length()).mapNotNull { evidenceIds.optString(it) }
                val successIds = (0 until tools.length()).mapNotNull { index ->
                    val record = tools.optJSONObject(index) ?: return@mapNotNull null
                    if (record.optBoolean("executed", false) && record.optBoolean("success", false)) {
                        record.optString("id")
                    } else {
                        null
                    }
                }
                if (declaredIds.none { it in successIds }) {
                    status = "blocked"
                    reasonLabel = "no_valid_evidence"
                }
            }
        }

        summary = summary.trim()
        val summaryTruncated = summary.length > SUMMARY_INLINE_MAX

        val evidencePath = archiveEvidence(job, outcomeJson, status, reasonLabel, summary)
        return SubagentOutcome(
            status = status,
            reason = reasonLabel,
            summary = summary.take(SUMMARY_INLINE_MAX),
            summaryTruncated = summaryTruncated,
            executedToolCount = executedToolCount,
            success = status == "completed",
            evidencePath = evidencePath,
            childSessionId = childSessionId,
        )
    }

    /** 终局声明解析：仅接受完整 JSON 对象或 json 围栏，判定口径复用宿主的严格实现。 */
    private fun parseFinalDeclaration(finalText: String): JSONObject? {
        // 推理模型会在最终 JSON 前输出 <think>...</think>。候选判定本身允许前置隐藏块，
        // 但 stripJsonFence 只认「{」开头；直接用原文会让声明解析失败，
        // 子任务被误判 blocked/no_final_declaration，信封正文退化成原始 JSON。
        // 先剥离思考块再判定与提取，保证「思考 + 裸 JSON」也能识别为 completed。
        val cleaned = SubtaskDeclaration.stripThinkBlocks(finalText)
        if (com.ai.assistance.operit.api.chat.enhance.SubtaskExecutionState
                .isFinalDeclarationCandidate(cleaned)
        ) {
            val body = SubtaskDeclaration.stripJsonFence(cleaned) ?: return null
            return runCatching { JSONObject(body) }.getOrNull()
        }
        // 嵌入围栏提取：实测子模型常先写完整正文、末尾才附 JSON 声明（deepseek 系），
        // 取最后一个 json 围栏解析；账本校验保持严格，正文不属于声明。
        SubtaskDeclaration.findLastJsonFence(cleaned)?.let { fenced ->
            return runCatching { JSONObject(fenced) }.getOrNull()
        }
        // 正文 + 末尾裸 JSON（无围栏）：任务本身要求输出特定文字时，模型会把那段文字写在
        // 前面、协议声明以裸 JSON 附在后面。既不是纯 JSON 也不是围栏，必须单独识别，
        // 否则整个声明被丢弃、子任务被误判 no_final_declaration。
        val bare = SubtaskDeclaration.findLastJsonObject(cleaned) ?: return null
        return runCatching { JSONObject(bare) }.getOrNull()
    }

    /** 证据归档：完整 Outcome + 账本 + 最终判定写入 filesDir/subagent_runs/<jobId>.json。 */
    private fun archiveEvidence(
        job: AgentJobRegistry.SubagentJob,
        outcomeJson: String,
        status: String,
        reason: String,
        summary: String,
    ): String? {
        // 传入的 job 是 launchRun 捕获的入参快照，用量字段在那一刻还是 0；
        // 归档要用注册表里的最新值，否则已完成任务的详情会丢掉模型与 token。
        val live = registry.get(job.jobId) ?: job
        return try {
            val dir = File(context.filesDir, "subagent_runs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${job.jobId}.json")
            val outcome = JSONObject(outcomeJson)
            val document = JSONObject()
                .put("jobId", job.jobId)
                .put("parentChatId", job.parentChatId)
                .put("profileId", job.profileId)
                .put("profileName", job.profileName)
                .put("toolTitle", job.toolTitle)
                .put("task", job.snapshot.task)
                .put("finalStatus", status)
                .put("finalReason", reason)
                .put("summary", summary)
                // 运行参数终值：发给父代理的信封不带这些（它们是给人看的元信息），
                // 但已完成任务的详情面板需要，否则关掉会话后卡片详情会丢掉模型与用量。
                .put("modelLabel", live.modelLabel ?: JSONObject.NULL)
                .put("contextTokens", live.contextTokens)
                .put("contextLimitTokens", live.contextLimitTokens)
                .put("inputTokens", live.inputTokens)
                .put("outputTokens", live.outputTokens)
                .put("outcome", outcome)
            file.writeText(document.toString())
            file.absolutePath
        } catch (error: Exception) {
            // 归档失败：协议要求不假装有证据，返回 null 使信封不带 evidence_path。
            AppLogger.e(TAG, "证据归档失败 job=${job.jobId}", error)
            null
        }
    }

    // ============ 信封 ============

    /**
     * 组装协议信封：`<subagent><final run success tool_count status reason summary_truncated evidence_path>正文</final></subagent>`。
     * 正文为内联摘要（4000 截断），完整正文在证据 JSON 中。
     */
    private fun buildEnvelope(
        outcome: SubagentOutcome,
        job: AgentJobRegistry.SubagentJob,
        includeToolIndex: Boolean,
    ): String {
        val body = buildString {
            append(escapeXml(outcome.summary))
            if (outcome.summaryTruncated) {
                append("\n[summary truncated; full text in evidence archive]")
            }
            if (includeToolIndex && outcome.executedToolCount > 0) {
                append("\ntools_executed=${outcome.executedToolCount} status=${outcome.status} reason=${outcome.reason}")
            }
            // 实测父代理读到 evidence_path 后会立刻 read_file 读证据归档，
            // 多出一轮工具调用与长结果显示。加一行软指令减少无谓读取；
            // 用户明确要求核对原始证据时仍可读。
            append(
                "\n[The summary above is the subtask's final report. Use it directly; " +
                    "do NOT read the evidence archive unless the user explicitly asks to verify raw evidence.]"
            )
        }
        return buildString {
            append("<subagent><final run=\"")
            append(job.jobId)
            append("\" success=\"")
            append(outcome.success)
            append("\" tool_count=\"")
            append(outcome.executedToolCount)
            append("\" status=\"")
            append(outcome.status)
            append("\" reason=\"")
            append(outcome.reason)
            append("\" summary_truncated=\"")
            append(outcome.summaryTruncated)
            append("\"")
            if (outcome.evidencePath != null) {
                append(" evidence_path=\"")
                append(outcome.evidencePath)
                append("\"")
            }
            append(">")
            append(body)
            append("</final></subagent>")
        }
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // ============ 治理动作 ============

    /**
     * status 的轮询闸门：同一会话连续无进展的查询超过上限后直接拒绝。
     *
     * 提示词要求「不要轮询」是软约束，实测模型仍会 sleep+status 空转，
     * 回合不结束会让排队的完成唤醒永远无法派发。这里用「状态指纹」判定进展：
     * 指纹包含每个任务的状态/已执行工具数/最近活跃时间，任一变化都算有进展并清零计数。
     * 连续无进展达到上限时返回失败结果，明确要求模型结束回合。
     */
    private fun statusWithPollGuard(chatId: String?, jobId: String?): String {
        val jobs = if (jobId.isNullOrBlank()) registry.all() else listOfNotNull(registry.get(jobId))
        val fingerprint = jobs.joinToString("|") { job ->
            "${job.jobId}:${job.status}:${job.executedToolCalls}:${job.lastActivityAt ?: 0}"
        }
        val key = chatId ?: "__NO_CHAT__"
        val previous = lastStatusFingerprintByChatId.put(key, fingerprint)
        if (previous != fingerprint) {
            idleStatusPollsByChatId[key] = 0
        } else {
            val next = (idleStatusPollsByChatId[key] ?: 0) + 1
            idleStatusPollsByChatId[key] = next
            if (next >= MAX_IDLE_STATUS_POLLS && jobs.any { it.isActive() }) {
                throw IllegalStateException(
                    "status polling rejected: no progress across $next consecutive queries. " +
                        "The task result is delivered to this chat automatically on completion. " +
                        "END YOUR TURN NOW; do not sleep, do not poll again. " +
                        "Use action=status only when the user explicitly asks for progress.",
                )
            }
        }
        return status(jobId)
    }

    private fun AgentJobRegistry.SubagentJob.isActive(): Boolean =
        status == AgentJobRegistry.Status.RUNNING || status == AgentJobRegistry.Status.QUEUED

    private fun status(jobId: String?): String {
        val jobs = if (jobId.isNullOrBlank()) {
            registry.all()
        } else {
            listOfNotNull(registry.get(jobId))
        }
        if (jobs.isEmpty()) {
            return "no tasks" +
                (jobId?.let { " for job_id=$it" } ?: "") +
                ". Known ids: ${registry.all().joinToString { it.jobId }}"
        }
        val report = jobs.joinToString("\n") { job ->
            val elapsed = job.startedAt?.let { start ->
                ((job.finishedAt ?: System.currentTimeMillis()) - start) / 1000
            }
            buildString {
                append("job_id=${job.jobId} agent=${job.profileName} title=${job.toolTitle} status=${job.status}")
                if (elapsed != null) append(" elapsed=${elapsed}s")
                append(" executed_tools=${job.executedToolCalls}")
                if (job.lastToolName != null) {
                    append(" last_tool=${job.lastToolName}")
                    job.lastActivityAt?.let {
                        append(" (active ${((System.currentTimeMillis() - it) / 1000)}s ago)")
                    }
                }
                if (job.childSessionId != null) append(" child_session=${job.childSessionId}")
                if (job.summary != null) append("\n  summary: ${job.summary}")
                if (job.outcomePath != null) append("\n  evidence: ${job.outcomePath}")
            }
        }
        // 实测父代理会对 status 做 sleep 轮询，回合永不结束，
        // 排队的完成唤醒永远发不出去（用户看到界面一直「处理中」）。
        // 这里在还有未结束任务时直接给出行为约束，让模型看到轮询无效。
        val pending = jobs.count { it.isActive() }
        return if (pending > 0) {
            "$report\n\n$pending task(s) still running. Do NOT poll: the result is delivered " +
                "automatically to this chat when the task finishes. End your turn now; " +
                "only call status again if the user explicitly asks for progress."
        } else {
            report
        }
    }

    /**
     * 供 UI 卡片调用的取消入口。
     *
     * 与工具动作 `action=cancel` 同一实现：运行中的任务经 RunningHandle.job 取消，
     * 阻塞委派立刻拿到 cancelled 终态；排队任务直接摘除并 complete 其 deferred。
     * 返回 true 表示已受理。
     */
    fun requestCancelFromUi(jobId: String): Boolean {
        val job = registry.get(jobId) ?: return false
        if (job.status != AgentJobRegistry.Status.RUNNING &&
            job.status != AgentJobRegistry.Status.QUEUED
        ) {
            return false
        }
        return registry.cancel(jobId)
    }

    private fun cancel(jobId: String?): String {
        require(!jobId.isNullOrBlank()) { "job_id is required for cancel" }
        val handle = registry.findHandle(jobId)
        val cancelled = registry.cancel(jobId)
        require(cancelled) {
            "job $jobId is not cancellable (state=${registry.get(jobId)?.status ?: "unknown job_id"})"
        }
        // 运行中的任务由取消协程收敛为 cancelled 终态并照常投递；这里只回执。
        return if (handle != null) {
            "cancel requested for job $jobId; partial results will be delivered"
        } else {
            "queued job $jobId removed"
        }
    }

    private suspend fun steer(jobId: String?, message: String?): String {
        require(!jobId.isNullOrBlank()) { "job_id is required for steer" }
        require(!message.isNullOrBlank()) { "message is required for steer" }
        val job = registry.get(jobId) ?: throw IllegalArgumentException("unknown job_id: $jobId")
        val handle = registry.findHandle(jobId)
        if (handle == null) {
            // 已结束：如实告知纠偏未送达，状态一并返回。
            return "steer NOT delivered: job $jobId has finished (status=${job.status}). " +
                (job.summary?.let { "Final summary: $it" } ?: "No summary available.")
        }
        handle.steerChannel.send(message)
        return "steer queued for job $jobId; it will be injected at the next tool-round boundary"
    }

    private fun resume(jobId: String?): String {
        require(!jobId.isNullOrBlank()) { "job_id is required for resume" }
        val job = registry.get(jobId) ?: throw IllegalArgumentException("unknown job_id: $jobId")
        require(job.status == AgentJobRegistry.Status.INTERRUPTED) {
            "job $jobId is not resumable (state=${job.status}); only interrupted tasks can resume"
        }
        val (newJob, hasSlot) = registry.register(
            parentChatId = job.parentChatId,
            profileId = job.profileId,
            profileName = job.profileName,
            toolTitle = job.toolTitle,
            snapshot = job.snapshot,
            notifyParentOnFinish = job.notifyParentOnFinish,
            // 重放必须沿用原任务的模型绑定，否则并发分道与模型口径都会漂移到
            // 当前档案/会话状态，和原任务不再是同一件事。
            modelConfigId = job.modelConfigId,
            modelIndex = job.modelIndex,
            // 模型名与上下文上限同样沿袭：否则 resume 后的详情卡片会从
            // 「配置名 · 模型名 + 上下文进度」退化成空白，与实际执行不一致。
            modelLabel = job.modelLabel,
            contextLimitTokens = job.contextLimitTokens,
        )
        // 并发满时 resume 也允许——入队等待，由槽位释放自动启动。
        if (hasSlot) {
            launchRun(newJob, notifyParent = true)
        }
        return "resumed as job_id=${newJob.jobId} (previous child session context is gone; files it created remain on disk)"
    }

    // ============ 完成投递 ============

    /**
     * 任务完成唤醒父会话：以投递文本触发一轮完整的发送路径——消息写入父会话并
     * 驱动父代理对结果做出响应。
     *
     * 直接写 ChatHistoryManager 只落库，不进 UI 内存态也不触发响应，
     * 表现为「任务执行完了没有正确唤醒会话」。改走 [com.ai.assistance.operit.services.ChatServiceCore.wakeChatAfterTurn]：
     * 会话空闲立即发送；父代理还在输出（或另一路子代理刚唤醒过）时排队到当前回合结束，
     * 这样「父代理先结束回合、任务完成后再自行回复」的语义才真正成立。
     * 取消/失败/超时同样投递。
     */
    private fun deliverToParent(jobId: String, outcome: SubagentOutcome) {
        val job = registry.get(jobId) ?: return
        val parentChatId = job.parentChatId ?: return
        scope.launch {
            try {
                val envelope = buildEnvelope(outcome, job, includeToolIndex = true)

                // 两段式投递：
                // 1) 以 AI 消息写入信封——聊天渲染管线把它显示为「子代理任务」卡片，
                //    同时进入上下文供父代理阅读完整结果；必须走 appendMessageToChat，
                //    否则只落库不刷新内存显示列表，卡片要切换会话才出现；
                // 2) 再以隐藏用户消息（hideUserMessage，不显示气泡）驱动父代理响应，
                //    避免粗暴地以用户气泡承载结果。
                val runMessage = ChatMessage(
                    sender = "ai",
                    content = envelope,
                    roleName = job.profileName,
                )
                val core = ChatRuntimeHolder.getInstance(context).getCore(ChatRuntimeSlot.MAIN)
                core.appendMessageToChat(parentChatId, runMessage)

                core.wakeChatAfterTurn(
                    chatId = parentChatId,
                    messageText =
                        "[子代理任务完成通知] job_id=${job.jobId} 已结束（status=${outcome.status}, " +
                            "reason=${outcome.reason}）。结果已作为上方「子代理任务」卡片写入对话，" +
                            "请阅读并向用户简报要点，或按当前剧情继续。",
                    turnOptions = ChatTurnOptions(
                        hideUserMessage = true,
                        notifyReply = false
                    ),
                    preferActiveRoleCard = true
                )
            } catch (error: Exception) {
                AppLogger.e(TAG, "完成投递失败 job=$jobId chat=$parentChatId", error)
            }
        }
    }
}

/** 终局声明 JSON 体的提取：候选判定由 SubtaskExecutionState.isFinalDeclarationCandidate 负责。 */
internal object SubtaskDeclaration {
    /** 从候选文本提取 JSON 体：原生对象或 json 围栏内容。 */
    fun stripJsonFence(body: String): String? {
        if (body.startsWith("{")) return body
        val fence = Regex("^```json[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```$", RegexOption.IGNORE_CASE)
            .find(body) ?: return null
        return fence.groupValues[1]
    }

    /**
     * 从混合文本（正文 + 末尾声明）中提取最后一个 json 围栏的内容。
     * 子模型把回答正文与声明写在同一轮是实测常见行为，
     * 取最后一个围栏可避开正文中偶然出现的引用围栏。
     */
    fun findLastJsonFence(text: String): String? {
        val fence = Regex("```json[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```", RegexOption.IGNORE_CASE)
        return fence.findAll(text).lastOrNull()?.groupValues?.get(1)
    }

    /**
     * 从混合文本（正文 + 末尾裸 JSON）中提取最后一个 JSON 对象。
     *
     * 只接受带 `status` 键的对象：正文里可能夹着普通 JSON 片段（比如任务本身让模型
     * 输出 JSON），用 status 做判别可避免把任务数据误当协议声明。
     * 从文本末尾的 `}` 往前逐个 `{` 尝试，天然覆盖嵌套结构。
     */
    fun findLastJsonObject(text: String): String? {
        val end = text.lastIndexOf('}')
        if (end < 0) return null
        var start = text.lastIndexOf('{', end)
        while (start >= 0) {
            val candidate = text.substring(start, end + 1)
            val parsed = runCatching { org.json.JSONObject(candidate) }.getOrNull()
            if (parsed != null && parsed.has("status")) return candidate
            start = text.lastIndexOf('{', start - 1)
        }
        return null
    }

    /** 剥离 think/thinking 等思考块：摘要提炼时避免把思考过程误当结果正文。 */
    fun stripThinkBlocks(text: String): String {
        return text
            .replace(
                Regex(
                    "<(think|thinking|search|status)(?=[\\s/>])[^>]*>[\\s\\S]*?</\\1>",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex("<(think|thinking|search|status)(?=[\\s/>])[^>]*/>", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()
    }
}
