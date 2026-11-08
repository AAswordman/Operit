package com.ai.assistance.operit.data.updates.patch

import java.util.ArrayDeque

/** 按 APK 哈希搜索最短有效路径，避免贪选较新补丁进入无法到达目标的分支。 */
internal fun <T> resolvePatchPath(
    steps: Collection<T>,
    currentSha: String,
    targetSha: String,
    maxSteps: Int,
    baseSha: (T) -> String,
    nextSha: (T) -> String,
): List<T>? {
    if (currentSha == targetSha) return emptyList()
    val outgoing = steps.groupBy(baseSha)
    val queue = ArrayDeque<Pair<String, List<T>>>()
    val visited = mutableSetOf(currentSha)
    queue.add(currentSha to emptyList())
    while (queue.isNotEmpty()) {
        val (sha, path) = queue.removeFirst()
        if (path.size >= maxSteps) continue
        for (step in outgoing[sha].orEmpty()) {
            val next = nextSha(step)
            if (!visited.add(next)) continue
            val nextPath = path + step
            if (next == targetSha) return nextPath
            queue.add(next to nextPath)
        }
    }
    return null
}
