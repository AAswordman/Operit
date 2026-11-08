package com.ai.assistance.operit.ui.error.report

import com.ai.assistance.operit.ui.features.management.databackup.logcat.LogExportOptions
import org.junit.Assert.*
import org.junit.Test

class CrashReportTest {
    private val time = 1_792_735_175_000L
    private val ordinary =
        "java.lang.IllegalStateException: stopped\n    at example.Screen.open(Screen.kt:9)"

    @Test
    fun classifiesCommonExceptionFamilies() {
        val samples =
            mapOf(
                "java.lang.OutOfMemoryError" to CrashReason.MEMORY,
                "java.lang.StackOverflowError" to CrashReason.STACK_OVERFLOW,
                "android.database.sqlite.SQLiteFullException" to CrashReason.STORAGE_FULL,
                "java.lang.SecurityException" to CrashReason.PERMISSION,
                "java.net.UnknownHostException" to CrashReason.NETWORK,
                "javax.net.ssl.SSLHandshakeException" to CrashReason.NETWORK,
                "android.database.sqlite.SQLiteDatabaseCorruptException" to CrashReason.DATABASE,
                "java.io.FileNotFoundException" to CrashReason.FILE_ACCESS,
                "java.lang.UnsatisfiedLinkError" to CrashReason.COMPONENT_LOADING,
                "android.os.DeadObjectException" to CrashReason.REMOTE_PROCESS,
                "java.lang.NullPointerException" to CrashReason.NULL_REFERENCE,
                "kotlin.UninitializedPropertyAccessException" to CrashReason.NULL_REFERENCE,
                "java.lang.IllegalArgumentException" to CrashReason.INVALID_STATE,
            )
        samples.forEach { (name, expected) ->
            assertEquals(name, expected, CrashReasonClassifier.classify("$name: failure"))
        }
    }

    @Test
    fun nestedSpecificCauseTakesPriorityOverWrapper() {
        val stack =
            ordinary + "\nCaused by: java.lang.OutOfMemoryError: Failed to allocate 1024 bytes"
        assertEquals(CrashReason.MEMORY, CrashReasonClassifier.classify(stack))
        assertEquals("OutOfMemoryError", CrashReasonClassifier.exceptionType(stack))
    }

    @Test
    fun recognizesStorageAndPermissionKeywordsOnlyInExceptionMessages() {
        assertEquals(
            CrashReason.STORAGE_FULL,
            CrashReasonClassifier.classify("java.io.IOException: open failed: ENOSPC"),
        )
        assertEquals(
            CrashReason.PERMISSION,
            CrashReasonClassifier.classify("java.io.FileNotFoundException: open failed: EACCES"),
        )
        assertEquals(
            CrashReason.UNKNOWN,
            CrashReasonClassifier.classify("regular message mentions OutOfMemoryError: memory"),
        )
        assertEquals(
            CrashReason.INVALID_STATE,
            CrashReasonClassifier.classify(
                ordinary + "\n    at example.OutOfMemoryError.report(Log.kt:1)"
            ),
        )
        assertEquals(
            CrashReason.UNKNOWN,
            CrashReasonClassifier.classify("custom.UnrecognizedError: failure"),
        )
        assertEquals(CrashReason.UNKNOWN, CrashReasonClassifier.classify(""))
    }

    @Test
    fun identityKeepsVisibleStackAndAddsOnlyExportMetadata() {
        val report = prepareCrashReport(ordinary, time, LogExportOptions())
        assertEquals(ordinary, report.stackTrace)
        assertTrue(report.exportText.contains("E/CrashReportActivity: $ordinary"))
        assertTrue(report.showTime)
    }

    @Test
    fun errorContextKeepsPlainFatalStackWithoutOriginalLogHeaders() {
        val report = prepareCrashReport(ordinary, time, LogExportOptions(errorContextOnly = true))
        assertEquals(ordinary, report.stackTrace)
        assertTrue(report.exportText.contains("IllegalStateException"))
    }

    @Test
    fun debugExclusionAppliesToEmbeddedLogRecords() {
        val raw =
            ordinary +
                "\n2026-10-23 13:59:35.001 D/Example: DEBUG_PAYLOAD\n2026-10-23 13:59:35.002 E/Example: fatal"
        val report = prepareCrashReport(raw, time, LogExportOptions(excludeDebug = true))
        assertFalse(report.stackTrace.contains("DEBUG_PAYLOAD"))
        assertFalse(report.exportText.contains("DEBUG_PAYLOAD"))
        assertTrue(report.stackTrace.contains("fatal"))
    }

    @Test
    fun systemExclusionKeepsApplicationFatalStack() {
        val raw =
            ordinary +
                "\n2026-10-23 13:59:35.001 I/WebView: SYSTEM_PAYLOAD\n2026-10-23 13:59:35.002 E/Example: application fatal"
        val report = prepareCrashReport(raw, time, LogExportOptions(excludeSystem = true))
        assertFalse(report.stackTrace.contains("SYSTEM_PAYLOAD"))
        assertFalse(report.exportText.contains("SYSTEM_PAYLOAD"))
        assertTrue(report.stackTrace.contains("IllegalStateException"))
        assertTrue(report.stackTrace.contains("application fatal"))
    }

    @Test
    fun sensitiveJsonIsAbsentFromVisibleAndExportedReports() {
        val raw =
            "java.lang.IllegalArgumentException: {\"systemInstruction\":\"PRIVATE_PROMPT\"}\n    at example.Screen.open(Screen.kt:9)"
        val report = prepareCrashReport(raw, time, LogExportOptions(hideSensitive = true))
        assertFalse(report.stackTrace.contains("PRIVATE_PROMPT"))
        assertFalse(report.exportText.contains("PRIVATE_PROMPT"))
        assertTrue(report.stackTrace.contains("[redacted,"))
    }

    @Test
    fun sensitiveRequestMarkersInExceptionAndContinuationAreRedacted() {
        for (raw in
            listOf(
                "java.lang.IllegalStateException: 请求体JSON: PRIVATE_REQUEST",
                "java.lang.IllegalStateException: stopped\n请求体JSON: PRIVATE_REQUEST",
                "java.lang.RuntimeException: stopped\nCaused by: java.lang.IllegalArgumentException: Request body: PRIVATE_REQUEST",
            )) {
            val report = prepareCrashReport(raw, time, LogExportOptions(hideSensitive = true))
            assertFalse(report.stackTrace, report.stackTrace.contains("PRIVATE_REQUEST"))
            assertFalse(report.exportText, report.exportText.contains("PRIVATE_REQUEST"))
        }
    }

    @Test
    fun timestampExclusionAppliesToReportTextAndSummary() {
        val raw = ordinary + "\n2026-10-23 13:59:35.001 E/Example: fatal"
        val report = prepareCrashReport(raw, time, LogExportOptions(stripTimestamp = true))
        val timestamp = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
        assertFalse(timestamp.containsMatchIn(report.stackTrace))
        assertFalse(timestamp.containsMatchIn(report.exportText))
        assertFalse(report.showTime)
        assertTrue(report.exportText.startsWith("E/CrashReportActivity:"))
    }

    @Test
    fun allFiveFiltersShareOneReportWithoutDroppingFatalError() {
        val raw =
            ordinary +
                "\n" +
                listOf(
                        "2026-10-23 13:59:35.001 D/Example: DEBUG_PAYLOAD",
                        "2026-10-23 13:59:35.002 I/WebView: SYSTEM_PAYLOAD",
                        "2026-10-23 13:59:35.003 E/Example: 请求体JSON: PRIVATE_REQUEST",
                    )
                    .joinToString("\n")
        val report = prepareCrashReport(raw, time, LogExportOptions(true, true, true, true, true))
        for (text in listOf(report.stackTrace, report.exportText)) {
            assertFalse(text.contains("DEBUG_PAYLOAD"))
            assertFalse(text.contains("SYSTEM_PAYLOAD"))
            assertFalse(text.contains("PRIVATE_REQUEST"))
            assertFalse(text.contains("2026-10-23"))
            assertTrue(text.contains("[redacted,"))
            assertTrue(text.contains("E/Example"))
        }
    }
}
