# MNN Tool Call 调研记录

更新时间：2026-03-17

## 背景

当前目标不是在 app 层额外拼一套 prompt 协议，而是确认：

1. MNN 官方是否已经给出关键设计思路。
2. 是否存在不退化的接法。
3. 如果要接 `tool call`，正确的接入层应该在哪里。

---

## 已检查的公开资料

### 官方公开资料

- MNN 总 README  
  https://github.com/alibaba/MNN
- MNN-LLM 总说明  
  https://github.com/alibaba/MNN/blob/master/transformers/README.md
- MNN docs 中的 LLM 页面  
  https://github.com/alibaba/MNN/blob/master/docs/transformers/llm.md
- MNN 官方 Wiki 的 `llm` 页面  
  https://github.com/alibaba/MNN/wiki/llm
- MNN 官方 Android Chat App README  
  https://github.com/alibaba/MNN/blob/master/apps/Android/MnnLlmChat/README.md

### 相关上游组件

- Minja 项目  
  https://github.com/google/minja

### 外网核对结果

- 官方 Wiki 的 `llm` 页面当前明确写着 `This document is a placeholder.`，不能作为 tool call 接线文档使用。
- `minja` README 明确说明它的 `chat_template` 会对消息历史做规范化，并处理不同模板对 `tool_calls.function.arguments` 的不同期待。
- 未发现一篇官方公开文章或 README，直接覆盖 `MNN tool call + Android/JNI` 接入细节。

---

## 本地源码检查范围

### MNN 上游源码

- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/prompt.cpp`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/prompt.hpp`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/include/llm/llm.hpp`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/minja/chat_template.cpp`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/minja/chat_template.hpp`

### 官方 Android Chat App

- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/app/src/main/cpp/llm_mnn_jni.cpp`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/README.md`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/README_CN.md`

---

## 结论

### 1. 官方公开文档没有直接讲 `tool call + JNI + Android` 的接法

公开文档主要覆盖：

- 模型导出
- 引擎编译
- 运行时推理
- Android Chat App 的使用和发布说明

但没有一篇文档直接说明：

- assistant 历史中的 `tool_calls` 怎么传
- `role="tool"` 的工具结果怎么传
- Android/JNI 层如何把结构化消息交给 MNN 内部模板

另外，官方 Wiki 的 `llm` 页面当前仍是占位页，不是可直接照抄的接入文档。

---

### 2. 官方真正的关键思路在源码，不在文档正文

关键点不在 README，而在 `minja` 和 MNN 对 `minja` 的封装里。

从 `chat_template.hpp/.cpp` 可以确认，MNN 内部模板链路本身已经理解：

- `inputs.tools`
- assistant 消息上的 `tool_calls`
- `role == "tool"` 的工具响应
- `tool_call_id`
- 针对不同模板差异的 polyfill

这说明：

- MNN 内部并不是没有 tool 能力。
- 真正的问题是公开入口没有把完整结构暴露出来。

---

### 3. 官方 Android Chat App 给出的关键思路是“完整历史提交”，不是手工拼 prompt

`MnnLlmChat` 的 `LlmSession.kt` 里有 `submitFullHistory(...)`，并且 JNI 对应入口直接接收完整历史列表。

这至少说明官方思路是：

- 不要只传一个拼好的长字符串。
- 聊天侧应该保留历史级别的结构，再交给底层处理。

这和“在 app 层往 system prompt 追加一段工具协议说明”是两种思路。

前者是结构化历史驱动，后者是纯文本 hack。

---

### 4. 当前 MNN 公开 `Prompt/Llm` 入口过薄，才是我们这里发生退化的根因

当前本地 checkout 中：

- `ChatMessage = pair<string, string>`
- `Prompt::applyTemplate(...)` 只接 `role/content`
- `Llm::response(...)` 也只接简单历史

这意味着即使底层 `minja` 支持 `tools/tool_calls/tool responses`，上层如果继续只走这条 API，也会丢掉结构信息。

结论：

- 退化不是因为 MNN internal tools 不存在。
- 退化是因为当前公开入口把消息降维了。

---

## 对我们实现的直接意义

### 错误方向

不应继续采用下面这种方案：

- app 层在 system prompt 里手工拼工具说明
- 要求模型按我们新定义的一段文本协议输出

原因：

- 这不是接入 MNN internal tools。
- 这会把问题重新退化成 prompt engineering。

### 正确方向

正确方向必须保留结构化消息，至少包括：

- `messages`
- `tools`
- assistant 历史上的 `tool_calls`
- tool 历史上的 `tool_call_id` / `content`

---

## 可行方案

### 方案 A：不改上游 MNN 源码，但在我们自己的 JNI 中直接接 MNN 内部模板

思路：

- Provider 先把内部 XML 历史转换成结构化 `messages_json + tools_json`
- JNI 直接调用 MNN 内部的 chat template 能力生成 prompt
- 再把 prompt 交给现有 tokenizer / generate 流程

优点：

- 不改上游 MNN 源码
- 仍然接的是 MNN internal tools 思路
- 不会退化成 prompt 拼接协议

代价：

- 我们自己的 native 层要承担一部分“structured messages -> internal template”桥接逻辑

### 方案 B：改上游 MNN 源码，给 `Prompt/Llm` 增加结构化消息入口

思路：

- 给 `Prompt` / `Llm` 增加 `messages_json + tools_json` 入口
- 让 JNI 直接调用新的公开 API

优点：

- 结构最干净
- JNI 更薄
- 更接近内部能力的自然暴露

代价：

- 要修改上游 MNN 源码

---

## 当前判断

截至 2026-03-17，公开资料能够给出的最关键思路是：

1. 官方推荐的总体方向是 chat template 驱动，而不是 app 层拼 prompt 协议。
2. `minja` 就是这条能力链的核心。
3. 官方 Android Chat App 也体现了“完整历史提交”的思路。
4. 真正缺的不是能力，而是我们当前接入点到 `minja` 之间的结构化消息桥接。

因此，如果目标是“不退化”，那就必须让结构化 `messages/tools/tool_calls/tool responses` 至少在 native 层保持住。

---

## 附：本地可直接查看的关键文件

- `<MNN_SOURCE_DIR>/README.md`
- `<MNN_SOURCE_DIR>/transformers/README.md`
- `<MNN_SOURCE_DIR>/docs/transformers/llm.md`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/minja/chat_template.cpp`
- `<MNN_SOURCE_DIR>/transformers/llm/engine/src/prompt.cpp`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/README.md`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/app/src/main/java/com/alibaba/mnnllm/android/llm/LlmSession.kt`
- `<MNN_SOURCE_DIR>/apps/Android/MnnLlmChat/app/src/main/cpp/llm_mnn_jni.cpp`
