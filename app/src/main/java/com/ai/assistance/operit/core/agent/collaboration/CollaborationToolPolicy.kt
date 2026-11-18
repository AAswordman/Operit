package com.ai.assistance.operit.core.agent.collaboration

import android.content.Context

object CollaborationToolPolicy {
    fun visibility(context: Context, chatId: String?, isSubTask: Boolean, isGroupOrchestration: Boolean = false, isPluginSubtask: Boolean = false): Map<String, Boolean> {
        val v2Child = CollaborationCoordinator.getInstance(context).isAgent(chatId)
        return visibilityForCaller(chatId, isSubTask, v2Child, isGroupOrchestration, isPluginSubtask)
    }

    internal fun visibilityForCaller(chatId: String?, isSubTask: Boolean, v2Child: Boolean, isGroupOrchestration: Boolean = false, isPluginSubtask: Boolean = false): Map<String, Boolean> {
        // User chats always use v2, including chats with obsolete saved v1 preferences.
        // Internal v1 tasks invoke the coordinator directly and cannot recursively delegate.
        // Group turns do not own a steering inbox, so wait_agent cannot receive messages there.
        // Plugin subtasks reuse the caller chat ID but do not own its collaboration turn.
        val enabled = !isPluginSubtask && !isGroupOrchestration && (v2Child || (!isSubTask && !chatId.isNullOrBlank()))
        return CollaborationTools.names.associateWith { enabled } +
            (CollaborationTools.LEGACY_TASK_NAME to false)
    }
}
