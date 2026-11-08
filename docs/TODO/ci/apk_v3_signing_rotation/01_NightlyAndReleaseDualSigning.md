---
title: 步骤 1：Release 与 Nightly 双签
status: draft
document_type: implementation-step
plan_scope: apk-v3-signing-rotation
step: 1
fork_repository: "https://github.com/AAswordman/Operit.git"
last_reviewed: 2026-08-07
---

# 步骤 1：Release 与 Nightly 双签

## 旧实现情况

- `tools/hotbuild/nightly_auto.py` 构建 Release 或 Nightly APK 后直接使用 Gradle 产物
- Release 使用当前发布密钥
- Nightly 使用 debug 密钥
- 没有 APK Signature Scheme v3 轮换链

## 预期的新实现情况

- 脚本构建 Release 或 Nightly 后，以旧发布密钥写入 V2 签名
- 脚本以新发布密钥和已生成 lineage 写入 V3 签名
- Android 8 与 8.1 保持旧证书更新链
- Android 9 及以上迁移到新证书更新链
- Debug 不进入正式 APK 的双签任务

## 修改作用域

已修改：

- `app/build.gradle.kts`
- `local.properties.example`
- `.gitignore`

不修改：

- App 业务代码
- Gradle build type 定义
- `tools/hotbuild/nightly_auto.py`，它继续调用原有的 Gradle assemble 任务
- Debug 签名策略，由下一步骤处理
- GitHub Actions 发布流程

## 验收

- Release 与 Nightly 产物均报告 V2、V3 签名有效
- V2 使用当前发布证书
- V3 lineage 从当前发布证书指向新发布证书
- Debug 产物不进入双签流程

## 完成记录

状态：[DONE]。
