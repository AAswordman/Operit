---
fork: https://github.com/yoruuuchan/Operit
branch: fix/issue-1405-nul-message-content
status: implementation-complete
---

# 含 NUL 消息的完整读取修复

## 原状

PDF 提取文本可能带有 `U+0000`。SQLite 的 `LENGTH`/`SUBSTR` 把 TEXT 当字符处理，并在第一个 NUL 处停止，所以一条实际含 82,675 字符的消息会被报告为 5,296 字符，首段也只返回 NUL 之前的内容。

`ChatContentDao` 用这两个函数判断是否需要分段并拼接正文。含 NUL 的消息因此被当作短消息直接返回前半段，切换会话后正文消失，后续模型请求也缺少这段历史。消息变体走同一套逻辑，问题一致。

## 修改意图

不修改数据库中的历史内容，也不缩减 `CursorWindow` 保护范围。把消息与消息变体的分段查询改为按 BLOB 字节读取：`LENGTH(CAST(content AS BLOB))` 给出真实字节数，`SUBSTR(CAST(content AS BLOB), ...)` 按字节偏移切块。只有所有字节块拼接完成后才按 UTF-8 解码，避免多字节字符被块边界切开。

同时在 PDF 提取的写盘边界剔除 NUL，避免新的工具结果继续写入不可见字符。其余字符保持不变。

## 作用域

- `ChatContentDao` 的消息与消息变体行查询、分段查询和重组逻辑
- `DocumentConversionUtil.extractTextFromPdf` 的 PDFBox 直出路径与 OCR 路径
- 新增 JVM Room + sqlite-jdbc 测试，覆盖短消息、多个 NUL、跨分块的长消息、多字节边界、消息变体与各读取入口
- 数据库实体、表结构、版本号和归档 JSON 结构保持不变

## 不在本次范围

- `MessageDao` 的滚动定位预览与聊天列表全文搜索仍然使用 `LENGTH`/`SUBSTR`/`LIKE`，含 NUL 时预览偏短、搜索可能漏匹配。它们只影响导航条与列表筛选，不影响会话正文和模型历史，本次不改变其 SQL 语义。
- `getSelectedContentCharacterCountsByChat` 仍按字符统计，只用于导出流式阈值与进度显示。

## 验收条件

- `AB + NUL + CD` 重新读取后完整等于原字符串
- 含多个 NUL 的短消息完整返回
- 超过 65,536 字节、且多字节字符跨越分块边界的长消息完整返回
- 消息变体的短内容与长内容同样完整返回
- 普通消息和长消息仍通过一次首段查询 + 按需分段读取完成，单行不超过 64 KiB
- PDF 提取结果不再包含 `U+0000`，其余文本保持不变
- `:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过

实现已完成。[DONE]
