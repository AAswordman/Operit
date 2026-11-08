package com.ai.assistance.operit.ui.common.composedsl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Compose DSL 的界面树在每次 JS 状态更新后都会整体重建。
 *
 * LazyColumn/LazyRow 的 LazyListState 只被 remember 在当前的组合位置里，若列表节点在外层
 * 条件分支、页面切换或对话框重建时离开组合，状态就会连同组合一起被丢弃，滚动位置回到顶部。
 *
 * 这个 store 由 DSL 宿主（页面与对话框）持有，生命周期长于界面树本身；列表渲染器按稳定的
 * 节点身份从 store 取回同一个 LazyListState，从而让普通状态更新、弹层重建和页面切换不再丢失
 * 滚动位置。
 *
 * 节点身份优先使用 DSL 显式传入的 key；没有 key 时退回节点路径。显式 key 用于区分同一个
 * 页面里可能共享路径的多个视图（例如世界书的列表页与编辑页）。
 */
internal class ComposeDslScrollStateStore(
    restoredStates: Map<String, Pair<Int, Int>> = emptyMap()
) {
    private val lazyListStates =
        restoredStates.mapValuesTo(mutableMapOf()) { (_, position) ->
            LazyListState(position.first, position.second)
        }

    fun lazyListState(key: String): LazyListState = lazyListStates.getOrPut(key) { LazyListState() }

    fun saveState(): Map<String, List<Int>> =
        lazyListStates.mapValues { (_, state) ->
            listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }

    companion object {
        val Saver: Saver<ComposeDslScrollStateStore, Map<String, List<Int>>> =
            Saver(
                save = { store -> store.saveState() },
                restore = { saved ->
                    ComposeDslScrollStateStore(
                        restoredStates =
                            saved.mapValues { (_, position) ->
                                position[0] to position[1]
                            }
                    )
                }
            )
    }
}

internal val LocalComposeDslScrollStateStore =
    compositionLocalOf<ComposeDslScrollStateStore?> { null }

@Composable
internal fun rememberComposeDslScrollStateStore(routeInstanceId: String): ComposeDslScrollStateStore =
    rememberSaveable(routeInstanceId, saver = ComposeDslScrollStateStore.Saver) {
        ComposeDslScrollStateStore()
    }

/**
 * 计算列表滚动状态的稳定身份。
 *
 * key 中包含节点路径和节点类型，避免不同位置的列表共用同一个状态；显式 key 参与身份计算，
 * 让 DSL 可以给同一路径下的不同视图声明独立的滚动位置。
 */
internal fun composeDslLazyListStateKey(
    parentNodePath: String,
    nodeType: String,
    nodeKey: Any?
): String {
    val normalizedType = normalizeToken(nodeType)
    val explicitKey = nodeKey?.toString()?.trim()?.ifBlank { null }
    return if (explicitKey != null) {
        "$parentNodePath:$normalizedType:key:$explicitKey"
    } else {
        "$parentNodePath:$normalizedType"
    }
}

@Composable
internal fun rememberComposeDslLazyListState(
    nodePath: String,
    nodeType: String,
    nodeKey: Any?
): LazyListState {
    val stateKey = composeDslLazyListStateKey(nodePath, nodeType, nodeKey)
    val store = LocalComposeDslScrollStateStore.current
    return if (store != null) {
        store.lazyListState(stateKey)
    } else {
        rememberSaveable(stateKey, saver = LazyListState.Saver) { LazyListState() }
    }
}
