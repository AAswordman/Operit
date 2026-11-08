package com.ai.assistance.operit.core.workflow.scheduling

import com.ai.assistance.operit.core.workflow.model.TriggerNode
import com.ai.assistance.operit.core.workflow.model.Workflow
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** 计划定义与时间计算不依赖 Android 调度器，便于验证边界和时区行为。 */
internal class WorkflowSchedulePlan
private constructor(
    val triggerId: String,
    val type: String,
    val repeat: Boolean,
    val signature: String,
    val intervalMillis: Long?,
    private val specificTime: ZonedDateTime?,
    private val cron: CronSchedule?,
    private val endTimeMillis: Long?,
    private val zone: ZoneId,
) {
    val periodic: Boolean
        get() = type == "interval" && repeat

    val calendarBased: Boolean
        get() = type != "interval"

    fun expired(nowMillis: Long): Boolean = endTimeMillis?.let { nowMillis > it } == true

    fun nextAfter(nowMillis: Long): Long? {
        val next =
            when (type) {
                "interval" -> Math.addExact(nowMillis, requireNotNull(intervalMillis))
                "cron" -> requireNotNull(cron).nextAfter(nowMillis, zone)
                "specific_time" -> {
                    val start = requireNotNull(specificTime)
                    if (!repeat) start.toInstant().toEpochMilli().takeIf { it > nowMillis }
                    else {
                        var date = Instant.ofEpochMilli(nowMillis).atZone(start.zone).toLocalDate()
                        if (date < start.toLocalDate()) date = start.toLocalDate()
                        var candidate: ZonedDateTime?
                        do {
                            val local = LocalDateTime.of(date, start.toLocalTime())
                            candidate =
                                if (start.zone.rules.getValidOffsets(local).isEmpty()) null
                                else local.atZone(start.zone)
                            date = date.plusDays(1)
                        } while (
                            candidate == null ||
                                candidate.toInstant().toEpochMilli() <= nowMillis ||
                                candidate.toInstant() < start.toInstant()
                        )
                        candidate.toInstant().toEpochMilli()
                    }
                }
                else -> error("Unsupported schedule type")
            }
        return next?.takeUnless { endTimeMillis != null && it > endTimeMillis }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 15 * 60 * 1000L
        private val knownKeys =
            setOf(
                "schedule_type",
                "interval_ms",
                "specific_time",
                "cron_expression",
                "repeat",
                "end_time",
                "enabled",
            )

        fun identity(workflow: Workflow): String? {
            if (!workflow.enabled) return null
            val trigger =
                workflow.nodes.filterIsInstance<TriggerNode>().firstOrNull {
                    it.triggerType == "schedule"
                } ?: return null
            if (trigger.triggerConfig["schedule_type"].isNullOrBlank()) return null
            if (trigger.triggerConfig["enabled"]?.equals("false", ignoreCase = true) == true)
                return null
            val content =
                trigger.id +
                    "\n" +
                    trigger.triggerConfig
                        .filterKeys { it in knownKeys }
                        .toSortedMap()
                        .entries
                        .joinToString("\n") {
                            "${it.key.length}:${it.key}${it.value.length}:${it.value}"
                        }
            return MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun from(workflow: Workflow, zone: ZoneId = ZoneId.systemDefault()): WorkflowSchedulePlan? {
            val signature = identity(workflow) ?: return null
            val trigger =
                workflow.nodes.filterIsInstance<TriggerNode>().first {
                    it.triggerType == "schedule"
                }
            val config = trigger.triggerConfig
            val type = requireNotNull(config["schedule_type"]) { "Schedule type is missing" }
            require(type in setOf("interval", "specific_time", "cron")) {
                "Unsupported schedule type: $type"
            }
            val repeat =
                config["repeat"]?.lowercase(java.util.Locale.ROOT)?.toBooleanStrict()
                    ?: (type != "specific_time")
            val interval =
                if (type == "interval") {
                    requireNotNull(config["interval_ms"]?.toLongOrNull()) { "Invalid interval" }
                        .also {
                            require(it >= MIN_INTERVAL_MILLIS) {
                                "Workflow interval must be at least 15 minutes"
                            }
                        }
                } else null
            val specific =
                if (type == "specific_time")
                    parseDateTime(requireNotNull(config["specific_time"]), zone)
                else null
            val cron =
                if (type == "cron") CronSchedule(requireNotNull(config["cron_expression"]))
                else null
            val end =
                config["end_time"]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseDateTime(it, zone).toInstant().toEpochMilli() }
            return WorkflowSchedulePlan(
                trigger.id,
                type,
                repeat,
                signature,
                interval,
                specific,
                cron,
                end,
                zone,
            )
        }

        fun parseDateTime(value: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
            val text = value.trim()
            if (Regex(".*(?:Z|[+-]\\d{2}:?\\d{2})$").matches(text)) {
                return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toZonedDateTime()
            }
            val local =
                when {
                    Regex("\\d{4}-\\d{2}-\\d{2}").matches(text) ->
                        LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
                    Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?").matches(text) -> {
                        val pattern =
                            if (text.length == 16) "uuuu-MM-dd HH:mm" else "uuuu-MM-dd HH:mm:ss"
                        LocalDateTime.parse(
                            text.replace('T', ' '),
                            DateTimeFormatter.ofPattern(pattern)
                                .withResolverStyle(ResolverStyle.STRICT),
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported date format: $value")
                }
            require(zone.rules.getValidOffsets(local).isNotEmpty()) {
                "Local time does not exist in this time zone"
            }
            return local.atZone(zone)
        }
    }
}

internal class WorkflowScheduleObsoleteException :
    Exception("Scheduled trigger was disabled, removed or changed")
