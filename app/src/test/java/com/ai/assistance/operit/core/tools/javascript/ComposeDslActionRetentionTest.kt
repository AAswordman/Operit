package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslRenderResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeDslActionRetentionTest {
    @Test
    fun `collect callbacks from children slots modifiers and cached nodes`() {
        val root = ToolPkgComposeDslNode(
            type = "Column",
            props = mapOf("onLoad" to action("load"), "empty" to null),
            children = listOf(
                ToolPkgComposeDslNode(
                    type = "Button",
                    props = mapOf("onClick" to action("click")),
                    children = emptyList()
                )
            ),
            slots = mapOf("title" to listOf(
                ToolPkgComposeDslNode(
                    type = "Text",
                    props = mapOf("modifier" to listOf(mapOf("args" to listOf(action("modifier"))))),
                    children = emptyList()
                )
            ))
        )
        val result = ToolPkgComposeDslRenderResult(
            tree = root,
            state = mapOf("saved" to action("state")),
            memo = mapOf("cached" to listOf(mapOf("props" to action("cached")), action("click")))
        )

        assertEquals(setOf("load", "click", "modifier", "state", "cached"), collectComposeDslActionIds(result))
    }

    @Test
    fun `deep trees do not require recursive traversal`() {
        var tree = ToolPkgComposeDslNode("Button", mapOf("onClick" to action("deep")), emptyList())
        repeat(5000) {
            tree = ToolPkgComposeDslNode("Box", emptyMap(), listOf(tree))
        }
        val result = ToolPkgComposeDslRenderResult(tree, emptyMap(), emptyMap())

        assertEquals(setOf("deep"), collectComposeDslActionIds(result))
    }

    @Test
    fun `ordinary text does not become a retained callback`() {
        val result = ToolPkgComposeDslRenderResult(
            ToolPkgComposeDslNode("Text", mapOf("text" to "__action_123"), emptyList()),
            emptyMap(),
            mapOf("empty" to null)
        )

        assertEquals(emptySet<String>(), collectComposeDslActionIds(result))
    }

    private fun action(id: String): Map<String, String> = mapOf("__actionId" to id)
}
