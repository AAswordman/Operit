package com.ai.assistance.operit.core.workflow

import android.content.Context
import androidx.work.*
import com.ai.assistance.operit.core.workflow.scheduling.WorkflowSchedulePlan
import com.ai.assistance.operit.core.workflow.model.Workflow
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** WorkManager 保留持久任务；日历计划按下一次匹配时刻逐次排队。 */
class WorkflowScheduler(context: Context) {
    companion object {
        private const val TAG = "WorkflowScheduler"
        private const val WORK_NAME_PREFIX = "workflow_"
        private const val REVISION_TAG = "workflow_schedule_v2"
        private const val SIGNATURE_TAG_PREFIX = "workflow_plan:"
        private val scheduleMutex = Mutex()
        const val SCHEDULE_TYPE_INTERVAL = "interval"
        const val SCHEDULE_TYPE_SPECIFIC_TIME = "specific_time"
        const val SCHEDULE_TYPE_CRON = "cron"
        const val CONFIG_SCHEDULE_TYPE = "schedule_type"
        const val CONFIG_INTERVAL_MS = "interval_ms"
        const val CONFIG_SPECIFIC_TIME = "specific_time"
        const val CONFIG_CRON_EXPRESSION = "cron_expression"
        const val CONFIG_REPEAT = "repeat"
        const val CONFIG_END_TIME = "end_time"
        const val CONFIG_ENABLED = "enabled"
        const val KEY_TRIGGER_NODE_ID = "trigger_node_id"
        const val KEY_SCHEDULE_SIGNATURE = "schedule_signature"
        const val KEY_SCHEDULED_AT = "scheduled_at"
    }

    private val completionState =
        context.applicationContext.getSharedPreferences(
            "workflow_schedule_completion",
            Context.MODE_PRIVATE,
        )
    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    suspend fun scheduleWorkflow(
        workflow: Workflow,
        replaceExisting: Boolean = false,
        preserveRunning: Boolean = false,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                scheduleMutex.withLock {
                    val plan = WorkflowSchedulePlan.from(workflow)
                    if (plan == null) {
                        workManager.cancelUniqueWork(getWorkName(workflow.id)).await()
                        return@withLock false
                    }
                    val infos =
                        workManager.getWorkInfosForUniqueWork(getWorkName(workflow.id)).await()
                    val active = infos.filterNot { it.state.isFinished }
                    // 冷启动、重启和时区变化不取消正在执行的任务；旧任务完成后再迁移。
                    if (
                        active.any {
                            it.state == WorkInfo.State.RUNNING &&
                                (REVISION_TAG !in it.tags ||
                                    SIGNATURE_TAG_PREFIX + plan.signature in it.tags)
                        } && (!replaceExisting || preserveRunning)
                    )
                        return@withLock true
                    if (plan.expired(System.currentTimeMillis())) {
                        workManager.cancelUniqueWork(getWorkName(workflow.id)).await()
                        return@withLock false
                    }
                    if (
                        !replaceExisting &&
                            active.any { SIGNATURE_TAG_PREFIX + plan.signature in it.tags }
                    )
                        return@withLock true
                    if (
                        (!replaceExisting || preserveRunning) &&
                            !plan.repeat &&
                            (isCompleted(workflow.id, plan.signature) ||
                                infos.any {
                                    it.state == WorkInfo.State.SUCCEEDED &&
                                        SIGNATURE_TAG_PREFIX + plan.signature in it.tags
                                })
                    ) {
                        workManager.cancelUniqueWork(getWorkName(workflow.id)).await()
                        return@withLock false
                    }
                    val now = System.currentTimeMillis()
                    val legacyNext =
                        if (!replaceExisting && plan.periodic)
                            active
                                .filter { REVISION_TAG !in it.tags && it.periodicityInfo != null }
                                .minOfOrNull { it.nextScheduleTimeMillis }
                                ?.takeIf { it > 0 && it < Long.MAX_VALUE }
                        else null
                    val next =
                        legacyNext
                            ?: plan.nextAfter(now)
                            ?: if (
                                preserveRunning &&
                                    plan.type == SCHEDULE_TYPE_SPECIFIC_TIME &&
                                    !plan.repeat &&
                                    active.any { SIGNATURE_TAG_PREFIX + plan.signature in it.tags }
                            )
                                now
                            else {
                                workManager.cancelUniqueWork(getWorkName(workflow.id)).await()
                                return@withLock false
                            }
                    if (replaceExisting && !preserveRunning)
                        check(
                            completionState
                                .edit()
                                .remove("${workflow.id}:${plan.signature}")
                                .commit()
                        )
                    if (plan.periodic) {
                        val request =
                            PeriodicWorkRequestBuilder<WorkflowWorker>(
                                    requireNotNull(plan.intervalMillis),
                                    TimeUnit.MILLISECONDS,
                                )
                                .setInitialDelay(
                                    (next - now).coerceAtLeast(0),
                                    TimeUnit.MILLISECONDS,
                                )
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                                .setInputData(input(workflow.id, plan, next))
                                .addTag(workflow.id)
                                .addTag(REVISION_TAG)
                                .addTag(SIGNATURE_TAG_PREFIX + plan.signature)
                                .build()
                        workManager
                            .enqueueUniquePeriodicWork(
                                getWorkName(workflow.id),
                                if (active.isEmpty()) ExistingPeriodicWorkPolicy.KEEP
                                else ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                                request,
                            )
                            .await()
                    } else {
                        workManager
                            .enqueueUniqueWork(
                                getWorkName(workflow.id),
                                if (active.isEmpty()) ExistingWorkPolicy.KEEP
                                else ExistingWorkPolicy.REPLACE,
                                oneTime(workflow.id, plan, next),
                            )
                            .await()
                    }
                    AppLogger.d(
                        TAG,
                        "Scheduled workflow=${workflow.id}, trigger=${plan.triggerId}, type=${plan.type}, plannedAt=$next",
                    )
                    true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is IllegalArgumentException)
                    workManager.cancelUniqueWork(getWorkName(workflow.id)).await()
                AppLogger.e(TAG, "Cannot schedule workflow: ${workflow.id}", error)
                false
            }
        }

    internal suspend fun continueAfterExecution(
        workflow: Workflow,
        triggerId: String,
        signature: String?,
    ): Boolean {
        val plan = WorkflowSchedulePlan.from(workflow) ?: return false
        if (
            plan.triggerId != triggerId ||
                (signature != null && signature != plan.signature) ||
                plan.expired(System.currentTimeMillis())
        )
            return false
        if (!plan.repeat) return false
        // 已发布的 WorkManager 请求没有签名；仅在当前执行结束后替换为新请求。
        if (signature == null) {
            if (plan.nextAfter(System.currentTimeMillis()) == null) return false
            check(scheduleWorkflow(workflow, replaceExisting = true)) {
                "Cannot migrate workflow schedule"
            }
            return true
        }
        if (plan.periodic) return true
        scheduleMutex.withLock {
            val active =
                workManager.getWorkInfosForUniqueWork(getWorkName(workflow.id)).await().filterNot {
                    it.state.isFinished
                }
            if (active.none { SIGNATURE_TAG_PREFIX + plan.signature in it.tags }) return@withLock
            val next = plan.nextAfter(System.currentTimeMillis()) ?: return@withLock
            workManager
                .enqueueUniqueWork(
                    getWorkName(workflow.id),
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    oneTime(workflow.id, plan, next),
                )
                .await()
            AppLogger.d(TAG, "Next calendar occurrence workflow=${workflow.id}, plannedAt=$next")
        }
        return true
    }

    internal fun isCompleted(workflowId: String, signature: String): Boolean =
        completionState.getBoolean("$workflowId:$signature", false)

    internal fun recordCompletion(workflowId: String, signature: String) {
        // WorkManager 会清理已完成记录，一次性间隔任务需独立记住消费状态，防止冷启动再次运行。
        check(completionState.edit().putBoolean("$workflowId:$signature", true).commit())
    }

    suspend fun cancelWorkflow(workflowId: String) {
        scheduleMutex.withLock { workManager.cancelUniqueWork(getWorkName(workflowId)).await() }
    }

    suspend fun isWorkflowScheduled(workflowId: String): Boolean =
        withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork(getWorkName(workflowId)).await().any {
                !it.state.isFinished
            }
        }

    suspend fun getNextExecutionTime(workflow: Workflow): Long? =
        withContext(Dispatchers.IO) {
            if (WorkflowSchedulePlan.identity(workflow) == null) return@withContext null
            // 展示已登记请求的时间，不在每次刷新时把间隔重新加到现在。
            workManager
                .getWorkInfosForUniqueWork(getWorkName(workflow.id))
                .await()
                .filter {
                    it.state == WorkInfo.State.ENQUEUED &&
                        (REVISION_TAG !in it.tags ||
                            SIGNATURE_TAG_PREFIX + WorkflowSchedulePlan.identity(workflow) in
                                it.tags)
                }
                .map { it.nextScheduleTimeMillis }
                .filter { it > 0 && it < Long.MAX_VALUE }
                .minOrNull()
        }

    private fun input(workflowId: String, plan: WorkflowSchedulePlan, next: Long) =
        workDataOf(
            WorkflowWorker.KEY_WORKFLOW_ID to workflowId,
            KEY_TRIGGER_NODE_ID to plan.triggerId,
            KEY_SCHEDULE_SIGNATURE to plan.signature,
            KEY_SCHEDULED_AT to next,
        )

    private fun oneTime(
        workflowId: String,
        plan: WorkflowSchedulePlan,
        next: Long,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<WorkflowWorker>()
            .setInitialDelay(
                (next - System.currentTimeMillis()).coerceAtLeast(0),
                TimeUnit.MILLISECONDS,
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setInputData(input(workflowId, plan, next))
            .addTag(workflowId)
            .addTag(REVISION_TAG)
            .addTag(SIGNATURE_TAG_PREFIX + plan.signature)
            .build()

    private fun getWorkName(workflowId: String) = "$WORK_NAME_PREFIX$workflowId"
}
