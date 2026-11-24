package com.ai.assistance.operit.core.subagent

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 子代理后台任务注册表：状态、并发槽位、FIFO 队列与运行句柄。
 *
 * 注册表只在进程内存活。证据与终态通过 evidence JSON 归档落盘，
 * 注册表本身不持久化——重启后旧任务不可 status/cancel，这是有意为之的诚实语义，
 * 不做「重启后假装还能管理」的兜底。
 */
class AgentJobRegistry {

    enum class Status {
        QUEUED,
        RUNNING,
        DONE,
        CANCELLED,
        FAILED,
        TIMEOUT,
        INTERRUPTED,
    }

    /**
     * 子任务单次工具调用的实时记录（运行中详情的时间线数据源）。
     * 与证据归档里的账本同源：来自 SubtaskExecutionState.recordToolResult 的回调 JSON。
     */
    data class ToolRecord(
        val id: String,
        val toolName: String,
        val success: Boolean,
        val executed: Boolean,
        val resultTruncated: Boolean,
        val error: String?,
        val at: Long,
    )

    /** 一次 delegate 的完整入参快照，resume 用它原样重放。 */
    data class TaskSnapshot(
        val task: String,
        val agentName: String?,
        val context: String?,
        val progressReport: String,
    )

    /** 一次子任务使用的模型绑定：(配置 id, 配置内模型索引)。 */
    data class ModelBinding(
        val configId: String,
        val modelIndex: Int,
    )

    data class SubagentJob(
        val jobId: String,
        val parentChatId: String?,
        val profileId: String,
        /** 委派时的名字快照：名单改动不影响历史任务显示。 */
        val profileName: String,
        val toolTitle: String,
        val snapshot: TaskSnapshot,
        val status: Status,
        val createdAt: Long,
        val startedAt: Long? = null,
        val finishedAt: Long? = null,
        val outcomePath: String? = null,
        /** 子任务会话范围 ID（SubtaskExecutionState.toolSessionScopeId），证据归档引用它。 */
        val childSessionId: String? = null,
        /** 入队时前方等待的任务数，给模型回报排队语境。 */
        val queuedBehind: Int = 0,
        val summary: String? = null,
        /** 最近一次执行的工具名与活跃时间，供 status/实时详情查询。 */
        val lastToolName: String? = null,
        val lastActivityAt: Long? = null,
        /** 已完成的工具调用数（运行中实时进度），仅运行中由回调累加。 */
        val executedToolCalls: Int = 0,
        /** 工具调用时间线（运行中实时增长），详情界面直接展示。 */
        val toolRecords: List<ToolRecord> = emptyList(),
        /** 完成后是否向父会话投递（后台委派 true；阻塞委派 false）。 */
        val notifyParentOnFinish: Boolean = true,
        /**
         * 本次子任务实际使用的模型配置 id。
         *
         * 并发上限跟随「子代理自己用的模型配置」，而不是父会话的配置。
         * 该值在 delegate 时解析一次并写入任务，执行体直接复用，
         * 保证「限流用的配置」与「真正调用的模型」是同一个。
         */
        val modelConfigId: String? = null,
        /** 与 [modelConfigId] 配套的模型索引（配置内多模型）。 */
        val modelIndex: Int? = null,
        /**
         * 委派时解析出的模型显示名（配置名 · 模型名）。
         *
         * 运行时快照：之后改档案或改配置都不会改写历史任务显示。
         * 卡片直接显示它，避免 UI 层再异步查配置。
         */
        val modelLabel: String? = null,
        /** 该模型配置的上下文窗口上限（token），用于「已用 / 上限」展示。 */
        val contextLimitTokens: Long = 0L,
        /** 最近一轮请求的上下文窗口占用量（token），随工具轮推进刷新。 */
        val contextTokens: Long = 0L,
        /** 本次子任务累计输入 token。 */
        val inputTokens: Long = 0L,
        /** 本次子任务累计输出 token。 */
        val outputTokens: Long = 0L,
    )

    /** 运行中任务的句柄：协程、终态信箱与纠偏通道。 */
    internal class RunningHandle(
        val deferred: CompletableDeferred<SubagentOutcome>,
        val steerChannel: Channel<String>,
        val job: Job,
    ) {
        @Volatile
        var childSessionId: String? = null
    }

    companion object {
        /**
         * 未配置并发时的默认并行数。
         *
         * 产品语义是「模型配置的 maxConcurrentRequests 为 0（即不限制）时，
         * 子代理并行数按 1 处理」——同一模型不并发打请求，避免把用户配额打爆。
         * 因此这里没有 UNLIMITED 概念，下限恒为 1。
         */
        const val DEFAULT_CONCURRENT = 1

        /** 把配置值归一为实际并行上限：0 或负数按 [DEFAULT_CONCURRENT] 处理。 */
        fun normalizeConcurrency(configured: Int): Int =
            if (configured <= 0) DEFAULT_CONCURRENT else configured
    }

    /**
     * 按模型配置分道的并发上限表：key = 子代理实际使用的模型配置 id。
     *
     * 并行数不是全局值，而是与每个子代理自己的模型绑定。例：子代理 A 绑 3 并发的配置、
     * B 绑 1 并发的配置，则可同时各跑 3 个与 1 个，而不是互相抢占同一个计数。
     * 由 DispatchService 在 delegate 时按 profile 解析后写入。
     */
    private val concurrencyByConfig = ConcurrentHashMap<String, Int>()

    /** 登记某模型配置的并行上限；[configured] 为 0 表示按 1 处理。 */
    fun setConcurrencyLimit(configId: String, configured: Int) {
        concurrencyByConfig[configId] = normalizeConcurrency(configured)
    }

    private fun limitFor(configId: String?): Int {
        // 无配置 id 的任务按默认 1 处理，不做无限制放行。
        val key = configId ?: return DEFAULT_CONCURRENT
        return concurrencyByConfig[key] ?: DEFAULT_CONCURRENT
    }

    private val lock = Any()
    private val jobs = LinkedHashMap<String, SubagentJob>()
    /** 全部未结束任务的句柄（排队与运行共用），终态时移除。 */
    private val handles = LinkedHashMap<String, RunningHandle>()
    /** 当前占用并发槽位的任务。 */
    private val running = LinkedHashMap<String, RunningHandle>()
    private val queue = ArrayDeque<String>()

    /**
     * 任务列表实时流：执行中卡片与详情弹窗的刷新源。
     * 任何状态变化（register/finish/markStarted/recordToolResult/cancel/restore）都发射最新快照。
     */
    private val _jobsFlow = MutableStateFlow<List<SubagentJob>>(emptyList())
    val jobsFlow: StateFlow<List<SubagentJob>> = _jobsFlow.asStateFlow()

    private fun publish() {
        _jobsFlow.value = synchronized(lock) { jobs.values.toList() }
    }

    /**
     * 持久化钩子：注册表状态每次变化后回调，由持有者写盘。
     * INTERRUPTED（进程被杀）只有配合持久化才有真实语义——重启后把非终态任务标记为
     * INTERRUPTED，resume 才能按快照重放。registry 保持纯逻辑，不依赖 Context。
     */
    var persistenceHook: ((List<SubagentJob>) -> Unit)? = null

    private fun persist() {
        persistenceHook?.invoke(synchronized(lock) { jobs.values.toList() })
    }

    fun get(jobId: String): SubagentJob? = synchronized(lock) { jobs[jobId] }

    fun all(): List<SubagentJob> = synchronized(lock) { jobs.values.toList() }

    /** 取任务句柄：排队与运行中的任务都有句柄，终态任务没有。 */
    internal fun findHandle(jobId: String): RunningHandle? = synchronized(lock) { handles[jobId] }

    internal fun isRunning(jobId: String): Boolean = synchronized(lock) { running.containsKey(jobId) }

    fun runningCountForParent(parentChatId: String?): Int = synchronized(lock) {
        running.keys.count { jobId -> jobs[jobId]?.parentChatId == parentChatId }
    }

    /**
     * 登记新任务。该任务所用模型配置的并发槽位已满时进入 FIFO 队列。
     *
     * 槽位按 [modelConfigId] 分道统计——并行数绑定的是子代理自己的模型配置，
     * 不同配置的任务互不挤占。返回的 Boolean 表示是否立即获得槽位。
     */
    fun register(
        parentChatId: String?,
        profileId: String,
        profileName: String,
        toolTitle: String,
        snapshot: TaskSnapshot,
        /** 完成后是否向父会话投递结果（后台委派 true；阻塞委派 false，结果随工具调用返回）。 */
        notifyParentOnFinish: Boolean = true,
        /** 本次任务实际使用的模型配置 id：并发槽位按它分道。 */
        modelConfigId: String? = null,
        modelIndex: Int? = null,
        /** 模型显示名快照：卡片直接展示，不在 UI 层异步回查配置。 */
        modelLabel: String? = null,
        /** 该模型的上下文窗口上限（token）。 */
        contextLimitTokens: Long = 0L,
    ): Pair<SubagentJob, Boolean> = synchronized(lock) {
        val hasSlot = runningCountForConfigLocked(modelConfigId) < limitFor(modelConfigId)
        val jobId = UUID.randomUUID().toString()
        // 句柄在登记时就创建并被排队/运行两条路径共用——阻塞委派即使排队也能直接 await
        // 同一 deferred，出队时不必重建句柄。
        handles[jobId] = RunningHandle(
            deferred = CompletableDeferred(),
            steerChannel = Channel(Channel.UNLIMITED),
            job = Job(),
        )
        val job = SubagentJob(
            jobId = jobId,
            parentChatId = parentChatId,
            profileId = profileId,
            profileName = profileName,
            toolTitle = toolTitle,
            snapshot = snapshot,
            status = if (hasSlot) Status.RUNNING else Status.QUEUED,
            createdAt = System.currentTimeMillis(),
            queuedBehind = queue.count { queuedId ->
                jobs[queuedId]?.modelConfigId == modelConfigId
            },
            notifyParentOnFinish = notifyParentOnFinish,
            modelConfigId = modelConfigId,
            modelIndex = modelIndex,
            modelLabel = modelLabel,
            contextLimitTokens = contextLimitTokens,
        )
        jobs[job.jobId] = job
        if (hasSlot) {
            running[job.jobId] = checkNotNull(handles[job.jobId])
        } else {
            queue.addLast(job.jobId)
        }
        persist()
        publish()
        return job to hasSlot
    }

    /** 某个模型配置当前占用的槽位数（调用方需持锁）。 */
    private fun runningCountForConfigLocked(configId: String?): Int =
        running.keys.count { jobId -> jobs[jobId]?.modelConfigId == configId }

    /** 开始执行时由执行体回填运行信息（句柄在 register 时已创建）。 */
    fun markStarted(jobId: String, childSessionId: String) = synchronized(lock) {
        val job = jobs[jobId] ?: return@synchronized
        jobs[jobId] = job.copy(
            status = Status.RUNNING,
            startedAt = job.startedAt ?: System.currentTimeMillis(),
            childSessionId = childSessionId,
        )
        running[jobId]?.childSessionId = childSessionId
        publish()
    }

    /**
     * 记录一次工具结果：追加时间线、更新计数与最近工具。
     * 运行中详情需要逐条工具记录（不只是计数），数据来自宿主账本回调。
     */
    fun recordToolResult(jobId: String, record: ToolRecord) = synchronized(lock) {
        val job = jobs[jobId] ?: return@synchronized
        jobs[jobId] = job.copy(
            lastToolName = record.toolName,
            lastActivityAt = record.at,
            executedToolCalls = job.executedToolCalls + 1,
            toolRecords = job.toolRecords + record,
        )
        publish()
    }

    /** 进程重启恢复：只回填任务条目（不带运行句柄），非终态任务由恢复方标记 INTERRUPTED。 */
    fun restore(job: SubagentJob) = synchronized(lock) {
        jobs[job.jobId] = job
        publish()
    }

    /**
     * 刷新运行中任务的上下文窗口与 token 用量。
     *
     * 数据来自子任务每轮请求的 onTokensUpdated 与窗口估算回调，
     * 供卡片实时显示「上下文 x / 上限 y」与累计输入输出。
     */
    fun recordUsage(
        jobId: String,
        contextTokens: Long,
        inputTokens: Long,
        outputTokens: Long,
    ) = synchronized(lock) {
        val job = jobs[jobId] ?: return@synchronized
        jobs[jobId] = job.copy(
            contextTokens = contextTokens.coerceAtLeast(0L),
            inputTokens = inputTokens.coerceAtLeast(0L),
            outputTokens = outputTokens.coerceAtLeast(0L),
            lastActivityAt = System.currentTimeMillis(),
        )
        publish()
    }

    /**
     * 任务结束：写终态并释放槽位。返回因释放槽位而可以启动的排队任务。
     */
    fun finish(
        jobId: String,
        status: Status,
        summary: String?,
        outcomePath: String?,
    ): List<SubagentJob> = synchronized(lock) {
        val job = jobs[jobId] ?: return emptyList()
        jobs[jobId] = job.copy(
            status = status,
            finishedAt = System.currentTimeMillis(),
            outcomePath = outcomePath ?: job.outcomePath,
            summary = summary,
        )
        running.remove(jobId)
        handles.remove(jobId)
        publish()

        val started = mutableListOf<SubagentJob>()
        // 槽位按模型配置分道统计，出队时必须逐个检查「该任务所属配置」是否还有空位；
        // 用全局 running.size 判断会让 A 配置的任务挡住 B 配置的出队。
        // 队列保持 FIFO，但允许跳过当前配置已满的任务去启动后面属于其它配置的任务，
        // 否则队首一个满额配置会把所有配置一起堵死。
        var progressed = true
        while (progressed && queue.isNotEmpty()) {
            progressed = false
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                val nextId = iterator.next()
                val next = jobs[nextId] ?: run { iterator.remove(); continue }
                val handle = handles[nextId] ?: run { iterator.remove(); continue }
                if (runningCountForConfigLocked(next.modelConfigId) >= limitFor(next.modelConfigId)) {
                    continue
                }
                iterator.remove()
                jobs[nextId] = next.copy(status = Status.RUNNING, startedAt = System.currentTimeMillis())
                // 复用登记时创建的句柄：阻塞委派正 await 同一 deferred。
                running[nextId] = handle
                started.add(checkNotNull(jobs[nextId]))
                progressed = true
            }
        }
        persist()
        started
    }

    /** 取消运行中任务；排队任务直接摘除。 */
    fun cancel(jobId: String): Boolean = synchronized(lock) {
        val job = jobs[jobId] ?: return false
        when (job.status) {
            Status.QUEUED -> {
                queue.remove(jobId)
                val handle = handles.remove(jobId)
                jobs[jobId] = job.copy(
                    status = Status.CANCELLED,
                    finishedAt = System.currentTimeMillis(),
                )
                // 阻塞委派在排队时也持有同一个 deferred。仅移除句柄而不完成它，
                // wait=true 的工具调用会永久停在 await；这里给出明确的 cancelled 终态。
                handle?.deferred?.complete(
                    SubagentOutcome(
                        status = "failed",
                        reason = "cancelled",
                        summary = "用户已取消子代理任务。",
                        summaryTruncated = false,
                        executedToolCount = 0,
                        success = false,
                        evidencePath = null,
                        childSessionId = "",
                    )
                )
                persist()
                publish()
                true
            }
            Status.RUNNING -> {
                handles[jobId]?.job?.cancel()
                true
            }
            else -> false
        }
    }
}

/** 单次子任务执行的结构化结果，与终态信封一一对应。 */
data class SubagentOutcome(
    val status: String,
    /** 合成后的原因（协议 wireValue 或宿主终态原因）。 */
    val reason: String,
    val summary: String,
    val summaryTruncated: Boolean,
    val executedToolCount: Int,
    val success: Boolean,
    val evidencePath: String?,
    val childSessionId: String,
)
