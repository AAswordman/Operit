package com.ai.assistance.operit.core.workflow.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.core.workflow.WorkflowSchedulerInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 日历计划跟随系统时间与时区重算，固定间隔保留原来的已用等待时间。 */
class WorkflowScheduleTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_TIME_CHANGED &&
                intent.action != Intent.ACTION_TIMEZONE_CHANGED
        )
            return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkflowSchedulerInitializer.restore(
                    context.applicationContext,
                    rebaseCalendar = true,
                )
            } finally {
                pending.finish()
            }
        }
    }
}
