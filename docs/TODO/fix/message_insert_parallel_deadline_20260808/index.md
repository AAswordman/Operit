---
fork_repository: local
---

# 额外信息注入并发时限

## 背景

额外信息注入中的天气、定位、OCR、通知和记忆检索按顺序执行。多个异步采集项同时开启时，发送前等待时间会累加。

## 目标

- 同时启动所有启用的异步采集项
- 整轮注入在设置的截止时间内返回
- 未在截止时间前完成的采集项不写入未完成数据，只保留 `timeout` 状态标记
- 保持附件内信息项的既有顺序

## 作用域

- `examples/message_insert/src/shared.ts`
- `examples/message_insert/dist/shared.js`
- `examples/message_insert/src/ui/index.ui.ts`
- `examples/message_insert/dist/ui/index.ui.js`

## 步骤

1. [完成] 将启用项声明为独立采集任务，并在同一截止时间下通过 `Promise.all` 汇总
2. [完成] 为超时任务记录诊断并在附件中写入 `timeout` 状态标记
3. [完成] 检查源文件与发布脚本的一致性，记录未执行构建和测试
4. [完成] 将注入超时作为秒级配置项提供给设置界面

[DONE]
