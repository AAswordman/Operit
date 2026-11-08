---
fork_repository: https://github.com/AAswordman/Operit.git
---

# 预编译删除思考正则

## 原状

`ChatUtils.removeThinkingContent` 和 `extractThinkingContent` 每次调用都 `toRegex()`。发送、重抽和窗口估算会对每条 AI 消息走一遍。长会话里主线程反复 ICU `Pattern.compile`，日志表现为删除思考 ANR。

## 意图

把相同规则的正则提升为对象级预编译实例。没有 think 或 search 标记的消息先做字符串包含判断，再决定是否走正则。删除思考的匹配结果不变。

## 作用域

- `ChatUtils.kt`
- 现有 `ChatUtilsTest`、`ChatUtilsThinkingTest`、`ChatUtilsThinkingEdgeTest` 覆盖的行为保持原样

## 验收

- 闭合和未闭合的 think、thinking、search 仍被删除
- 不再在每次调用时编译正则

[DONE]
