---
title: Issue 767 头像配置持久化修复
repo: https://github.com/luojiaping/Operit
issue: https://github.com/AAswordman/Operit/issues/767
status: completed
---

# Issue 767 头像配置持久化修复

## 原本状况

`avatar_preferences.xml` 中的 `avatar_configs` 由 Gson 直接反序列化为非空领域模型。Gson 可以绕过 Kotlin 构造器，因此持久化记录中的 `data: null` 会进入 `AvatarConfig`，随后在读取头像路径时触发空指针并造成重启循环。

## 修正意图

- 在持久化读取边界使用可空传输模型校验必要字段
- 拒绝损坏记录，确保业务层只接收满足非空约束的 `AvatarConfig`
- 继续使用现有磁盘扫描恢复头像，并通过现有写回流程永久移除损坏记录
- 添加包含正常记录和 `data: null` 记录的回归测试

## 作用域

修改头像配置的 SharedPreferences 解码流程及其单元测试。Room 数据库、原始快照格式、头像领域接口和用户界面均不变。

## 期待结果

恢复含损坏头像配置的数据后，应用能够正常启动。有效头像配置继续保留，损坏记录对应的头像在模型文件存在时由磁盘扫描重新生成，清理后的配置随后写回 SharedPreferences。

## 完成情况

- 持久化数组改为逐条解码和校验，非法记录不会进入领域模型
- 损坏记录的数组位置会写入应用日志，现有头像扫描结束后覆盖保存清理结果
- 回归测试覆盖 `data: null`、缺失 `data`、错误数据类型和有效记录往返

[DONE]
