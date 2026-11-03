package com.ai.assistance.operit.core.workflow.runtime

import com.ai.assistance.operit.core.workflow.model.ExecuteNode
import com.ai.assistance.operit.core.workflow.model.WorkflowNode


/** 没有自定义名称的执行节点用真实动作标识，界面不使用节点序号或内部 ID。 */
internal fun WorkflowNode.readableWorkflowName(): String = when {
    name.isNotBlank() -> name.trim()
    this is ExecuteNode && actionType.isNotBlank() -> actionType
    else -> type
}

/** 收集实际异常原因链，完整堆栈仍由运行日志保存。 */
internal fun workflowFailureCause(error: Throwable): String {
    val visited = mutableSetOf<Throwable>()
    return generateSequence(error) { it.cause }
        .takeWhile { visited.add(it) }
        .take(12)
        .joinToString("\n") { it.toString() }
}

/** 保留具体错误和原因链，过滤 JVM、JavaScript 与 Python 的堆栈行。 */
internal fun readableWorkflowFailure(reason: String?, nodeNames: Map<String, String> = emptyMap()): String {
    val exceptionPrefix = Regex("^(?:(?:Caused by|Suppressed):\\s*)?(?:[A-Za-z_$][\\w$]*\\.)*(?:[A-Za-z_$][\\w$]*)?(?:Exception|Error)(?::\\s*|$)")
    val omittedFrames = Regex("^(?:\\.{3}|…)\\s*\\d+\\s+(?:more|common frames omitted)$")
    val pythonError = Regex("^(?:[A-Za-z_][\\w]*\\.)*[A-Z][\\w]*:")
    val output = mutableListOf<String>()
    var pythonTraceback = false
    reason.orEmpty().replace(Regex("\\u001B\\[[0-9;]*[A-Za-z]"), "").lineSequence().forEach { original ->
        var line = original.trim()
        if (line.startsWith("Traceback (most recent call last)")) {
            pythonTraceback = true
            return@forEach
        }
        if (pythonTraceback) {
            if (!pythonError.containsMatchIn(line)) return@forEach
            pythonTraceback = false
        }
        if (line.isBlank() || omittedFrames.matches(line) ||
            line.startsWith("During handling of the above exception") ||
            line.startsWith("The above exception was the direct cause") ||
            (line.startsWith("at ") && (line.contains('(') || Regex(":\\d+").containsMatchIn(line)))) return@forEach
        while (exceptionPrefix.containsMatchIn(line)) line = line.replaceFirst(exceptionPrefix, "").trim()
        line = line.removePrefix("Caused by:").removePrefix("Suppressed:").trim()
        nodeNames.entries.sortedByDescending { it.key.length }.forEach { (id, name) ->
            if (name.isNotBlank()) {
                val reference = Regex("(?i)(引用节点\\s*|引用的节点\\s*|node\\s+|node[:#]\\s*)${Regex.escape(id)}(?![\\w-])")
                line = reference.replace(line) { it.groupValues[1] + name }
                if (id.matches(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) line = line.replace(id, name)
            }
        }
        if (line.isNotBlank() && line !in output) output += line
    }
    return output.take(12).joinToString("\n").take(2000)
}
