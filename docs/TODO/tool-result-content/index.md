---
fork: https://github.com/3316891527/Operit
branch: fix/thinking-tag-meta-display
pr: https://github.com/AAswordman/Operit/pull/1278
status: implemented
---

# 工具结果正文与卡片边界

正文中的 content 结束标签示例会让原有非贪婪提取提前结束。结构化消息显示时又拼回整段字符串，已识别的工具块可能因前置文字而被 Markdown 分段器当成普通文本。

本次在现有 sections 方案上统一正文提取和静态节点入口，并同步 Kotlin、原生流式工具标签的起始条件。数据库及聊天归档字段不增加，现有分组、详情、复制和文件差异组件继续使用。

- 正文完整性：单个工具结果块内移除外层 content 包裹，保留内部 XML、代码、CDATA 和同名标签
- 显示一致性：工具 section 直接生成 XML 卡片节点，代码示例保持正文；流式工具标签允许紧接普通文字
- 历史记录：raw 完整时恢复旧 sections 中被截短的工具正文，保留旧裸正文和自闭合结果的读取语义
- 验证：补充 JVM 和 Android 原生用例；仓库 Android Tests、Android Build 在推送后派发，设备用例尚未执行

实现位置、预期行为和全量回归清单见 [实现与验证](./01-implementation.md)。

最新完整固定回归、可选实时检查和用户设备判定见[设备回归](./04-device-regression.md)，输入与可填写结果表位于 `fixtures/`。
