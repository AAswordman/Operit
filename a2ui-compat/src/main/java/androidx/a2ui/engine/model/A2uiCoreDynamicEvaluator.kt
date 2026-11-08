/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.a2ui.engine.model

import androidx.a2ui.model.protocol.A2uiDataPath
import androidx.a2ui.model.protocol.A2uiExecutionContext

/** Evaluates dynamic payload by resolving data model bindings and client functions */
internal interface A2uiCoreDynamicEvaluator {
    /**
     * Evaluates a dynamic [payload] using [dataPath] for relative path resolution.
     *
     * This function supports evaluating complex, deeply-nested dynamic structures (including
     * [Map]s, [List]s, and primitive values). It scans for and resolves:
     * - **Path bindings**: Maps containing only a single `"path"` key, e.g., `{"path":
     *   "user/name"}`. These are resolved to actual values using
     *   [A2uiExecutionContext.resolveValue].
     * - **Client function calls**: Maps containing a `"call"` key and optional `"args"` key, e.g.,
     *   `{"call": "add", "args": {"a": 1, "b": 2}}`. Arguments themselves can be nested dynamic
     *   payloads, which are fully resolved before the catalog function is executed.
     * - **Nested payloads**: Plain Maps and Lists, whose nested elements are recursively evaluated
     *   while maintaining their structural layout.
     *
     * @param dataPath base data path used to resolve relative paths during evaluation
     * @param payload dynamic payload to evaluate, which can be a [Map], [List], or a primitive
     *   literal
     * @param executionContext context to use during evaluation
     * @return fully evaluated and resolved payload
     */
    fun evaluate(
        dataPath: A2uiDataPath,
        payload: Any?,
        executionContext: A2uiExecutionContext,
    ): Any?
}

/**
 * Default implementation of [A2uiCoreDynamicEvaluator].
 *
 * This implementation uses a stack-based iterative approach to evaluate deeply nested dynamic
 * payloads without risking stack overflow.
 */
internal object A2uiCoreDynamicEvaluatorImpl : A2uiCoreDynamicEvaluator {
    @Suppress("UNCHECKED_CAST")
    override fun evaluate(
        dataPath: A2uiDataPath,
        payload: Any?,
        executionContext: A2uiExecutionContext,
    ): Any? {
        if (payload !is Map<*, *> && payload !is List<*>) {
            return payload
        }

        val resultStack = mutableListOf<Any?>()
        val workStack = mutableListOf<Any?>()

        workStack.add(payload)

        while (workStack.isNotEmpty()) {
            val success =
                when (val current = workStack.removeAt(workStack.lastIndex)) {
                    is Map<*, *> ->
                        processMapNode(
                            current as Map<String, *>,
                            dataPath,
                            workStack,
                            resultStack,
                            executionContext,
                        )
                    is List<*> -> {
                        processListNode(current, workStack, resultStack)
                        true // Cannot fail
                    }
                    is Frame -> processFrameNode(current, resultStack, executionContext)
                    is MapFrame -> {
                        processMapFrameNode(current, resultStack)
                        true // Cannot fail
                    }
                    is ListFrame -> {
                        processListFrameNode(current, resultStack)
                        true // Cannot fail
                    }
                    else -> {
                        resultStack.add(current)
                        true // Cannot fail
                    }
                }
            if (!success) {
                // Stopping mid-evaluation due to failure
                return null
            }
        }
        return resultStack.first()
    }

    /**
     * Processes a map node which could be a path, a function call, or just a raw map.
     *
     * If it's a path node, it resolves the path and adds the result to [resultStack]. If it's a
     * call node, it schedules the function execution by pushing a [Frame] and its arguments onto
     * the [workStack]. Otherwise, it treats the map as a plain map and schedules nested evaluation
     * of its values.
     *
     * @return true if the node is processed successfully, otherwise false
     */
    private fun processMapNode(
        mapNode: Map<String, *>,
        dataPath: A2uiDataPath,
        workStack: MutableList<Any?>,
        resultStack: MutableList<Any?>,
        executionContext: A2uiExecutionContext,
    ): Boolean {
        if (mapNode.isEmpty()) {
            resultStack.add(mapNode)
            return true
        }

        when (tryProcessPathNode(mapNode, dataPath, workStack, resultStack, executionContext)) {
            PathNodeProcessingResult.PROCESSED -> return true
            PathNodeProcessingResult.FAILED -> return false
            PathNodeProcessingResult.NOT_MATCHED -> {}
        }

        if (tryProcessCallNode(mapNode, workStack, executionContext)) {
            return true
        }

        val keys = mapNode.keys.toTypedArray()
        workStack.add(MapFrame(mapNode, keys))
        for (i in keys.indices.reversed()) {
            workStack.add(mapNode[keys[i]])
        }
        return true
    }

    /** Schedules evaluation of [listNode] elements onto [workStack] and [resultStack]. */
    private fun processListNode(
        listNode: List<*>,
        workStack: MutableList<Any?>,
        resultStack: MutableList<Any?>,
    ) {
        if (listNode.isEmpty()) {
            resultStack.add(listNode)
            return
        }

        workStack.add(ListFrame(listNode))
        for (i in listNode.indices.reversed()) {
            workStack.add(listNode[i])
        }
    }

    /** Resolves a [mapNode] to [resultStack] if it is a path node. */
    private fun tryProcessPathNode(
        mapNode: Map<*, *>,
        dataPath: A2uiDataPath,
        workStack: MutableList<Any?>,
        resultStack: MutableList<Any?>,
        executionContext: A2uiExecutionContext,
    ): PathNodeProcessingResult {
        val path = mapNode[KEY_PATH] as? String

        if (path == null || mapNode.size != 1) return PathNodeProcessingResult.NOT_MATCHED

        val resolvedPath = dataPath / path
        val result = executionContext.resolveValue(resolvedPath)
        // If a path cannot be resolved:
        // - In plain properties (maps/lists) or functions requiring resolved arguments, evaluation
        //   fails so the component can wait in a loading state until the data model is populated.
        // - In functions that accept unresolved arguments (such as `required` opting in
        //   via [A2uiFunctionDefinition.acceptsUnresolvedArguments]), null is pushed onto the
        //   result stack and omitted from the evaluated arguments map passed to the function.
        val parentFrame = workStack.lastOrNull { it is Frame } as? Frame
        if (result == null && parentFrame?.acceptsUnresolvedArguments != true) {
            return PathNodeProcessingResult.FAILED
        }
        resultStack.add(result)
        return PathNodeProcessingResult.PROCESSED
    }

    /**
     * Schedules a [mapNode] function execution onto [workStack] if it is a call node. Returns true
     * if processed.
     */
    @Suppress("UNCHECKED_CAST")
    private fun tryProcessCallNode(
        mapNode: Map<String, *>,
        workStack: MutableList<Any?>,
        executionContext: A2uiExecutionContext,
    ): Boolean {
        val call = mapNode[KEY_CALL] as? String ?: return false

        for (key in mapNode.keys) {
            if (
                key != KEY_CALL &&
                    key != KEY_ARGS &&
                    key != KEY_CALLABLE_FROM &&
                    key != KEY_RETURN_TYPE
            ) {
                return false
            }
        }

        val argsMap = mapNode[KEY_ARGS] as? Map<String, *>
        if (argsMap == null && mapNode[KEY_ARGS] != null) {
            return false
        }

        val acceptsUnresolvedArguments =
            executionContext.getFunctionDefinition(call)?.acceptsUnresolvedArguments == true

        if (argsMap.isNullOrEmpty()) {
            workStack.add(Frame(call, emptyArray(), acceptsUnresolvedArguments))
            return true
        }
        val keys = argsMap.keys.toTypedArray()
        workStack.add(Frame(call, keys, acceptsUnresolvedArguments))
        for (i in keys.indices.reversed()) {
            workStack.add(argsMap[keys[i]])
        }
        return true
    }

    /**
     * Executes function in [frame] using arguments from [resultStack] and pushes the result to the
     * [resultStack].
     */
    private fun processFrameNode(
        frame: Frame,
        resultStack: MutableList<Any?>,
        executionContext: A2uiExecutionContext,
    ): Boolean {
        val evaluatedArgs = LinkedHashMap<String, Any>(frame.keys.size)
        val startIndex = resultStack.size - frame.keys.size

        for (i in frame.keys.indices) {
            val argValue = resultStack[startIndex + i]
            if (argValue != null) {
                evaluatedArgs[frame.keys[i]] = argValue
            }
        }

        if (frame.keys.isNotEmpty()) {
            resultStack.subList(startIndex, resultStack.size).clear()
        }

        val funcResult =
            executionContext.executeFunction(frame.callName, evaluatedArgs) ?: return false
        resultStack.add(funcResult)
        return true
    }

    /** Reconstructs evaluated map from [resultStack] using keys from [frame]. */
    private fun processMapFrameNode(frame: MapFrame, resultStack: MutableList<Any?>) {
        val startIndex = resultStack.size - frame.keysInStackOrder.size

        var changed = false
        for (i in frame.keysInStackOrder.indices) {
            val key = frame.keysInStackOrder[i]
            val originalValue = frame.original[key]
            val evaluatedValue = resultStack[startIndex + i]
            if (originalValue !== evaluatedValue) {
                changed = true
                break
            }
        }

        val result =
            if (changed) {
                val evaluatedMap = LinkedHashMap<String, Any?>(frame.keysInStackOrder.size)
                for (i in frame.keysInStackOrder.indices) {
                    evaluatedMap[frame.keysInStackOrder[i]] = resultStack[startIndex + i]
                }
                evaluatedMap
            } else {
                frame.original
            }

        if (frame.keysInStackOrder.isNotEmpty()) {
            resultStack.subList(startIndex, resultStack.size).clear()
        }

        resultStack.add(result)
    }

    /** Reconstructs evaluated list from [resultStack] using size from [frame]. */
    private fun processListFrameNode(frame: ListFrame, resultStack: MutableList<Any?>) {
        val size = frame.original.size
        val startIndex = resultStack.size - size

        var changed = false
        for (i in 0 until size) {
            val originalValue = frame.original[i]
            val evaluatedValue = resultStack[startIndex + i]
            if (originalValue !== evaluatedValue) {
                changed = true
                break
            }
        }

        val result =
            if (changed) {
                val evaluatedList = ArrayList<Any?>(size)
                for (i in startIndex until resultStack.size) {
                    evaluatedList.add(resultStack[i])
                }
                evaluatedList
            } else {
                frame.original
            }

        if (size > 0) {
            resultStack.subList(startIndex, resultStack.size).clear()
        }

        resultStack.add(result)
    }

    /**
     * Scheduled function call waiting for execution. Tracks the function name [callName], ordered
     * argument parameter [keys], and whether the function [acceptsUnresolvedArguments].
     */
    private class Frame(
        val callName: String,
        val keys: Array<String>,
        val acceptsUnresolvedArguments: Boolean,
    )

    /**
     * Scheduled plain map waiting for its values to be evaluated. Tracks the map
     * [keysInStackOrder].
     */
    private class MapFrame(val original: Map<String, *>, val keysInStackOrder: Array<String>)

    /** Scheduled list waiting for its elements to be evaluated. Tracks the original [List]. */
    private class ListFrame(val original: List<*>)

    /** Indicates the outcome of path node processing. */
    private enum class PathNodeProcessingResult {
        /** The node does not match the expected path format. */
        NOT_MATCHED,

        /** The path resolved successfully. */
        PROCESSED,

        /** The path resolution failed. */
        FAILED,
    }

    private const val KEY_PATH = "path"
    private const val KEY_CALL = "call"
    private const val KEY_ARGS = "args"
    private const val KEY_CALLABLE_FROM = "callableFrom"
    private const val KEY_RETURN_TYPE = "returnType"
}
