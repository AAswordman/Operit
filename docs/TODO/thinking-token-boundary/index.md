---
fork: https://github.com/3316891527/Operit
pr: https://github.com/AAswordman/Operit/pull/1278
---

# 思考 token 边界

旧运行时标记只靠标签名闭合，思考正文再次写出相同结束标签时会提前拆出正文。本次在已有 message sections 方案上迭代，使用每段随机 token，恢复新生成的 think 标签名，并同步提供商、Kotlin 与原生分段器、过滤和显示入口。

实现范围与回归用例见 [思考片段的 token 边界](../../doc-src/feature-protocol/thinking_token_boundary.md)。

可视化编辑器保存新增的思考标签时，自动补入随机 token 并重组匹配的结束标记。解析和重组逻辑位于 MessageEditorContent，复用 ThinkingMarkup；已有 token、未闭合状态和文本间空白在模式切换时保持，普通 XML 仍使用原来的闭合语法。MessageEditorContentTest 覆盖这些编辑路径。

代码与回归用例已完成；构建和测试结果由该分支的 Android Build、Android Tests 工作流记录。

[DONE]
