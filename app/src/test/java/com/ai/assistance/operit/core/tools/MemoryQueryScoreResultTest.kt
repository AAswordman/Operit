package com.ai.assistance.operit.core.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryQueryScoreResultTest {
    @Test
    fun scoredQuerySerializesTotalAndContributions() {
        val info = MemoryQueryResultData.MemoryInfo(
            title = "match",
            content = "content",
            source = "test",
            tags = emptyList(),
            createdAt = "2026-01-01 12:00",
            score = MemoryQueryResultData.ScoreInfo(
                total = 0.32,
                keyword = 0.1,
                tag = 0.0,
                reverseContainment = 0.0,
                semantic = 0.2,
                edge = 0.02
            )
        )

        val json = Json.parseToJsonElement(MemoryQueryResultData(listOf(info)).toJson())
        val score = json.jsonObject.getValue("memories").jsonArray.first()
            .jsonObject.getValue("score").jsonObject
        assertEquals(0.32, score.getValue("total").jsonPrimitive.double, 0.000001)
        assertEquals(0.2, score.getValue("semantic").jsonPrimitive.double, 0.000001)
        assertEquals(0.02, score.getValue("edge").jsonPrimitive.double, 0.000001)
    }

    @Test
    fun wildcardAndTitleLookupsDoNotInventScores() {
        val info = MemoryQueryResultData.MemoryInfo(
            title = "memory",
            content = "preview...",
            source = "test",
            tags = emptyList(),
            createdAt = "2026-01-01 12:00"
        )

        val json = Json.parseToJsonElement(MemoryQueryResultData(listOf(info)).toJson())
        assertNull(json.jsonObject.getValue("memories").jsonArray.first().jsonObject["score"])
    }
}