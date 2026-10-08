# 文件引用格式与解析支持

## 当前行为

聊天和工作区文件入口支持文件 URI、来源参数、文本行号与 Markdown 标题定位。文件引用说明保留在本文，系统提示不再默认追加中英文文件引用规则，避免低频定位格式增加所有对话的提示词长度，也保留自定义系统模板对输出格式的控制。

本轮删除文件引用提示词类及其在 SystemPromptConfig 中的导入和共用追加入口。链接解析器、文件查看器和编辑器定位链路继续保留；已经生成的文件链接仍按原规则打开。

## 写法

标准 Markdown 链接由 `[显示名称]` 与 `(目标)` 连写组成。`file:///绝对路径` 表示文件 URI；`#L12` 表示从 1 开始计数的第 12 行，是 Operit 文件入口支持的定位约定。

引用时使用已经确认的真实路径和来源。通过 `environment` 查询参数明确指定 `android`、`linux` 或 `repo:仓库名称`，路径段和查询参数值分别 URL 编码并保留路径分隔符。普通网页使用原 HTTP/HTTPS 地址。

```markdown
[报告](file:///sdcard/Download/report.md?environment=android)
[源码第12行](file:///home/user/project/main.kt?environment=linux#L12)
[仓库源码第12行](file:///src/main.kt?environment=repo%3Ademo#L12)
[特殊字符文件](file:///sdcard/Download/notes%20%281%29%23%3F%25.md?environment=android)
```

以上为格式示例。路径中的空格、括号、`#`、`?`、`%` 对应 `%20`、`%28/%29`、`%23`、`%3F`、`%25`，避免它们成为 Markdown 或 URI 语法的一部分。

行号也支持路径末尾 `:12`、`?line=12` 和 `#line=12`。多个位置同时指定时依次采用 fragment、query、路径后缀。Markdown 文档预览还支持相对路径与标题锚点，详见 [07-markdown-preview-links.md](07-markdown-preview-links.md)。

## 验证范围

删除两个从中英文提示词提取示例的 JVM 用例。独立的特殊文件名与仓库名称编码用例移入 WorkspaceFileLinkTest，继续验证真实解析器；其余来源、路径、行号和标题定位用例保留。

源码检查确认 SystemPromptConfig 不再引用或追加该规则，WorkspaceFileLink.kt 和 WorkspaceMarkdownLink.kt 与修改前内容一致。Android Build 执行 `assembleDebug`，Android Tests 执行 `:app:testDebugUnitTest`；工作流派发与运行结果分别记录。

[DONE] 默认文件引用提示词及注入入口已删除，格式文档与独立解析回归保留。
