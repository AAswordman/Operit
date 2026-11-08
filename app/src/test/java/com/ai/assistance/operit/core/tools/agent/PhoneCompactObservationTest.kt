package com.ai.assistance.operit.core.tools.agent

import org.junit.Assert.*
import org.junit.Test

class PhoneCompactObservationTest {
    private val pkg = "com.example.app"
    private val frame = PhoneBounds(0, 0, 1000, 1000)
    private fun state(vararg nodes: PhoneUiNode) = PhoneUiSnapshot(1, pkg, nodes.toList(), frame)

    @Test fun hiddenAndOffscreenControlsAreNotOfferedToTheModel() {
        val visible = PhoneUiNode(pkg, text = "Search", clickable = true, bounds = PhoneBounds(10, 10, 100, 100))
        val summary = state(visible, visible.copy(text = "Hidden", visibleToUser = false),
            visible.copy(text = "Offscreen", bounds = PhoneBounds(1200, 10, 1300, 100))).summary()
        assertEquals(listOf("Search"), summary.shown.map { it.modelLabel })
    }

    @Test fun clickableParentReceivesItsTextButKeepsIndependentChildControls() {
        val parent = PhoneUiNode(pkg, clickable = true, bounds = PhoneBounds(0, 0, 500, 100))
        val text = PhoneUiNode(pkg, text = "搜索", parentRef = 1, bounds = PhoneBounds(0, 0, 100, 100))
        val child = PhoneUiNode(pkg, text = "更多", parentRef = 1, clickable = true, bounds = PhoneBounds(400, 0, 500, 100))
        val summary = state(parent, text, child).summary()
        assertEquals(setOf(1, 3), summary.candidates.map { it.ref }.toSet())
        assertEquals("搜索", summary.candidates.first { it.ref == 1 }.modelLabel)
    }

    @Test fun aLateSearchEntrySurvivesTheFeedTextBudget() {
        val feed = List(130) { index -> PhoneUiNode(pkg, resourceId = "feed$index", text = "long feed entry ".repeat(20),
            clickable = true, bounds = PhoneBounds(0, 200 + index * 3, 500, 202 + index * 3)) }
        val search = PhoneUiNode(pkg, resourceId = "search", text = "搜索", clickable = true, bounds = PhoneBounds(800, 20, 900, 100))
        val summary = PhoneUiSnapshot(1, pkg, feed + search, frame).summary("搜索指定人物")
        assertTrue(summary.shown.any { it.resourceId == "search" })
        assertTrue(summary.shown.size < summary.candidates.size)
        assertTrue(summary.payload.length <= 3000)
    }

    @Test fun numberedTapNeedsNoUnseenPageSelectorAndCannotBeSavedAsATemplate() {
        val parsed = PhoneLocalPlanParser.parse("""{"action":"Tap","screen":3,"ref":17}""") as PhoneLocalCommand.Node
        assertEquals(3L, parsed.screen)
        assertEquals(17, parsed.ref)
        assertThrows(PhoneUiException::class.java) {
            PhoneLocalPlanParser.parse("""{"action":"Tap","screen":3,"ref":17,"save_as":"bad"}""")
        }
    }

    @Test fun imageAndNodeRequestsAreSeparateCommands() {
        assertEquals(PhoneLocalCommand.Inspect, PhoneLocalPlanParser.parse("""{"action":"Inspect"}"""))
        assertEquals(PhoneLocalCommand.Observe, PhoneLocalPlanParser.parse("""{"action":"Observe"}"""))
    }

    @Test fun launchOnlyScopeDoesNotSwallowAdditionalInstructions() {
        assertTrue(PhoneLaunchRequest.matchesInstalledApp("启动名为Mika的软件。", "Mika", pkg))
        assertTrue(PhoneLaunchRequest.matchesInstalledApp("当前任务已经完成:无。你需要在此基础上进一步完成:启动名为Mika的软件。", "Mika", pkg))
        assertFalse(PhoneLaunchRequest.matchesInstalledApp("打开Mika，然后搜索张三", "Mika", pkg))
        assertFalse(PhoneLaunchRequest.matchesInstalledApp("打开Mika搜索张三", "Mika", pkg))
    }

    @Test fun aHiddenAncestorExcludesOtherwiseVisibleDescendants() {
        val parent = PhoneUiNode(pkg, visibleToUser = false, bounds = frame)
        val child = PhoneUiNode(pkg, text = "隐藏搜索", clickable = true, parentRef = 1,
            visibleToUser = true, bounds = PhoneBounds(0, 0, 100, 100))
        assertTrue(state(parent, child).summary().shown.isEmpty())
        assertTrue(state(parent, child).matches(PhoneTarget(text = "隐藏搜索")).isEmpty())
    }

    @Test fun parentAndChildWordsAreBothKeptInTheMergedLabel() {
        val parent = PhoneUiNode(pkg, text = "入口", clickable = true, bounds = frame)
        val child = PhoneUiNode(pkg, text = "搜索", parentRef = 1, bounds = PhoneBounds(0, 0, 100, 100))
        val summary = state(parent, child).summary()
        assertEquals(1, summary.candidates.size)
        assertEquals("入口 · 搜索", summary.candidates.single().modelLabel)
    }

    @Test fun partiallyOffscreenTargetsUseTheVisibleIntersection() {
        val node = PhoneUiNode(pkg, text = "入口", clickable = true, bounds = PhoneBounds(-200, 0, 50, 100))
        val snapshot = state(node)
        assertEquals(PhoneBounds(0, 0, 50, 100), snapshot.summary().shown.single().bounds)
        assertEquals(PhoneBounds(0, 0, 50, 100), snapshot.matches(PhoneTarget(text = "入口")).single().bounds)
    }

    @Test fun displayDeduplicationDoesNotRemoveExecutionAmbiguity() {
        val node = PhoneUiNode(pkg, text = "入口", clickable = true, bounds = PhoneBounds(0, 0, 100, 100))
        val summary = state(node, node).summary()
        assertEquals(1, summary.shown.size)
        assertEquals(2, summary.candidates.size)
    }

    @Test fun escapedLabelsAndLongMetadataStillFitTheCharacterBudget() {
        val nodes = List(150) { index -> PhoneUiNode(pkg, resourceId = "control$index",
            text = "\"\\\n".repeat(50), clickable = true, bounds = PhoneBounds(0, index, 100, index + 1)) }
        val summary = PhoneUiSnapshot(Long.MAX_VALUE, pkg, nodes, frame, capturedAtMs = 0).summary()
        assertTrue(summary.shown.isNotEmpty())
        assertTrue(summary.payload.length <= 3000)
    }

    @Test fun launchNamesAndForegroundIdentityAreNotInferredFromAShortClass() {
        assertTrue(PhoneLaunchRequest.matchesInstalledApp("打开WhatsApp", "WhatsApp", pkg))
        assertNull(PhoneLaunchRequest.foregroundComponent(".MainActivity", pkg))
        assertNull(PhoneLaunchRequest.foregroundComponent("MainActivity", pkg))
        assertNull(PhoneLaunchRequest.foregroundComponent("com.other.app/.MainActivity", pkg))
        assertEquals("$pkg/.MainActivity", PhoneLaunchRequest.foregroundComponent("$pkg/.MainActivity", pkg))
        assertEquals("$pkg/$pkg.MainActivity", PhoneLaunchRequest.foregroundComponent("$pkg.MainActivity", pkg))
    }

    @Test fun aScrollableContainerKeepsItsOwnEntryAndDoesNotConsumeFeedText() {
        val list = PhoneUiNode(pkg, clickable = true, scrollable = true, bounds = frame)
        val text = PhoneUiNode(pkg, text = "结果文字", parentRef = 1, bounds = PhoneBounds(0, 100, 100, 200))
        val button = PhoneUiNode(pkg, text = "详情", clickable = true, parentRef = 1, bounds = PhoneBounds(0, 200, 100, 300))
        val summary = state(list, text, button).summary()
        assertEquals(setOf(1, 2, 3), summary.candidates.map { it.ref }.toSet())
    }
}
