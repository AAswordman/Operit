package com.ai.assistance.operit.core.workflow.scheduling

import com.ai.assistance.operit.core.workflow.model.NodePosition
import com.ai.assistance.operit.core.workflow.model.TriggerNode
import com.ai.assistance.operit.core.workflow.model.Workflow
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class WorkflowSchedulePlanTest {
    private val utc = ZoneId.of("UTC")

    private fun time(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    private fun next(expression: String, after: String, zone: ZoneId = utc) =
        CronSchedule(expression).nextAfter(time(after), zone)

    private fun workflow(config: Map<String, String>) =
        Workflow(
            id = "scheduled-workflow",
            nodes =
                listOf(
                    TriggerNode(
                        id = "scheduled-trigger",
                        triggerType = "schedule",
                        triggerConfig = config,
                    )
                ),
        )

    private fun plan(vararg config: Pair<String, String>) =
        requireNotNull(WorkflowSchedulePlan.from(workflow(mapOf(*config)), utc))

    @Test
    fun cronStartsAtTheNextWholeMinute() {
        assertEquals(time("2026-10-23T08:13:00Z"), next("* * * * *", "2026-10-23T08:12:59.999Z"))
    }

    @Test
    fun minuteStepsAlignWithTheClock() {
        assertEquals(time("2026-10-23T08:15:00Z"), next("*/15 * * * *", "2026-10-23T08:07:34Z"))
    }

    @Test
    fun hourStepsAlignWithTheClock() {
        assertEquals(time("2026-10-23T10:00:00Z"), next("0 */2 * * *", "2026-10-23T08:07:00Z"))
    }

    @Test
    fun weeklyPresetFindsMonday() {
        assertEquals(time("2026-10-26T00:00:00Z"), next("0 0 * * 1", "2026-10-23T10:00:00Z"))
    }

    @Test
    fun workdayPresetSkipsTheWeekend() {
        assertEquals(time("2026-10-26T09:00:00Z"), next("0 9 * * 1-5", "2026-10-23T09:00:00Z"))
    }

    @Test
    fun monthAndWeekdayNamesAreAccepted() {
        assertEquals(time("2027-01-04T09:00:00Z"), next("0 9 * JAN MON", "2026-10-23T09:00:00Z"))
    }

    @Test
    fun listsRangesAndStepsCanBeCombined() {
        assertEquals(
            time("2026-10-23T08:30:00Z"),
            next("5,20-40/10 8-18/2 * * *", "2026-10-23T08:20:00Z"),
        )
    }

    @Test
    fun restrictedDayAndWeekdayUseOr() {
        assertEquals(time("2026-10-26T09:00:00Z"), next("0 9 13 * MON", "2026-10-23T09:00:00Z"))
    }

    @Test
    fun wildcardDayStepsStillRestrictTheDay() {
        assertEquals(time("2026-11-09T09:00:00Z"), next("0 9 */2 * MON", "2026-10-23T09:00:00Z"))
    }

    @Test
    fun leapDayCanBeMoreThanOneYearAway() {
        assertEquals(time("2028-02-29T09:00:00Z"), next("0 9 29 2 *", "2025-03-01T00:00:00Z"))
    }

    @Test
    fun impossibleCalendarDateHasNoOccurrence() {
        assertNull(next("0 9 31 2 *", "2026-01-01T00:00:00Z"))
    }

    @Test
    fun sundayZeroAndSevenAreEquivalent() {
        assertEquals(
            next("0 9 * * 0", "2026-10-23T09:00:00Z"),
            next("0 9 * * 7", "2026-10-23T09:00:00Z"),
        )
    }

    @Test
    fun cronSkipsNonexistentSpringTime() {
        assertEquals(
            time("2026-03-09T02:30:00-04:00"),
            next("30 2 * * *", "2026-03-07T03:00:00-05:00", ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun cronIncludesBothOccurrencesOfAnOverlappingHour() {
        assertEquals(
            time("2026-11-01T01:30:00-05:00"),
            next("30 1 * * *", "2026-11-01T01:40:00-04:00", ZoneId.of("America/New_York")),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cronRejectsExtraFields() {
        CronSchedule("0 0 0 * * *")
    }

    @Test(expected = IllegalArgumentException::class)
    fun cronRejectsOutOfRangeValues() {
        CronSchedule("60 9 * * *")
    }

    @Test(expected = IllegalArgumentException::class)
    fun cronRejectsZeroStep() {
        CronSchedule("*/0 * * * *")
    }

    @Test
    fun intervalPreservesMillisecondsAndWaitsAFullInterval() {
        val plan = plan("schedule_type" to "interval", "interval_ms" to "900001")
        assertEquals(900001L, plan.intervalMillis)
        assertEquals(1000001L, plan.nextAfter(100000L))
        assertTrue(plan.periodic)
    }

    @Test(expected = IllegalArgumentException::class)
    fun intervalBelowFifteenMinutesIsRejected() {
        plan("schedule_type" to "interval", "interval_ms" to "899999")
    }

    @Test
    fun unconfiguredAndDisabledTriggersHaveNoPlan() {
        assertNull(WorkflowSchedulePlan.from(workflow(emptyMap()), utc))
        val configured = workflow(mapOf("schedule_type" to "interval", "interval_ms" to "900000"))
        assertNull(WorkflowSchedulePlan.from(configured.copy(enabled = false), utc))
        assertNull(
            WorkflowSchedulePlan.from(
                workflow(
                    mapOf(
                        "enabled" to "false",
                        "schedule_type" to "cron",
                        "cron_expression" to "* * * * *",
                    )
                ),
                utc,
            )
        )
    }

    @Test
    fun metadataAndExecutionStatisticsDoNotResetScheduleIdentity() {
        val original = workflow(mapOf("schedule_type" to "interval", "interval_ms" to "900000"))
        val trigger = original.nodes.single() as TriggerNode
        val edited =
            original.copy(
                name = "改名",
                description = "更新说明",
                nodes = listOf(trigger.copy(position = NodePosition(100f, 200f), name = "节点改名")),
                totalExecutions = 7,
            )
        assertEquals(WorkflowSchedulePlan.identity(original), WorkflowSchedulePlan.identity(edited))
    }

    @Test
    fun changingConfigurationOrTriggerChangesScheduleIdentity() {
        val original = workflow(mapOf("schedule_type" to "interval", "interval_ms" to "900000"))
        val changed = workflow(mapOf("schedule_type" to "interval", "interval_ms" to "1800000"))
        assertNotEquals(
            WorkflowSchedulePlan.identity(original),
            WorkflowSchedulePlan.identity(changed),
        )
        val node = original.nodes.single() as TriggerNode
        assertNotEquals(
            WorkflowSchedulePlan.identity(original),
            WorkflowSchedulePlan.identity(
                original.copy(nodes = listOf(node.copy(id = "another-trigger")))
            ),
        )
    }

    @Test
    fun nonrepeatingIntervalIsAOneTimePlan() {
        assertFalse(
            plan("schedule_type" to "interval", "interval_ms" to "900000", "repeat" to "false")
                .periodic
        )
    }

    @Test
    fun specificTimeOnlyRunsInTheFuture() {
        val plan =
            plan(
                "schedule_type" to "specific_time",
                "specific_time" to "2026-10-23 09:00:00",
                "repeat" to "false",
            )
        assertEquals(time("2026-10-23T09:00:00Z"), plan.nextAfter(time("2026-10-23T08:00:00Z")))
        assertNull(plan.nextAfter(time("2026-10-23T09:00:00Z")))
    }

    @Test
    fun repeatingSpecificTimeAdvancesToTheNextDay() {
        val plan =
            plan(
                "schedule_type" to "specific_time",
                "specific_time" to "2026-10-23 09:00:00",
                "repeat" to "true",
            )
        assertEquals(time("2026-10-24T09:00:00Z"), plan.nextAfter(time("2026-10-23T10:00:00Z")))
    }

    @Test
    fun repeatingSpecificTimeKeepsLocalTimeAcrossDst() {
        val plan =
            requireNotNull(
                WorkflowSchedulePlan.from(
                    workflow(
                        mapOf(
                            "schedule_type" to "specific_time",
                            "specific_time" to "2026-03-07 09:00:00",
                            "repeat" to "true",
                        )
                    ),
                    ZoneId.of("America/New_York"),
                )
            )
        assertEquals(
            time("2026-03-08T09:00:00-04:00"),
            plan.nextAfter(time("2026-03-07T10:00:00-05:00")),
        )
    }

    @Test
    fun endTimeIncludesItsBoundaryAndStopsLaterOccurrences() {
        val plan =
            plan(
                "schedule_type" to "cron",
                "cron_expression" to "0 9 * * *",
                "end_time" to "2026-10-23 09:00:00",
            )
        val end = time("2026-10-23T09:00:00Z")
        assertEquals(end, plan.nextAfter(time("2026-10-23T08:00:00Z")))
        assertFalse(plan.expired(end))
        assertTrue(plan.expired(end + 1))
        assertNull(plan.nextAfter(end))
    }

    @Test
    fun localScheduleUsesTheCurrentSystemZone() {
        val config = workflow(mapOf("schedule_type" to "cron", "cron_expression" to "0 9 * * *"))
        val now = time("2026-10-23T00:00:00Z")
        assertEquals(
            time("2026-10-23T01:00:00Z"),
            requireNotNull(WorkflowSchedulePlan.from(config, ZoneId.of("Asia/Shanghai")))
                .nextAfter(now),
        )
        assertEquals(
            time("2026-10-23T09:00:00Z"),
            requireNotNull(WorkflowSchedulePlan.from(config, utc)).nextAfter(now),
        )
    }

    @Test
    fun explicitOffsetIsPreserved() {
        assertEquals(
            time("2026-10-23T01:00:00Z"),
            WorkflowSchedulePlan.parseDateTime("2026-10-23T09:00:00+08:00", utc)
                .toInstant()
                .toEpochMilli(),
        )
    }

    @Test(expected = java.time.DateTimeException::class)
    fun invalidDateDoesNotRollIntoAnotherMonth() {
        WorkflowSchedulePlan.parseDateTime("2026-02-30 09:00:00", utc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonexistentLocalTimeIsRejected() {
        WorkflowSchedulePlan.parseDateTime("2026-03-08 02:30:00", ZoneId.of("America/New_York"))
    }
}
