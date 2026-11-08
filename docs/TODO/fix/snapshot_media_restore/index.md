---
fork: https://github.com/3316891527/Operit
branch: feat/snapshot-resources
pr: https://github.com/AAswordman/Operit/pull/1104
---

# 原始快照导入自动恢复媒体引用

## 原有行为

#1104 将已引用的媒体保存到 `payload/resources/` 并记录资源映射。恢复端仅依据映射改写 URI，release 导出的旧快照没有该字段，背景、头像和字体仍可能引用原安装目录。原有 protobuf 子串替换也会将嵌套消息当作文本，路径字节长度改变时可能破坏偏好结构。

## 预期行为

从快照中保存的主题偏好读取真实媒体引用，定位 `payload/files/` 或 `payload/external_files/` 中的文件，将资源引用转换为当前安装的路径。带资源映射的快照与不带映射的 release 快照均可恢复。角色、群组、默认角色和全局用户头像按原有配置生效，资源开关与主题参数随快照保留。

## 作用范围

- `data/backup/`：媒体路径解析、偏好读取及结构化重写、资源恢复计划
- `ChatBackupSettingsScreen`：恢复完成后的结果提示及自动重启
- JVM 回归用例：真实 Preferences protobuf、旧快照引用、路径安全和资源共享
- 导出资源整理、未引用媒体筛选和日志开关沿用 #1104 的实现

## 步骤

1. [导入资源应用](1-导入资源应用.md)
2. [完整回归清单](2-回归清单.md)
3. [编译与资源检查修复](3-编译与资源检查修复.md)

## 验证状态

代码与回归用例已完成。静态检查及仓库工作流派发状态见 PR 说明；本地未执行构建或测试，release 到当前安装的实机导入待验收。
