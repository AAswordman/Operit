package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiscModelTest {

    @Test fun `billing mode has TOKEN and COUNT`() {
        assertTrue(BillingMode.values().contains(BillingMode.TOKEN))
        assertTrue(BillingMode.values().contains(BillingMode.COUNT))
    }

    @Test fun `billing mode fromString parses correctly`() {
        assertEquals(BillingMode.TOKEN, BillingMode.fromString("token"))
        assertEquals(BillingMode.COUNT, BillingMode.fromString("count"))
    }

    @Test fun `billing mode fromString default to TOKEN`() {
        assertEquals(BillingMode.TOKEN, BillingMode.fromString("invalid"))
        assertEquals(BillingMode.TOKEN, BillingMode.fromString(null))
    }

    @Test fun `billing mode fromString is case insensitive`() {
        assertEquals(BillingMode.TOKEN, BillingMode.fromString("TOKEN"))
        assertEquals(BillingMode.COUNT, BillingMode.fromString("COUNT"))
    }

    @Test fun `active prompt character card`() {
        val prompt = ActivePrompt.CharacterCard(id = "card1")
        assertTrue(prompt is ActivePrompt.CharacterCard)
        assertEquals("card1", (prompt as ActivePrompt.CharacterCard).id)
    }

    @Test fun `active prompt character group`() {
        val prompt = ActivePrompt.CharacterGroup(id = "group1")
        assertTrue(prompt is ActivePrompt.CharacterGroup)
        assertEquals("group1", (prompt as ActivePrompt.CharacterGroup).id)
    }

    @Test fun `ai reference creation`() {
        val ref = AiReference(text = "source", url = "https://example.com")
        assertEquals("source", ref.text)
        assertEquals("https://example.com", ref.url)
    }

    @Test fun `ai reference empty fields`() {
        val ref = AiReference(text = "", url = "")
        assertEquals("", ref.text)
        assertEquals("", ref.url)
    }

    @Test fun `custom emoji creation`() {
        val emoji = CustomEmoji(emotionCategory = "happy", fileName = "smile.jpg", isBuiltInCategory = true)
        assertEquals("happy", emoji.emotionCategory)
        assertEquals("smile.jpg", emoji.fileName)
        assertTrue(emoji.isBuiltInCategory)
    }

    @Test fun `document chunk creation`() {
        val chunk = DocumentChunk(content = "Chunk content", chunkIndex = 1)
        assertEquals("Chunk content", chunk.content)
        assertEquals(1, chunk.chunkIndex)
        assertEquals(0L, chunk.id)
    }

    @Test fun `document chunk default values`() {
        val chunk = DocumentChunk()
        assertEquals("", chunk.content)
        assertEquals(0, chunk.chunkIndex)
        assertEquals(0L, chunk.id)
    }

    @Test fun `embedding dimension usage default values`() {
        val usage = EmbeddingDimensionUsage()
        assertEquals(0, usage.memoryTotal)
        assertEquals(0, usage.memoryMissing)
        assertTrue(usage.memoryDimensions.isEmpty())
        assertEquals(0, usage.chunkTotal)
        assertEquals(0, usage.chunkMissing)
        assertTrue(usage.chunkDimensions.isEmpty())
    }

    @Test fun `embedding dimension usage with counts`() {
        val dims = listOf(DimensionCount(dimension = 128, count = 5))
        val usage = EmbeddingDimensionUsage(
            memoryTotal = 10, memoryMissing = 2, memoryDimensions = dims,
            chunkTotal = 20, chunkMissing = 1, chunkDimensions = dims,
        )
        assertEquals(10, usage.memoryTotal)
        assertEquals(2, usage.memoryMissing)
        assertEquals(1, usage.memoryDimensions.size)
        assertEquals(128, usage.memoryDimensions[0].dimension)
        assertEquals(5, usage.memoryDimensions[0].count)
    }

    @Test fun `embedding rebuild progress default values`() {
        val progress = EmbeddingRebuildProgress()
        assertEquals(0, progress.total)
        assertEquals(0, progress.processed)
        assertEquals(0, progress.failed)
        assertEquals("", progress.currentStage)
    }

    @Test fun `embedding rebuild progress fraction`() {
        val progress = EmbeddingRebuildProgress(total = 10, processed = 5)
        assertEquals(0.5f, progress.fraction, 0.001f)
        assertFalse(progress.isFinished)
    }

    @Test fun `embedding rebuild progress finished`() {
        val progress = EmbeddingRebuildProgress(total = 10, processed = 10)
        assertEquals(1.0f, progress.fraction, 0.001f)
        assertTrue(progress.isFinished)
    }

    @Test fun `embedding rebuild progress zero total`() {
        val progress = EmbeddingRebuildProgress()
        assertEquals(0f, progress.fraction, 0.001f)
        assertFalse(progress.isFinished)
    }

    @Test fun `input processing state idle`() {
        val state = InputProcessingState.Idle
        assertTrue(state is InputProcessingState.Idle)
    }

    @Test fun `input processing state completed`() {
        val state = InputProcessingState.Completed
        assertTrue(state is InputProcessingState.Completed)
    }

    @Test fun `input processing state processing`() {
        val state = InputProcessingState.Processing(message = "Loading...")
        assertTrue(state is InputProcessingState.Processing)
        assertEquals("Loading...", (state as InputProcessingState.Processing).message)
    }

    @Test fun `input processing state error`() {
        val state = InputProcessingState.Error(message = "Failed")
        assertTrue(state is InputProcessingState.Error)
        assertEquals("Failed", (state as InputProcessingState.Error).message)
    }

    @Test fun `input processing state connecting`() {
        val state = InputProcessingState.Connecting(message = "Connecting...")
        assertTrue(state is InputProcessingState.Connecting)
    }

    @Test fun `input processing state receiving`() {
        val state = InputProcessingState.Receiving(message = "Receiving...")
        assertTrue(state is InputProcessingState.Receiving)
    }

    @Test fun `input processing state executing tool`() {
        val state = InputProcessingState.ExecutingTool(toolName = "calculator")
        assertTrue(state is InputProcessingState.ExecutingTool)
        assertEquals("calculator", (state as InputProcessingState.ExecutingTool).toolName)
    }

    @Test fun `input processing state tool progress`() {
        val state = InputProcessingState.ToolProgress(toolName = "search", progress = 0.5f)
        assertTrue(state is InputProcessingState.ToolProgress)
        assertEquals(0.5f, (state as InputProcessingState.ToolProgress).progress, 0.001f)
    }

    @Test fun `input processing state summarizing`() {
        val state = InputProcessingState.Summarizing(message = "Summarizing...")
        assertTrue(state is InputProcessingState.Summarizing)
    }

    @Test fun `input processing state executing plan`() {
        val state = InputProcessingState.ExecutingPlan(message = "Planning...")
        assertTrue(state is InputProcessingState.ExecutingPlan)
    }

    @Test fun `memory search config creation`() {
        val config = MemorySearchConfig(
            scoreMode = MemoryScoreMode.BALANCED,
            keywordWeight = 10.0f,
            tagWeight = 0.0f,
        )
        assertEquals(MemoryScoreMode.BALANCED, config.scoreMode)
        assertEquals(10.0f, config.keywordWeight, 0.001f)
        assertEquals(0.0f, config.tagWeight, 0.001f)
    }

    @Test fun `memory search debug info creation`() {
        val info = MemorySearchDebugInfo(
            query = "test query",
            keywords = listOf("test"),
            lexicalTokens = listOf("test"),
            scoreMode = MemoryScoreMode.BALANCED,
            relevanceThreshold = 0.5,
            effectiveKeywordWeight = 1.0,
            effectiveTagWeight = 0.0,
            effectiveSemanticWeight = 0.0f,
            semanticKeywordNormFactor = 0.0,
            effectiveEdgeWeight = 0.0,
            memoriesInScopeCount = 10,
            keywordMatchesCount = 5,
            tagMatchesCount = 0,
            reverseContainmentMatchesCount = 0,
            semanticMatchesCount = 0,
            graphEdgesTraversed = 0,
            scoredCount = 5,
            passedThresholdCount = 3,
            candidates = emptyList(),
            finalResultIds = emptyList(),
        )
        assertEquals("test query", info.query)
        assertEquals(5, info.keywordMatchesCount)
        assertEquals(10, info.memoriesInScopeCount)
    }

    @Test fun `workflow execution record creation`() {
        val record = WorkflowExecutionRecord(
            workflowId = "wf1",
            workflowName = "Test Workflow",
            success = true,
            message = "Completed",
        )
        assertEquals("wf1", record.workflowId)
        assertEquals("Test Workflow", record.workflowName)
        assertTrue(record.success)
        assertEquals("Completed", record.message)
    }

    @Test fun `chat message locator preview creation`() {
        val preview = ChatMessageLocatorPreview(
            timestamp = 1000L,
            sender = "user",
            previewContent = "Hello",
            contentLength = 5,
            displayMode = ChatMessageDisplayMode.NORMAL.name,
            isFavorite = false,
        )
        assertEquals(1000L, preview.timestamp)
        assertEquals("Hello", preview.previewContent)
        assertEquals(ChatMessageDisplayMode.NORMAL, preview.resolvedDisplayMode)
    }

    @Test fun `memory space creation`() {
        val space = MemorySpace(id = "default", name = "Default")
        assertEquals("default", space.id)
        assertEquals("Default", space.name)
    }

    @Test fun `operit node info creation`() {
        val info = OperitNodeInfo(
            className = "android.widget.Button",
            packageName = "com.example",
            text = "Submit",
            contentDescription = null,
            viewIdResourceName = null,
            boundsInScreen = "[0,0][100,50]",
            isClickable = true,
            isVisibleToUser = true,
            isFocused = false,
            isChecked = false,
        )
        assertEquals("android.widget.Button", info.className)
        assertEquals("com.example", info.packageName)
        assertEquals("Submit", info.text)
        assertTrue(info.isClickable)
        assertTrue(info.isVisibleToUser)
    }

    @Test fun `chat turn options creation`() {
        val options = ChatTurnOptions(persistTurn = true, hideUserMessage = false)
        assertTrue(options.persistTurn)
        assertFalse(options.hideUserMessage)
    }
}
