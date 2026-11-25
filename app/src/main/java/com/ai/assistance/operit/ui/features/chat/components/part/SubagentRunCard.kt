package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.subagent.AgentJobRegistry
import com.ai.assistance.operit.core.subagent.SubagentDispatchService
import com.ai.assistance.operit.core.subagent.SubtaskDeclaration
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 子代理运行卡片与详情。
 *
 * 两种形态共用同一详情组件：
 * - 运行中：[SubagentRunLiveCard] 渲染在工具请求行，订阅 registry.jobsFlow 实时刷新；
 * - 已完成：[RenderSubagentRun] 渲染信封，数据来自信封属性 + evidence JSON 归档。
 */
object SubagentRunRenderer {

    data class Envelope(
        val runId: String,
        val status: String,
        val reason: String,
        val toolCount: Int,
        val success: Boolean,
        val summaryTruncated: Boolean,
        val evidencePath: String?,
        val body: String,
    )

    private val envelopeRegex = Regex(
        "<subagent>\\s*<final([^>]*)>([\\s\\S]*?)</final>\\s*</subagent>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val attrRegex = Regex("""(\w+)\s*=\s*"([^"]*)"""")

    fun parse(content: String): Envelope? {
        val match = envelopeRegex.find(content) ?: return null
        val attrs = attrRegex.findAll(match.groupValues[1])
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return Envelope(
            runId = attrs["run"].orEmpty(),
            status = attrs["status"].orEmpty(),
            reason = attrs["reason"].orEmpty(),
            toolCount = attrs["tool_count"]?.toIntOrNull() ?: 0,
            success = attrs["success"] == "true",
            summaryTruncated = attrs["summary_truncated"] == "true",
            evidencePath = attrs["evidence_path"]?.takeIf { it.isNotBlank() },
            body = displayBody(match.groupValues[2].trim()),
        )
    }

    /**
     * 历史信封兼容：旧版本在 no_final_declaration 时把子任务的原始终局 JSON 当正文写入，
     * 卡片会直接显示 `{"status":...}`。这里只做展示层提取：剥离思考块后若能解析出
     * summary，就显示 summary；解析失败则原样显示，不改变任何协议数据。
     */
    private fun displayBody(rawBody: String): String {
        val cleaned = SubtaskDeclaration.stripThinkBlocks(rawBody)
        if (!cleaned.startsWith("{")) return rawBody
        return runCatching {
            val summary = JSONObject(cleaned).optString("summary")
            summary.ifBlank { rawBody }
        }.getOrDefault(rawBody)
    }
}

// ============ 配色与状态 ============

private object SubagentColors {
    val success = Color(0xFF2E7D32)
    val warning = Color(0xFFEF6C00)
    val error = Color(0xFFC62828)
    val running = Color(0xFF1565C0)
}

private data class StatusVisual(
    val labelRes: Int,
    val color: Color,
    val icon: ImageVector,
)

private fun statusVisual(status: String): StatusVisual = when (status) {
    "completed" -> StatusVisual(
        R.string.subagent_run_status_completed,
        SubagentColors.success,
        Icons.Default.CheckCircle
    )
    "blocked" -> StatusVisual(R.string.subagent_run_status_blocked, SubagentColors.warning, Icons.Default.Pending)
    "running" -> StatusVisual(R.string.subagent_run_status_running, SubagentColors.running, Icons.Default.Pending)
    "queued" -> StatusVisual(R.string.subagent_run_status_queued, SubagentColors.warning, Icons.Default.Pending)
    else -> StatusVisual(R.string.subagent_run_status_failed, SubagentColors.error, Icons.Default.Error)
}

// ============ 详情数据（两种形态统一） ============

private data class SubagentDetailData(
    val title: String,
    val agentName: String,
    val status: String,
    val task: String,
    val summary: String,
    val summaryTruncated: Boolean,
    val executedToolCalls: Int,
    val toolRecords: List<AgentJobRegistry.ToolRecord>,
    val evidencePath: String?,
    val startedAt: Long?,
    val finishedAt: Long?,
    /** 实际使用的模型显示名（配置 · 模型），运行中与完成后都展示。 */
    val modelLabel: String? = null,
    val contextTokens: Long = 0L,
    val contextLimitTokens: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    /** 详情面板里的停止按钮需要 jobId 才能定位任务；已完成任务为 null。 */
    val jobId: String? = null,
)

// ============ 已完成：信封卡片 ============

@Composable
fun RenderSubagentRun(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color,
    enableDialogs: Boolean
) {
    val envelope = remember(content) { SubagentRunRenderer.parse(content) } ?: return
    var showDetail by remember { mutableStateOf(false) }
    val visual = statusVisual(envelope.status)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enableDialogs) { showDetail = true },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.75.dp, visual.color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(visual.icon, null, tint = visual.color, modifier = Modifier.width(18.dp).height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.subagent_run_card_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(visual.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = visual.color
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "×${envelope.toolCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = envelope.body,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.subagent_run_card_detail_hint),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.55f)
            )
        }
    }

    if (showDetail) {
        SubagentDetailDialog(
            data = SubagentDetailData(
                title = "",
                agentName = "",
                status = envelope.status,
                task = "",
                summary = envelope.body,
                summaryTruncated = envelope.summaryTruncated,
                executedToolCalls = envelope.toolCount,
                toolRecords = emptyList(),
                evidencePath = envelope.evidencePath,
                startedAt = null,
                finishedAt = null,
            ),
            onDismiss = { showDetail = false }
        )
    }
}

// ============ 运行中：工具请求行卡片 ============

/**
 * 阻塞/后台执行中的子代理卡片。
 *
 * 工具请求行在子任务结束前只有请求没有结果；无匹配任务时直接不渲染，
 * 由后续的 tool_result 卡片承接结果，避免留下「执行中 已执行 0/0」的空壳。
 *
 * 匹配口径：只靠 tool_title 不够——两路并行委派用同一个标题时，两张卡片会显示
 * 同一个任务。这里追加档案名与任务正文做联合匹配；任务正文是 delegate 的必填
 * 参数，实际并行的两路几乎不可能完全一致。
 */
@Composable
fun SubagentRunLiveCard(
    toolTitle: String?,
    agentName: String?,
    taskText: String?,
    modifier: Modifier = Modifier,
    textColor: Color,
    enableDialogs: Boolean
) {
    val context = LocalContext.current
    val service = remember { SubagentDispatchService.getInstance(context) }
    val jobs by service.jobsFlow.collectAsState()

    // 匹配本次委派。多路并行时任何「回退到最近任务」都会让每张卡片显示同一个
    // 任务，因此必须全部条件命中：
    //   tool_title 必填，精确匹配；
    //   agent 在模型显式指定时比对档案名快照；
    //   task 正文由函数调用参数带入，两路并行委派的任务描述极少完全相同。
    // 三者都不足以区分时（调用方拿不到参数）才退到最近创建的未结束任务。
    val job = remember(jobs, toolTitle, agentName, taskText) {
        val active = jobs.filter {
            it.status == AgentJobRegistry.Status.RUNNING || it.status == AgentJobRegistry.Status.QUEUED
        }
        if (toolTitle.isNullOrBlank()) {
            active.lastOrNull()
        } else {
            val sameTitle = active.filter { it.toolTitle == toolTitle }
            val sameAgent = if (agentName.isNullOrBlank()) {
                sameTitle
            } else {
                sameTitle.filter { it.profileName == agentName }
            }
            if (taskText.isNullOrBlank()) {
                sameAgent.firstOrNull()
            } else {
                sameAgent.firstOrNull { it.snapshot.task == taskText }
                    ?: sameAgent.firstOrNull()
            }
        }
    }

    // 空壳修复：没有对应任务时不渲染任何内容。
    if (job == null) return

    var showDetail by remember { mutableStateOf(false) }
    val visual = statusVisual(
        if (job.status == AgentJobRegistry.Status.QUEUED) "queued" else "running"
    )
    val resolvedAgentName = agentName?.takeIf { it.isNotBlank() } ?: job.profileName
    val cancellable =
        job.status == AgentJobRegistry.Status.RUNNING || job.status == AgentJobRegistry.Status.QUEUED

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enableDialogs) { showDetail = true },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.75.dp, visual.color.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.width(14.dp).height(14.dp),
                    strokeWidth = 1.5.dp,
                    color = visual.color
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = job.toolTitle.ifBlank { stringResource(R.string.subagent_run_card_title) },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(visual.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = visual.color
                )
            }
            Spacer(Modifier.height(6.dp))
            // 模型：委派时定格的名字快照，直接展示，不在 UI 层回查配置。
            job.modelLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = stringResource(R.string.subagent_run_live_model, label),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = "$resolvedAgentName · " +
                    stringResource(R.string.subagent_run_live_progress, job.executedToolCalls),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            // 上下文窗口：已用 / 上限，有上限时才显示比例。
            if (job.contextTokens > 0L || job.contextLimitTokens > 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (job.contextLimitTokens > 0L) {
                        stringResource(
                            R.string.subagent_run_live_context_with_limit,
                            formatTokenCount(job.contextTokens),
                            formatTokenCount(job.contextLimitTokens),
                        )
                    } else {
                        stringResource(
                            R.string.subagent_run_live_context,
                            formatTokenCount(job.contextTokens),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            // token 用量：累计输入 / 输出。
            if (job.inputTokens > 0L || job.outputTokens > 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.subagent_run_live_tokens,
                        formatTokenCount(job.inputTokens),
                        formatTokenCount(job.outputTokens),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            job.lastToolName?.let { lastTool ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.subagent_run_live_current_tool, lastTool),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.subagent_run_card_detail_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f)
                )
                // 停止按钮：只对未结束的任务显示，点击即取消并让阻塞委派立刻拿到终态。
                if (cancellable && enableDialogs) {
                    TextButton(onClick = { service.requestCancelFromUi(job.jobId) }) {
                        Text(
                            text = stringResource(R.string.subagent_run_stop),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDetail) {
        SubagentLiveDetailDialog(jobId = job.jobId, onDismiss = { showDetail = false })
    }
}

/** 运行中详情：订阅 jobsFlow，任务与工具时间线实时刷新。 */
@Composable
private fun SubagentLiveDetailDialog(jobId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val service = remember { SubagentDispatchService.getInstance(context) }
    val jobs by service.jobsFlow.collectAsState()
    val job = jobs.firstOrNull { it.jobId == jobId } ?: return

    val status = when (job.status) {
        AgentJobRegistry.Status.QUEUED -> "queued"
        AgentJobRegistry.Status.RUNNING -> "running"
        AgentJobRegistry.Status.DONE -> "completed"
        AgentJobRegistry.Status.CANCELLED,
        AgentJobRegistry.Status.FAILED,
        AgentJobRegistry.Status.TIMEOUT,
        AgentJobRegistry.Status.INTERRUPTED -> "failed"
    }

    SubagentDetailDialog(
        data = SubagentDetailData(
            title = job.toolTitle,
            agentName = job.profileName,
            status = status,
            task = job.snapshot.task,
            summary = job.summary.orEmpty(),
            summaryTruncated = false,
            executedToolCalls = job.executedToolCalls,
            toolRecords = job.toolRecords,
            evidencePath = job.outcomePath,
            startedAt = job.startedAt,
            finishedAt = job.finishedAt,
            modelLabel = job.modelLabel,
            contextTokens = job.contextTokens,
            contextLimitTokens = job.contextLimitTokens,
            inputTokens = job.inputTokens,
            outputTokens = job.outputTokens,
            jobId = job.jobId,
        ),
        onDismiss = onDismiss
    )
}

// ============ 统一详情界面 ============

/**
 * 子代理执行详情：头部状态 + 代理 + 耗时 → 任务简报 → 工具时间线 → 结果摘要。
 * 已完成任务从证据归档补充任务简报与账本（运行中数据已由 registry 实时提供）。
 */
@Composable
private fun SubagentDetailDialog(
    data: SubagentDetailData,
    onDismiss: () -> Unit
) {
    var archive by remember(data.evidencePath) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(data.evidencePath) {
        archive = data.evidencePath?.let { path ->
            withContext(Dispatchers.IO) {
                runCatching { JSONObject(File(path).readText()) }
                    .onFailure { AppLogger.e("SubagentDetail", "读取证据归档失败: $path", it) }
                    .getOrNull()
            }
        }
    }

    val visual = statusVisual(data.status)
    val title = archive?.optString("toolTitle")?.takeIf { it.isNotBlank() } ?: data.title
    val agentName = archive?.optString("profileName")?.takeIf { it.isNotBlank() } ?: data.agentName
    val modelLabel = archive?.optString("modelLabel")?.takeIf { it.isNotBlank() }
        ?: data.modelLabel.orEmpty()
    val task = archive?.optString("task")?.takeIf { it.isNotBlank() } ?: data.task
    val summary = archive?.optString("summary")?.takeIf { it.isNotBlank() && it != "null" } ?: data.summary
    // 用量与模型：运行中来自 registry 的实时快照，已完成后来自证据归档里的终值。
    val contextTokens = archive?.optLong("contextTokens", 0L)?.takeIf { it > 0L } ?: data.contextTokens
    val contextLimitTokens =
        archive?.optLong("contextLimitTokens", 0L)?.takeIf { it > 0L } ?: data.contextLimitTokens
    val inputTokens = archive?.optLong("inputTokens", 0L)?.takeIf { it > 0L } ?: data.inputTokens
    val outputTokens = archive?.optLong("outputTokens", 0L)?.takeIf { it > 0L } ?: data.outputTokens
    val archiveTools = archive?.optJSONObject("outcome")?.optJSONArray("tools")
    val records: List<AgentJobRegistry.ToolRecord> = if (data.toolRecords.isNotEmpty()) {
        data.toolRecords
    } else if (archiveTools != null) {
        (0 until archiveTools.length()).mapNotNull { i ->
            val r = archiveTools.optJSONObject(i) ?: return@mapNotNull null
            AgentJobRegistry.ToolRecord(
                id = r.optString("id"),
                toolName = r.optString("toolName"),
                success = r.optBoolean("success"),
                executed = r.optBoolean("executed", true),
                resultTruncated = r.optBoolean("resultTruncated"),
                error = r.optString("error").takeIf { it.isNotBlank() && it != "null" },
                at = 0L,
            )
        }
    } else {
        emptyList()
    }
    val executed = if (data.executedToolCalls > 0) data.executedToolCalls else records.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SmartToy,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(22.dp).height(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.subagent_run_card_title) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(visual.icon, null, tint = visual.color, modifier = Modifier.width(12.dp).height(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(visual.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = visual.color
                        )
                        if (agentName.isNotBlank()) {
                            Text(
                                text = " · $agentName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val duration = durationText(data.startedAt, data.finishedAt)
                        if (duration != null) {
                            Text(
                                text = " · $duration",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 运行参数：模型 / 上下文窗口 / token 用量。运行中实时刷新，完成后保留终值。
                if (modelLabel.isNotBlank()) {
                    SectionLabel(stringResource(R.string.subagent_detail_model))
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (contextTokens > 0L || contextLimitTokens > 0L ||
                    inputTokens > 0L || outputTokens > 0L
                ) {
                    SectionLabel(stringResource(R.string.subagent_detail_usage))
                    Text(
                        text = if (contextLimitTokens > 0L) {
                            stringResource(
                                R.string.subagent_detail_context_with_limit,
                                formatTokenCount(contextTokens),
                                formatTokenCount(contextLimitTokens),
                            )
                        } else {
                            stringResource(
                                R.string.subagent_detail_context,
                                formatTokenCount(contextTokens),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.subagent_detail_tokens,
                            formatTokenCount(inputTokens),
                            formatTokenCount(outputTokens),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (task.isNotBlank()) {
                    SectionLabel(stringResource(R.string.subagent_detail_task))
                    Text(
                        text = task,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                SectionLabel(stringResource(R.string.subagent_detail_tools, executed))
                if (records.isEmpty()) {
                    Text(
                        text = stringResource(R.string.subagent_detail_no_tools_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    records.forEachIndexed { index, record ->
                        ToolTimelineRow(index = index, record = record)
                        if (index != records.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                if (summary.isNotBlank()) {
                    SectionLabel(stringResource(R.string.subagent_detail_summary))
                    Text(text = summary, style = MaterialTheme.typography.bodySmall)
                    if (data.summaryTruncated && data.evidencePath != null) {
                        Text(
                            text = stringResource(R.string.subagent_detail_full_in_evidence),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (data.status == "running" || data.status == "queued") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.subagent_detail_live_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            // 运行中任务的详情面板也提供停止入口，与卡片上的按钮同一实现。
            val runningJobId = data.jobId
            if (runningJobId != null &&
                (data.status == "running" || data.status == "queued")
            ) {
                val context = LocalContext.current
                val service = remember { SubagentDispatchService.getInstance(context) }
                TextButton(onClick = { service.requestCancelFromUi(runningJobId) }) {
                    Text(
                        text = stringResource(R.string.subagent_run_stop),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

private fun durationText(startedAt: Long?, finishedAt: Long?): String? {
    if (startedAt == null) return null
    val end = finishedAt ?: System.currentTimeMillis()
    val seconds = ((end - startedAt) / 1000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m${seconds % 60}s"
}

/** token 数展示：千级用 k，百万级用 M，避免卡片里出现一长串数字。 */
private fun formatTokenCount(tokens: Long): String {
    val value = tokens.coerceAtLeast(0L)
    return when {
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format("%.1fk", value / 1_000.0)
        else -> value.toString()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToolTimelineRow(index: Int, record: AgentJobRegistry.ToolRecord) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Icon(
            imageVector = if (record.success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (record.success) SubagentColors.success else SubagentColors.error,
            modifier = Modifier.width(14.dp).height(14.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.toolName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!record.executed) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.subagent_detail_tool_not_executed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.resultTruncated) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.subagent_detail_tool_truncated),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            record.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
