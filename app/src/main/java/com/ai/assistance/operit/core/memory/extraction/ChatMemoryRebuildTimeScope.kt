package com.ai.assistance.operit.core.memory.extraction

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

sealed class ChatMemoryRebuildTimeScope {
    data object EntireChat : ChatMemoryRebuildTimeScope()

    data class InclusiveLocalRange(
        val startInclusiveMs: Long,
        val endExclusiveMs: Long
    ) : ChatMemoryRebuildTimeScope() {
        init {
            require(endExclusiveMs > startInclusiveMs) {
                "memory rebuild range end must be after start"
            }
        }

        fun contains(timestamp: Long): Boolean =
            timestamp >= startInclusiveMs && timestamp < endExclusiveMs
    }

    companion object {
        fun utcMidnightMillisToLocalDate(utcMidnightMs: Long): LocalDate =
            Instant.ofEpochMilli(utcMidnightMs).atZone(ZoneOffset.UTC).toLocalDate()

        fun inclusiveDates(
            start: LocalDate,
            end: LocalDate,
            zone: ZoneId
        ): InclusiveLocalRange {
            require(!end.isBefore(start)) {
                "memory rebuild range end must not be before start"
            }
            return InclusiveLocalRange(
                startInclusiveMs = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                endExclusiveMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }
    }
}
