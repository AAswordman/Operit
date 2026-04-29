package com.ai.assistance.operit.feature.lifecare

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LifeCompanionCareManager {
    private const val TAG = "LifeCompanionCareManager"
    private const val PREFS = "life_companion_care_prefs"
    private const val KEY_LAST_NOTIFY_DATE = "last_notify_date"
    private const val KEY_LAST_MIDNIGHT_FLOW_DATE = "last_midnight_flow_date"
    private const val KEY_PENDING_TEMPLATE = "pending_template"
    private const val CHANNEL_ID = "life_companion_care"
    private const val NOTIFICATION_ID = 73021
    const val ACTION_LIFE_CARE_MIDNIGHT_CHECK = "com.ai.assistance.operit.action.LIFE_CARE_MIDNIGHT_CHECK"
    private const val REQUEST_CODE_MIDNIGHT_CHECK = 73022



    /**

     * 应用冷启动时：调度下一次零点检测 + 若处于凌晨窗口且今日尚未跑过流程，则补跑一次（闹钟被系统推迟 / 进程未启动时的补偿）。

     */

    fun onApplicationWarmStart(context: Context) {
        runCatching {
            scheduleDailyMidnightCheck(context)
            runIfNeededOnLaunch(context)
        }.onFailure {
            AppLogger.e(TAG, "onApplicationWarmStart failed", it)
        }
    }



    /** 由闹钟广播触发（应用可能未在前台）。 */

    fun runAlarmTriggeredMidnightCheck(context: Context) {
        runCatching {
            val today = todayString()
            executeMidnightLifeCareFlow(context, today)
        }.onFailure {
            AppLogger.e(TAG, "runAlarmTriggeredMidnightCheck failed", it)
        }
    }



    /**

     * 漏触发补偿：参考 new 项目在凌晨启动补跑；窗口为当天 0–4 点。

     */

    fun runIfNeededOnLaunch(context: Context) {
        runCatching {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            if (hour !in 0..4) return@runCatching
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_MIDNIGHT_FLOW_DATE, null) == today) return@runCatching
            executeMidnightLifeCareFlow(context, today)
        }.onFailure {
            AppLogger.e(TAG, "runIfNeededOnLaunch failed", it)
        }
    }





    fun consumePendingTemplate(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_PENDING_TEMPLATE, null)?.trim().orEmpty()
        if (message.isBlank()) return null
        prefs.edit().remove(KEY_PENDING_TEMPLATE).apply()
        return message
    }



    fun scheduleDailyMidnightCheck(context: Context) {
        scheduleAlarm(
            context = context,
            pendingIntent = buildMidnightCheckPendingIntent(context),
            triggerAt = nextMidnightCheckMillis(),
            logPrefix = "Daily midnight life-care alarm"
        )
    }

    private fun scheduleAlarm(
        context: Context,
        pendingIntent: PendingIntent,
        triggerAt: Long,
        logPrefix: String
    ) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@runCatching
            val canUseExactAlarm =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
                else true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (canUseExactAlarm) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                } else {
                    if (canUseExactAlarm) {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                }
            } catch (_: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (_: Throwable) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            AppLogger.d(TAG, "$logPrefix scheduled at: $triggerAt")
        }.onFailure {
            AppLogger.e(TAG, "scheduleAlarm failed", it)
        }
    }



    private fun buildMidnightCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LifeCareAlarmReceiver::class.java).apply {
            action = ACTION_LIFE_CARE_MIDNIGHT_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT_CHECK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }



    private fun nextMidnightCheckMillis(): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return trigger.timeInMillis
    }



    private fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())



    /**

     * 与 new 项目 NightFlowRunner 类似：先标记今日已跑流程，再根据是否仍在使用手机等决定是否发通知。

     */

    private fun executeMidnightLifeCareFlow(context: Context, today: String) {
        scheduleDailyMidnightCheck(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_NOTIFY_DATE, null) == today) {
            prefs.edit().putString(KEY_LAST_MIDNIGHT_FLOW_DATE, today).apply()
            return
        }
        prefs.edit().putString(KEY_LAST_MIDNIGHT_FLOW_DATE, today).apply()
        if (!isActiveAfterMidnight(context, System.currentTimeMillis())) {
            prefs.edit().remove(KEY_PENDING_TEMPLATE).apply()
            return
        }
        val careMessage = buildCareMessage(context)
        ensureChannel(context)
        notifyLifeCare(context, careMessage)
        prefs.edit()
            .putString(KEY_LAST_NOTIFY_DATE, today)
            .putString(KEY_PENDING_TEMPLATE, careMessage)
            .apply()
    }



    private fun isActiveAfterMidnight(context: Context, nowMillis: Long): Boolean {
        val usageManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val midnight = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (nowMillis - midnight <= 5 * 60_000L && powerManager?.isInteractive == true) return true
        val stats =
            usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnight, nowMillis).orEmpty()
        return stats.any { stat -> stat.lastTimeUsed >= midnight || stat.totalTimeInForeground > 0L }
    }



    private fun buildCareMessage(context: Context): String {
        val usageManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return "夜深了，我在这儿。要不要和我说说你现在的状态，我陪你慢慢放松一下？"
        val end = System.currentTimeMillis()
        val start = end - 24L * 60L * 60L * 1000L
        val stats = collectTopUsage(context, usageManager, start, end)
        if (stats.isEmpty()) {
            return "夜深了，我在这儿。要不要和我说说你现在的状态，我陪你慢慢放松一下？"
        }
        val top3 = stats.joinToString("；") { (name, ms) ->
            val minutes = (ms / 60000L).coerceAtLeast(1)
            "$name ${minutes}分钟"
        }
        return "我看到你今晚主要用了：$top3。辛苦啦，我在这里听你说。愿意先分享一下，今天最让你有感觉的一件事吗？"
    }

    private fun collectTopUsage(
        context: Context,
        usageManager: UsageStatsManager,
        start: Long,
        end: Long
    ): List<Pair<String, Long>> {
        return usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
            .asSequence()
            .filter { it.totalTimeInForeground > 0L && !it.packageName.isNullOrBlank() }
            .groupBy { it.packageName }
            .mapNotNull { (pkg, items) ->
                val totalMs = items.sumOf { it.totalTimeInForeground }
                if (totalMs <= 0L) return@mapNotNull null
                val appName = runCatching {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(pkg)
                appName to totalMs
            }
            .sortedByDescending { it.second }
            .take(3)
            .toList()
    }



    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name) + " 生活关怀",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }



    private fun notifyLifeCare(context: Context, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_LIFE_CARE_CHAT
            putExtra(MainActivity.EXTRA_LIFE_CARE_MESSAGE, message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("生活关怀")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }



    fun lifeCareCardId(): String = CharacterCardManager.LIFE_CARE_CHARACTER_CARD_ID
}

