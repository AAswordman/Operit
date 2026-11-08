package com.ai.assistance.operit.ui.features.extensions.packages.management

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.text.Collator
import java.util.Locale
import com.ibm.icu.text.Collator as IcuCollator

class PluginListOrderingTest {
    private fun entry(
        id: String,
        enabled: Boolean = false,
        size: Long = 0L,
        installed: Long = 0L,
        used: Long = 0L,
        name: String = id
    ) = PluginSortEntry(id, name, enabled, size, installed, used)

    private fun ids(entries: List<PluginSortEntry>) = entries.map { it.packageName }

    private fun withAndroidCollation(block: () -> Unit) {
        // Android 的 java.text.Collator 委托给 ICU，OpenJDK 的中文规则不是完整拼音排序。
        // 只替换测试中的排序器工厂，比较和强度设置都转交真实 ICU，业务排序仍调用原实现。
        val icuCollator = IcuCollator.getInstance(Locale.CHINA)
        val collator = Mockito.mock(Collator::class.java)
        Mockito.doAnswer { invocation ->
            icuCollator.strength = invocation.getArgument<Int>(0)
            null
        }.`when`(collator).setStrength(Mockito.anyInt())
        Mockito.`when`(collator.compare(Mockito.anyString(), Mockito.anyString())).thenAnswer { invocation ->
            icuCollator.compare(invocation.getArgument<String>(0), invocation.getArgument<String>(1))
        }
        Mockito.mockStatic(Collator::class.java).use { factory ->
            factory.`when`<Collator> { Collator.getInstance(Locale.CHINA) }.thenReturn(collator)
            block()
        }
    }

    @Test
    fun draggingUnusedPluginDoesNotPinItDuringAutomaticSorting() {
        val entries = listOf(entry("unused"), entry("recent", used = 200), entry("old", used = 100))
        val dragged = listOf("unused", "old", "recent")
        assertEquals(dragged, ids(sortPluginEntries(entries, PluginSortSelection(), dragged)))
        assertEquals(
            listOf("recent", "old", "unused"),
            ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.LAST_USED, true), dragged))
        )
    }

    @Test
    fun unrecordedTimesStayLastInBothDirections() {
        val entries = listOf(entry("unknown"), entry("new", installed = 200, used = 300), entry("old", installed = 100, used = 100))
        listOf(PluginSortMode.INSTALLED, PluginSortMode.LAST_USED).forEach { mode ->
            assertEquals(listOf("old", "new", "unknown"), ids(sortPluginEntries(entries, PluginSortSelection(mode), emptyList())))
            assertEquals(listOf("new", "old", "unknown"), ids(sortPluginEntries(entries, PluginSortSelection(mode, true), emptyList())))
        }
    }

    @Test
    fun installationUpdateMovesPluginWithoutChangingUsageOrder() {
        val entries = listOf(entry("updated", installed = 300, used = 50), entry("other", installed = 200, used = 100))
        assertEquals(listOf("updated", "other"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.INSTALLED, true), emptyList())))
        assertEquals(listOf("other", "updated"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.LAST_USED, true), emptyList())))
    }

    @Test
    fun nameSizeAndEnabledSortingIgnoreManualOrder() = withAndroidCollation {
        val entries = listOf(entry("c", size = 20), entry("b", enabled = true, size = 10), entry("a", size = 30))
        val manual = listOf("c", "b", "a")
        assertEquals(listOf("a", "b", "c"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME), manual)))
        assertEquals(listOf("c", "b", "a"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME, true), manual)))
        assertEquals(listOf("b", "c", "a"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.SIZE), manual)))
        assertEquals(listOf("a", "c", "b"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.SIZE, true), manual)))
        assertEquals(listOf("b", "a", "c"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.ENABLED), manual)))
        assertEquals(listOf("a", "c", "b"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.ENABLED, true), manual)))
    }

    @Test
    fun chineseNamesFollowPinyinOrder() = withAndroidCollation {
        val entries = listOf(entry("z", name = "文件"), entry("a", name = "安卓"), entry("b", name = "浏览器"))
        assertEquals(listOf("a", "b", "z"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME), emptyList())))
        assertEquals(listOf("z", "b", "a"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME, true), emptyList())))
    }

    @Test
    fun chineseNamesCompareWholeNameAndIgnoreManualOrder() = withAndroidCollation {
        // 标识与中文名称的顺序故意不同，避免只按标识排序也能通过。
        val entries = listOf(
            entry("a", name = "文件"), entry("z", name = "文本"),
            entry("b", name = "浏览器"), entry("c", name = "安卓")
        )
        val manual = listOf("a", "z", "b", "c")
        assertEquals(listOf("c", "b", "z", "a"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME), manual)))
        assertEquals(listOf("a", "z", "b", "c"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.NAME, true), manual)))
    }

    @Test
    fun filteredDragKeepsHiddenPluginsAndDropsDeletedIds() {
        assertEquals(
            listOf("c", "hidden", "a", "new"),
            mergeVisiblePluginOrder(
                allPluginNames = listOf("a", "hidden", "c", "new"),
                previousOrder = listOf("deleted", "a", "hidden", "c"),
                visibleOrder = listOf("c", "a")
            )
        )
    }

    @Test
    fun customReverseIncludesNewPlugins() {
        val entries = listOf(entry("a"), entry("b"), entry("new"))
        assertEquals(listOf("new", "a", "b"), ids(sortPluginEntries(entries, PluginSortSelection(PluginSortMode.CUSTOM, true), listOf("b", "a"))))
    }
}
