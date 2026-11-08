package com.ai.assistance.operit.ui.features.conversation.memory.workbench

import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.conversation.memory.screens.graph.model.Node
import org.junit.Assert.*
import org.junit.Test

class MemoryGraphPresentationTest {
    private fun node(id: String, folder: String = "") = Node(id, id, metadata = mapOf("folder" to folder))
    private fun edge(id: Long, from: String, to: String) = Edge(id, from, to, "USES")
    private val graph = Graph(
        listOf(node("a", "work"), node("b", "work/sub"), node("c", "personal"), node("d", "docs"), node("e"), node("f", "workshop")),
        listOf(edge(1, "a", "b"), edge(2, "a", "c"), edge(3, "c", "d"), edge(4, "a", "e")),
    )
    private val uncategorized = "未分类"

    @Test fun emptyFolderOverviewRemainsEmpty() {
        assertEquals(Graph(emptyList(), emptyList()), memoryFolderGraph(Graph(emptyList(), emptyList()), uncategorized))
    }

    @Test fun folderOverviewCountsEachMemoryOnce() {
        val folded = memoryFolderGraph(graph, uncategorized)
        assertEquals(6, folded.nodes.sumOf { it.metadata.getValue("count").toInt() })
        assertEquals(6, folded.nodes.size)
        assertEquals("work", folded.nodes.first { it.id == "folder:work" }.label)
    }

    @Test fun sameFolderLinksAreNotCrossFolderAggregates() {
        val input = Graph(listOf(node("a", "work"), node("b", "work")), listOf(edge(1, "a", "b")))
        val folded = memoryFolderGraph(input, uncategorized)
        assertEquals(1, folded.nodes.size)
        assertEquals("2", folded.nodes.single().metadata["count"])
        assertTrue(folded.edges.isEmpty())
    }

    @Test fun oppositeDirectionsContributeToTheSameFolderConnection() {
        val input = Graph(listOf(node("a", "work"), node("b", "personal")), listOf(edge(1, "a", "b"), edge(2, "b", "a")))
        val aggregate = memoryFolderGraph(input, uncategorized).edges.single()
        assertEquals("2", aggregate.metadata["count"])
        assertEquals(2f, aggregate.weight, 0f)
        assertTrue(aggregate.id < 0)
    }

    @Test fun folderScopeIncludesDescendantsButNotSimilarPrefixes() {
        val scoped = memoryGraphScope(graph, "work", uncategorized, false)
        assertEquals(setOf("a", "b"), scoped.nodes.map { it.id }.toSet())
        assertEquals(listOf(1L), scoped.edges.map { it.id })
    }

    @Test fun uncategorizedScopeUsesTheExistingEmptyPath() {
        assertEquals(listOf("e"), memoryGraphScope(graph, uncategorized, uncategorized, false).nodes.map { it.id })
        assertEquals(uncategorized, memoryFolderGraph(graph, uncategorized).nodes.first { it.id == "folder:" }.label)
    }

    @Test fun crossFolderToggleAddsOnlyDirectNeighbors() {
        val scoped = memoryGraphScope(graph, "work", uncategorized, true)
        assertEquals(setOf("a", "b", "c", "e"), scoped.nodes.map { it.id }.toSet())
        assertFalse(scoped.nodes.any { it.id == "d" })
    }

    @Test fun relatedViewIncludesIncomingAndOutgoingNodesAcrossFolders() {
        val scoped = memoryGraphScope(graph, "work", uncategorized, false, relatedId = "c")
        assertEquals(setOf("a", "c", "d"), scoped.nodes.map { it.id }.toSet())
        assertEquals(setOf(2L, 3L), scoped.edges.map { it.id }.toSet())
    }

    @Test fun isolatedMemoryRemainsVisibleInRelatedView() {
        val scoped = memoryGraphScope(graph, "work", uncategorized, false, relatedId = "f")
        assertEquals(listOf("f"), scoped.nodes.map { it.id })
        assertTrue(scoped.edges.isEmpty())
    }

    @Test fun everyDisplayedEdgeHasBothEndpointsInScope() {
        val scoped = memoryGraphScope(graph, "personal", uncategorized, true)
        val ids = scoped.nodes.map { it.id }.toSet()
        assertTrue(scoped.edges.all { it.sourceId in ids && it.targetId in ids })
    }

    @Test fun emptySearchResultsDoNotRestoreTheWholeGraph() {
        val scoped = memoryGraphScope(graph, "", uncategorized, true, searchIds = emptySet())
        assertTrue(scoped.nodes.isEmpty())
        assertTrue(scoped.edges.isEmpty())
    }

    @Test fun searchCanDisplayDirectNeighborsWithoutIncludingAnotherHop() {
        val scoped = memoryGraphScope(graph, "", uncategorized, true, searchIds = setOf("d"))
        assertEquals(setOf("c", "d"), scoped.nodes.map { it.id }.toSet())
    }

    @Test fun viewTransformsLeaveOriginalGraphUnchanged() {
        val original = graph.copy()
        memoryFolderGraph(graph, uncategorized)
        memoryGraphScope(graph, "work", uncategorized, false, relatedId = "a")
        assertEquals(original, graph)
    }

    @Test fun unknownRelationshipTypeIsPreservedAsAnOption() {
        val options = memoryRelationOptions("Legacy.Custom")
        assertEquals("Legacy.Custom", options.first())
        assertEquals(R.string.memory_relation_existing, memoryRelationName(options.first()))
    }

    @Test fun lowercaseLegacyTypeKeepsItsOriginalStoredValue() {
        assertEquals("uses", memoryRelationOptions("uses").first())
        assertEquals(R.string.memory_relation_uses, memoryRelationName("uses"))
        assertEquals(R.string.memory_relation_related_to, memoryRelationName("related"))
    }

    @Test fun commonRelationshipOptionsAreFixedAndNotDuplicated() {
        val options = memoryRelationOptions("USES")
        assertEquals(10, options.size)
        assertEquals(options.size, options.distinct().size)
        assertTrue(options.all { it == it.uppercase(java.util.Locale.ROOT) })
    }
}