package com.ai.assistance.operit.integrations.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for receiving workflow trigger requests from Tasker
 * 
 * This receiver allows Tasker to trigger Operit workflows via broadcasts.
 */
class WorkflowTaskerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowTaskerReceiver"
        const val ACTION_TRIGGER_WORKFLOW = "com.ai.assistance.operit.TRIGGER_WORKFLOW"
        
        /**
         * Creates an intent to trigger workflows based on intent data.
         * This can be used by other parts of the app or external apps to trigger a check.
         */
        fun createTriggerIntent(context: Context, extras: Bundle? = null): Intent {
            return Intent(ACTION_TRIGGER_WORKFLOW).apply {
                setPackage(context.packageName)
                extras?.let { putExtras(it) }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action.isNullOrBlank()) {
            return
        }

        AppLogger.d(TAG, "Received workflow trigger broadcast for action: $action. Checking for matching workflows.")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                // New method to find and trigger workflows based on the intent's content (action, extras, etc.)
                repository.triggerWorkflowsByIntentEvent(intent)
                AppLogger.d(TAG, "Finished processing intent trigger.")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error processing intent trigger for workflows", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * BroadcastReceiver for boot completed event
 * 
 * Re-schedules all enabled workflows after device reboot
 */
class WorkflowBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        AppLogger.d(TAG, "Device booted, rescheduling workflows")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val result = repository.getAllWorkflows()
                
                result.getOrNull()?.forEach { workflow ->
                    if (workflow.enabled) {
                        repository.scheduleWorkflow(workflow.id)
                        AppLogger.d(TAG, "Rescheduled workflow: ${workflow.name}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error rescheduling workflows after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

