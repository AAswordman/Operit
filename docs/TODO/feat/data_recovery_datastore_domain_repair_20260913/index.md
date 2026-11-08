---
Repository: https://github.com/AAswordman/Operit
Branch: feat/data-recovery-datastore-domain-repair
Status: in-progress
---

# 数据救援 DataStore 领域修复

## 原有状况

数据救援已经能检查 Preferences protobuf 是否可读，并在保全后删除无法解码的文件。可读文件里的损坏 JSON、悬空模型引用和非法记忆空间 ID 仍会原样留下。PR #1006 有完整的 DataStore 领域修复，但绑在启动流程和双槽恢复上，不能整包合并。

## 意图

- 从 #1006 抽出 Preferences 领域修复，接到现有用户触发的自动修复
- 不接入启动流程，不替换现有 DataStore owner，不引入双槽或 ObjectBox 恢复
- 检查仍用隔离副本；写入前保全原件，并要求主进程已停止

## 作用域

- `PreferencesHealthManager` 检查与修复
- 从 #1006 改编的领域校验：模型配置、功能映射、角色卡、角色组、API 设置、记忆空间索引
- 数据救援界面修复计划文案
- 开发文档和 JVM 测试

## 非目标

- 启动时预检或自动修复
- RecoverablePreferencesDataStore / 双槽快照
- ObjectBox 发现与恢复
- 语音 profile 迁移式修复
- 修改业务 DataStore 声明
