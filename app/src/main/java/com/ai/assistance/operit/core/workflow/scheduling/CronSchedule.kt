package com.ai.assistance.operit.core.workflow.scheduling

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** 五段 Cron 按当地日历匹配；步进从字段边界开始，不从保存时刻累加。 */
internal class CronSchedule(expression: String) {
    private val fields = expression.trim().split(Regex("\\s+"))
    private val minutes: Set<Int>
    private val hours: Set<Int>
    private val days: Set<Int>
    private val months: Set<Int>
    private val weekdays: Set<Int>
    private val anyDay: Boolean
    private val anyWeekday: Boolean

    init {
        require(fields.size == 5) { "Cron requires exactly five fields" }
        minutes = parseField(fields[0], 0, 59)
        hours = parseField(fields[1], 0, 23)
        days = parseField(fields[2], 1, 31)
        months =
            parseField(
                fields[3],
                1,
                12,
                mapOf(
                    "JAN" to 1,
                    "FEB" to 2,
                    "MAR" to 3,
                    "APR" to 4,
                    "MAY" to 5,
                    "JUN" to 6,
                    "JUL" to 7,
                    "AUG" to 8,
                    "SEP" to 9,
                    "OCT" to 10,
                    "NOV" to 11,
                    "DEC" to 12,
                ),
            )
        weekdays =
            parseField(
                    fields[4],
                    0,
                    7,
                    mapOf(
                        "SUN" to 0,
                        "MON" to 1,
                        "TUE" to 2,
                        "WED" to 3,
                        "THU" to 4,
                        "FRI" to 5,
                        "SAT" to 6,
                    ),
                )
                .map { it % 7 }
                .toSet()
        anyDay = fields[2].startsWith("*")
        anyWeekday = fields[4].startsWith("*")
    }

    fun nextAfter(afterMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        var next =
            Instant.ofEpochMilli(afterMillis).atZone(zone).withSecond(0).withNano(0).plusMinutes(1)
        val limit = next.plusYears(8).plusDays(1).toInstant()
        while (next.toInstant().isBefore(limit)) {
            when {
                next.monthValue !in months ->
                    next = next.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone)
                !matchesDate(next) -> next = next.toLocalDate().plusDays(1).atStartOfDay(zone)
                next.hour !in hours -> next = next.plusHours(1).withMinute(0)
                next.minute !in minutes -> next = next.plusMinutes(1)
                else -> return next.toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun matchesDate(time: ZonedDateTime): Boolean {
        val dayMatches = time.dayOfMonth in days
        val weekdayMatches = time.dayOfWeek.value % 7 in weekdays
        return if (anyDay || anyWeekday) dayMatches && weekdayMatches
        else dayMatches || weekdayMatches
    }

    private fun parseField(
        raw: String,
        minimum: Int,
        maximum: Int,
        names: Map<String, Int> = emptyMap(),
    ): Set<Int> {
        val values = mutableSetOf<Int>()
        fun number(token: String): Int =
            names[token.uppercase(Locale.ROOT)]
                ?: token.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid Cron value: $token")
        raw.split(',').forEach { part ->
            val pieces = part.split('/')
            require(pieces.size in 1..2 && pieces.none { it.isBlank() }) {
                "Invalid Cron field: $raw"
            }
            val step = if (pieces.size == 2) pieces[1].toIntOrNull() else 1
            require(step != null && step > 0 && step <= maximum - minimum + 1) {
                "Invalid Cron step: $part"
            }
            val base = pieces[0]
            val bounds =
                when {
                    base == "*" -> minimum to maximum
                    base.contains('-') -> {
                        val range = base.split('-')
                        require(range.size == 2) { "Invalid Cron range: $base" }
                        number(range[0]) to number(range[1])
                    }
                    pieces.size == 2 -> number(base) to maximum
                    else -> number(base).let { it to it }
                }
            require(
                bounds.first in minimum..maximum &&
                    bounds.second in minimum..maximum &&
                    bounds.first <= bounds.second
            ) {
                "Cron value outside range: $part"
            }
            for (value in bounds.first..bounds.second step step) values += value
        }
        require(values.isNotEmpty()) { "Empty Cron field" }
        return values
    }
}
