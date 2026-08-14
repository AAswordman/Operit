package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.util.AppLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MemoryAnalysisProtocolTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun parseAnalysisResult_acceptsObjectProtocolWithoutMainEvent() {
        val analysis =
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main": null,
                  "new": [{
                    "title": "TMUX close result",
                    "content": "The close command works after state verification.",
                    "tags": ["tmux", "verification"],
                    "folder_path": "Tool state",
                    "alias_for": null
                  }],
                  "update": [{
                    "title": "SSH tool state",
                    "content": "The ls timeout is resolved.",
                    "reason": "The current test confirms the tool is healthy.",
                    "credibility": 1.0,
                    "importance": 0.6
                  }],
                  "merge": [{
                    "source_titles": ["Old A", "Old B"],
                    "title": "Merged state",
                    "content": "The merged fact.",
                    "tags": ["state"],
                    "folder_path": "Tool state",
                    "reason": "They describe the same fact."
                  }],
                  "links": [{
                    "source": "TMUX close result",
                    "target": "SSH tool state",
                    "type": "INVOLVES",
                    "description": "The test used the SSH tool.",
                    "weight": 0.8
                  }]
                }
                """.trimIndent()
            )

        assertNull(analysis.mainProblem)
        assertEquals("TMUX close result", analysis.extractedEntities.single().title)
        assertEquals("SSH tool state", analysis.updatedEntities.single().titleToUpdate)
        assertEquals("Merged state", analysis.mergedEntities.single().newTitle)
        assertEquals("INVOLVES", analysis.links.single().type)
    }

    @Test
    fun parseAnalysisResult_rejectsLegacyPositionalArrayProtocol() {
        assertThrows(Exception::class.java) {
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main": null,
                  "new": [["Title", "Content", ["tag"], "Folder", null]],
                  "update": [],
                  "merge": [],
                  "links": []
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun parseAnalysisResult_rejectsMissingRequiredOperationField() {
        assertThrows(IllegalArgumentException::class.java) {
            MemoryLibrary.parseAnalysisResult(
                """
                {
                  "main": null,
                  "new": [],
                  "update": [],
                  "merge": []
                }
                """.trimIndent()
            )
        }
    }
}
