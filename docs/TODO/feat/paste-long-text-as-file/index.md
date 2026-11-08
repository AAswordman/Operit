---
fork: local workspace
---

# 粘贴超长文本转文件

聊天输入框会直接保留粘贴的全部文本。大量文本会使编辑器难以使用，也可能影响界面响应。

本次改动仅处理聊天输入框的粘贴增量：当纯文本粘贴长度超过用户设定的阈值时，创建 UTF-8 文本附件并清空该次粘贴产生的输入内容。手动附件、普通输入和未达到阈值的粘贴维持现有行为。

作用域包括聊天输入控件、持久化设置、临时附件创建和中英文资源。不涉及消息协议或已有附件处理流程的替换。

- [01-settings-and-paste-conversion.md](01-settings-and-paste-conversion.md)
- [02-localization-and-verification.md](02-localization-and-verification.md)
