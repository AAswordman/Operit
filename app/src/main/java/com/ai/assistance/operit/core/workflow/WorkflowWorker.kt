package com.ai.assistance.operit.core.workflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.assistance.operit.core.workflow.scheduling.WorkflowScheduleObsoleteException
import com.ai.assistance.operit.core.workflow.scheduling.WorkflowSchedulePlan
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException

/** 按持久计划执行；运行环境未就绪时重试，业务失败不终止后续重复计划。 */
class WorkflowWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "WorkflowWorker"
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_TRIGGER_NODE_ID = "trigger_node_id"
    }

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID)
        val triggerId = inputData.getString(KEY_TRIGGER_NODE_ID)
        val signature = inputData.getString(WorkflowScheduler.KEY_SCHEDULE_SIGNATURE)
        if (workflowId.isNullOrBlank() || triggerId.isNullOrBlank()) return Result.failure()
        return try {
            val repository = WorkflowRepository(applicationContext)
            val workflow =
                repository.getWorkflowById(workflowId).getOrThrow() ?: return Result.success()
            val plan = WorkflowSchedulePlan.from(workflow) ?: return Result.success()
            if (plan.triggerId != triggerId || (signature != null && signature != plan.signature))
                return Result.success()
            if (
                plan.expired(System.currentTimeMillis()) ||
                    (!plan.repeat &&
                        WorkflowScheduler(applicationContext)
                            .isCompleted(workflowId, plan.signature))
            ) {
                WorkflowScheduler(applicationContext).cancelWorkflow(workflowId)
                return Result.success()
            }
            val scheduledAt = inputData.getLong(WorkflowScheduler.KEY_SCHEDULED_AT, 0L)
            AppLogger.d(
                TAG,
                "Executing workflow=$workflowId, trigger=$triggerId, ${if (plan.periodic) "firstPlannedAt" else "plannedAt"}=$scheduledAt, dispatchedAt=${System.currentTimeMillis()}",
            )
            val result = repository.triggerScheduledWorkflow(workflowId, triggerId, signature)
            if (result.exceptionOrNull() is WorkflowScheduleObsoleteException)
                return Result.success()
            if (result.exceptionOrNull() is WorkflowExecutionRetryableException) {
                AppLogger.w(
                    TAG,
                    "Workflow runtime is not ready; retrying: ${result.exceptionOrNull()?.message}",
                )
                return Result.retry()
            }
            if (!plan.repeat) {
                WorkflowScheduler(applicationContext).recordCompletion(workflowId, plan.signature)
                // 旧版本可能把一次性规则注册成周期任务，业务执行完成后清理旧请求。
                if (signature == null)
                    WorkflowScheduler(applicationContext).cancelWorkflow(workflowId)
            }
            val latest = repository.getWorkflowById(workflowId).getOrThrow()
            val continuing =
                latest?.let {
                    WorkflowScheduler(applicationContext)
                        .continueAfterExecution(it, triggerId, signature)
                } == true
            if (result.isFailure)
                AppLogger.e(TAG, "Workflow execution failed: ${result.exceptionOrNull()?.message}")
            // 重复计划的业务结果已由仓库记录，调度节点成功完成才能释放下一次请求。
            if (result.isSuccess || continuing) Result.success() else Result.failure()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            AppLogger.e(TAG, "Invalid workflow schedule: $workflowId", error)
            Result.failure()
        } catch (error: Exception) {
            AppLogger.e(TAG, "Scheduled workflow infrastructure failed: $workflowId", error)
            Result.retry()
        }
    }
}
