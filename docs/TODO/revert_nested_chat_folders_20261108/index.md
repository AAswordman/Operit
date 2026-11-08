---
title: 撤回未发布的嵌套对话目录以恢复 Operit2 导入契约
status: ready_for_review
fork: https://github.com/luojiaping/Operit
date: 2026-11-08
---

# 撤回未发布的嵌套对话目录

[返回总计划](../README.md)

## 原因与兼容性

PR #1472 通过 #1476 合入 dev，将 Room v21 升级到 v22、JSON 导出格式由 v2 升级到 v4，并将运行时分组切换为独立文件夹实体和 folderId。当前 Operit2 的旧版 Room 快照导入桥仅识别 v10、v20、v21，聊天导入仍读取 group 字段，v22 数据库会被明确拒绝。

维护者确认该变化仅存在于 dev 和 CNB 测试包，未正式发布。核对时 dev 为 `03ca58ec3`，main 为 `fd931441a`，main 仍使用 Room v21；目录功能提交不在 main 历史中。

本次撤回该未发布方案，恢复 Room v21、原分组数据结构、JSON v2 导出和既有导入路径。已被测试包升级为 v22 的数据库不属于正式发布兼容范围，不能直接由 v21 应用打开；本改动不改写现有测试数据库，也不新增自动降版本或清库行为。

## 实施范围

1. 反向应用 #1472 的 `614b022a2`、`30aee703b`、`8b9d85393`，同步恢复 Android、Web、工具参数、外部聊天接口及导入导出。
2. 一并撤回依赖目录树的紧凑布局提交 `7fc3da9c6` 和验收记录 `66be5a9c4`，移除该方案专属代码、测试及文档。
3. 保留独立的 PromptHook 数据库单条快照查询，适配恢复后的 group 数据模型。
4. 检查 GPT Image 2.6 GIF 与市场默认关闭精选的代码未被这次反向提交改变。
5. 在基于最新 upstream/dev 的干净分支中形成一个签署提交，推送个人 Fork 并创建目标 dev 的 PR。

## 验证安排

静态核对数据库声明、迁移注册、实体字段、归档版本、被删除符号的引用和差异纯净性。编译、构建、测试、真机及实际 Operit2 导入均未在本轮执行，结果不得由源码检查替代。

## 实施结果

已反向应用上述五个提交，撤除独立目录实体、v21→v22 迁移、目录专属测试和文档，并恢复 Android、Web、工具及归档各层的旧接口。唯一冲突位于 ChatHistoryManager：保留独立的单条持久化查询，改为直接转换 v21 ChatEntity，不再访问 folderId。

与目录功能引入前的 `1957624df` 比较，忽略恢复代码时清理的行尾空白，AppDatabase、ChatDao、ChatEntity、ChatHistory 和 OperitChatArchive 完全一致；app 和 web-chat 的其余差异仅为既有 PromptHook 修正、对应单条查询和三个保留用例。目录专属符号引用检查无残留，GPT Image 与市场文件相较当前 dev 无差异。

[DONE] 反向变更、PromptHook 适配与静态核对完成，交由维护者通过 PR 审阅合并。
