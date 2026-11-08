package com.ai.assistance.operit.ui.features.models.tokenstats

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal enum class CustomRangeValidation {
    VALID,
    INVALID_BOUNDS,
    TOO_LONG,
}

internal fun validateCustomRange(
    startMs: Long,
    endMs: Long,
    zone: ZoneId,
    maxRangeDays: Long,
): CustomRangeValidation {
    if (endMs <= startMs) return CustomRangeValidation.INVALID_BOUNDS
    val startDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val exclusiveEndDate = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
    return if (ChronoUnit.DAYS.between(startDate, exclusiveEndDate) > maxRangeDays) {
        CustomRangeValidation.TOO_LONG
    } else {
        CustomRangeValidation.VALID
    }
}
