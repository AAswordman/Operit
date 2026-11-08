package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.uidsl.ToolPkgComposeDslRenderResult
import java.util.ArrayDeque

internal fun collectComposeDslActionIds(result: ToolPkgComposeDslRenderResult): Set<String> {
    val actionIds = linkedSetOf<String>()
    val pending = ArrayDeque<Any>()
    pending.add(result.tree)
    pending.add(result.state)
    pending.add(result.memo)
    // 记忆中缓存的节点同样可能持有仍会复用的回调，不能只遍历当前可见树。
    while (pending.isNotEmpty()) {
        when (val value = pending.removeLast()) {
            is ToolPkgComposeDslNode -> {
                pending.add(value.props)
                value.children.forEach { pending.add(it) }
                value.slots.values.forEach { pending.add(it) }
            }
            is Map<*, *> -> {
                (value["__actionId"] as? String)?.let { actionIds.add(it) }
                value.values.forEach { if (it != null) pending.add(it) }
            }
            is Iterable<*> -> value.forEach { if (it != null) pending.add(it) }
        }
    }
    return actionIds
}
