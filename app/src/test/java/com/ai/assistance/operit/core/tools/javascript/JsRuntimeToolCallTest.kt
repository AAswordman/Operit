package com.ai.assistance.operit.core.tools.javascript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsRuntimeToolCallTest {
    @Test
    fun `toolCall dispatches through the generic native bridge`() {
        val source =
            buildInitRuntimeModules(
                operitDownloadDir = "/operit/downloads",
                operitCleanOnExitDir = "/operit/clean"
            ).first { it.fileName == "quickjs/init/runtime-tool-call.js" }.source

        assertTrue(source.contains("'callToolAsync'"))
        assertTrue(source.contains("'callToolAsyncStreaming'"))
        assertFalse(source.contains("callToolAsyncForCall"))
        assertFalse(source.contains("callToolAsyncStreamingForCall"))
        assertFalse(source.contains("toolCall requires an active execution call"))
    }
}
