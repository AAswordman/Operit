# 步骤 4：流式解析

[返回总计划](index.md)

## 旧实现情况

### 标签名不接受冒号

四处解析器的标签名延续字符集都是 `[A-Za-z0-9_]`：

| 文件 | 位置 |
| --- | --- |
| `app/src/main/cpp/streamnative/plugins/StreamXmlPlugin.cpp` | `isTagNameContinuationChar` |
| `app/src/main/java/.../util/stream/plugins/StreamXmlPlugin.kt` | `predicate("xmlTagNameContinuation")` |
| `app/src/main/java/.../util/stream/StreamKmpGraph.kt` | `"xmlTagNameContinuation" -> "[A-Za-z0-9_]"` |
| `app/src/main/java/.../util/streamnative/NativeXmlSplitter.kt` | 正则 `<([A-Za-z][A-Za-z0-9_]*)[\s>]` |

`subagent:subagent_run` 在历史解析里会退化成 unknown/text，卡片不出现，模型回放时也认不出这是工具调用。

### 并行 tool_calls 被提前闭合

`OpenAIProvider` 在 `processToolCallsDelta` 里检测 index 切换，切到新 index 就 `handleToolSwitch` 闭合上一个。但 Chat Completions 的并行 `tool_calls` 会**交错**下发不同 index 的 `arguments` 增量，index 切换不代表前一个工具已结束。

坏结构实测形态：

```
<tool_46sh name="package_proxy">
  <param name="tool_name">subagent:subagent_run</param>
</tool_46sh>

<tool_oeII name="package_proxy">
</tool_oeII>
  <param name="call_purpose">第 4 路...</param>     ← 参数落在标签外
```

后果：界面显示裸露 `<param>` 文本（用户看到的"乱码"）；`package_proxy` 收到 0 个 `tool_name`，报 `Exactly one tool_name parameter is required`。

## 意图

让 `包:函数` 形式的工具标签能被正常切分与渲染，并让并行 `tool_calls` 的输出结构合法。

## 新实现

### 四处解析器加冒号

首字符仍必须字母（`WAIT_FIRST_LETTER`），保证 `:<abc>` 不会被当成标签开头。

```cpp
// StreamXmlPlugin.cpp
bool StreamXmlPlugin::isTagNameContinuationChar(char16_t c) {
    // ':' supports the reserved `package:function` tool tag form (e.g. subagent:subagent_run).
    return isAsciiLetter(c) || (c >= u'0' && c <= u'9') || c == u'_' || c == u':';
}
```

```kotlin
// stream/plugins/StreamXmlPlugin.kt
predicate("xmlTagNameContinuation") {
    (it in 'A'..'Z') || (it in 'a'..'z') || (it in '0'..'9') || it == '_' || it == ':'
}
```

```kotlin
// StreamKmpGraph.kt
"xmlTagNameContinuation" -> "[A-Za-z0-9_:]"
```

```kotlin
// NativeXmlSplitter.kt
val tagNameMatch = Regex("<([A-Za-z][A-Za-z0-9_:]*)[\\s>]").find(chunk)
```

四处必须同口径，改一处会让流式路径与历史解析路径产出不同的标签名。

### OpenAIProvider 按 index 缓冲

`ToolCallState` 加：

```kotlin
val xmlBuffers: MutableMap<Int, StringBuilder> = mutableMapOf()
fun getXmlBuffer(index: Int) = xmlBuffers.getOrPut(index) { StringBuilder() }
```

`clear()` 里一并清空。

四个改动点：

1. 开始标签写入缓冲区，不直接 `emitTag`
2. 参数事件追加到缓冲区（`appendToolEventsToBuffer`），不直接 `handleJsonEvents`
3. `processToolCallsDelta` 删掉 `handleToolSwitch` 与 `lastProcessedToolIndex`，只按 index 累积
4. `closeAllOpenToolCalls` 按 index 排序一次性输出，并补计 token

```kotlin
private suspend fun closeAllOpenToolCalls(state: StreamingState, emitter: StreamEmitter) {
    val indices = state.toolCallState.xmlBuffers.keys.sorted()
    if (indices.isEmpty()) return
    for (index in indices) {
        closeToolCallIfOpen(index, state, emitter)
        val buffer = state.toolCallState.xmlBuffers[index] ?: continue
        if (buffer.isNotEmpty()) {
            val block = buffer.toString()
            emitter.emitTag(block)
            tokenCacheManager.addOutputTokens(ChatUtils.estimateTokenCount(block))
            buffer.setLength(0)
        }
    }
}
```

`closeToolCallIfOpen` 里的 `requireNotNull(state.toolCallState.tagNames[index])` 不能改成 `getTagName(index)`——后者是 `getOrPut`，会静默生成新标签名，掩盖状态错乱。

代价：工具卡片要等流结束才出现（原来是流式渐显）。这是正确性的必要代价——参数交错时无法安全地提前闭合。

## 验收

- 历史消息里 `subagent:subagent_run` 标签正常渲染成卡片，不退化成文本
- 8 路并行委派：输出无裸露 `<param>`，每个 `package_proxy` 都收到完整 `tool_name` 与 `params`
- 单路委派行为不变

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
