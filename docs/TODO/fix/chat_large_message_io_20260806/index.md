---
fork: https://github.com/luojiaping/Operit
branch: fix/chat-large-message-io
status: implementation-complete
---

# 超大聊天消息读取修复

## 原状

聊天消息和消息变体通过 `SELECT *` 直接装入 Android `CursorWindow`。单条 `content` 超过窗口容量时会抛出 `SQLiteBlobTooBigException`。聊天导出因此中断；对话窗口查询又把异常转换为空列表，最终表现为切换对话后内容空白。

## 修改意图

完整保留现有数据库内容和导出格式，不截断消息，也不调整设备相关的 `CursorWindow` 容量。读取查询只返回固定大小的首段文本和完整字符数，超出部分在同一 Room 事务内继续分段读取并重组。

## 作用域

- 新增 `ChatContentDao`，统一读取消息及消息变体的完整文本
- 对话展示、运行时上下文、消息变体操作、长期记忆和聊天导出改用安全读取路径
- 移除 `MessageDao` 与 `MessageVariantDao` 中会直接返回完整大文本行的查询
- 导出失败时删除已经创建但尚未完成的文件
- 数据库实体、表结构、版本号和归档 JSON 结构保持不变

## 验收条件

- 包含超过 Android `CursorWindow` 单行容量消息的对话可以正常切换和显示
- JSON 导出可完整保留该消息及其变体，导入后内容一致
- 普通消息继续通过一次查询完成读取
- 导出异常不会在备份目录留下截断文件

实现已完成。[DONE]
