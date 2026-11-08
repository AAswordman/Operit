package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.core.workflow.scheduling.WorkflowSchedulePlan
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 启动恢复只补缺失任务，避免系统唤醒进程时重置或取消原有计划。 */
object WorkflowSchedulerInitializer {
    private const val TAG = "WorkflowSchedulerInit"
    private val restoreMutex = Mutex()

    fun initialize(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { restore(context.applicationContext) }
    }

    suspend fun restore(context: Context, rebaseCalendar: Boolean = false) {
        restoreMutex.withLock {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val workflows = repository.getAllWorkflows().getOrThrow()
                for (workflow in workflows) {
                    try {
                        val plan = WorkflowSchedulePlan.from(workflow)
                        val replace = rebaseCalendar && plan?.calendarBased == true
                        repository.scheduleWorkflow(
                            workflow.id,
                            replaceExisting = replace,
                            preserveRunning = replace,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e(TAG, "Cannot restore schedule: ${workflow.id}", error)
                    }
                }
                AppLogger.d(TAG, "Schedule restoration complete, calendarRebase=$rebaseCalendar")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "Cannot restore workflow schedules", error)
            }
        }
    }
}
