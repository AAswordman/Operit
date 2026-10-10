package com.ai.assistance.operit.ui.common.markdown.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf

internal class MarkdownCardStateStore(
    val nodeKeys: List<String>,
    val values: SnapshotStateMap<String, Any?>,
)

internal val LocalMarkdownCardStateStore = staticCompositionLocalOf<MarkdownCardStateStore?> { null }
internal val LocalMarkdownCardNodeIndex = staticCompositionLocalOf<Int?> { null }

/** 卡片状态归属于消息和内容身份，不随整条渲染与分块渲染的切换而丢失。 */
@Composable
internal fun <T> rememberMarkdownCardValue(slot: String, initial: T): MutableState<T> {
    val store = LocalMarkdownCardStateStore.current
    val index = LocalMarkdownCardNodeIndex.current
    val nodeKey = index?.let { store?.nodeKeys?.getOrNull(it) }
    if (store == null || nodeKey == null) {
        return rememberSaveable { mutableStateOf(initial) }
    }
    // 存储被复用时也响应默认值变化，已经保存的手动选择仍优先读取。
    return remember(store, nodeKey, slot, initial) {
        val key = "$nodeKey/$slot"
        object : MutableState<T> {
            @Suppress("UNCHECKED_CAST")
            override var value: T
                get() = if (store.values.containsKey(key)) store.values[key] as T else initial
                set(value) { store.values[key] = value }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
