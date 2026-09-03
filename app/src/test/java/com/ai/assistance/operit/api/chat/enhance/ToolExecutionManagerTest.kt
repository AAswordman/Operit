package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.stream.StreamCollector
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolExecutionManagerTest {
    @Test
    fun resultMarkupIsDeliveredToDisplayPersistenceAndLiveCollector() = runTest {
        val persisted = mutableListOf<String>()
        val live = mutableListOf<String>()
        val markup = "<tool_result_26C6 name=\"read_file_part\" status=\"error\"><content>missing path</content></tool_result_26C6>"

        ToolExecutionManager.emitToolResultMarkup(
            content = markup,
            collector = object : StreamCollector<String> {
                override suspend fun emit(value: String) {
                    live += value
                }
            },
            onDisplayMarkupEmitted = { persisted += it },
        )

        assertEquals(listOf("$markup\n"), persisted)
        assertEquals(persisted, live)
    }

    @Test
    fun parallelResultMarkupEmissionKeepsPersistenceAndLiveOrderAligned() = runTest {
        val persisted = mutableListOf<String>()
        val live = mutableListOf<String>()
        val mutex = Mutex()

        coroutineScope {
            (1..12)
                .map { index ->
                    async {
                        ToolExecutionManager.emitToolResultMarkup(
                            content = "<tool_result_${index} name=\"read_file_part\" status=\"success\"><content>$index</content></tool_result_${index}>",
                            collector = object : StreamCollector<String> {
                                override suspend fun emit(value: String) {
                                    yield()
                                    live += value
                                }
                            },
                            onDisplayMarkupEmitted = { value ->
                                persisted += value
                                yield()
                            },
                            emissionMutex = mutex,
                        )
                    }
                }
                .awaitAll()
        }

        assertEquals(persisted, live)
        assertEquals(12, persisted.size)
    }
}
