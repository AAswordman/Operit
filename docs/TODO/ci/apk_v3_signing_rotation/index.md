---
title: APK V3 签名轮换
status: draft
document_type: implementation-plan
fork_repository: "https://github.com/AAswordman/Operit.git"
last_reviewed: 2026-08-07
---

# APK V3 签名轮换

当前线上 APK 仅使用旧发布证书的 V2 签名。旧证书已经泄露，需要在不影响 Android 8 与 8.1 更新的前提下，把 Android 9 及以上设备迁移到新证书。

Release 与 Nightly 保持同一正式包名并执行旧 V2、新 V3 双签。Debug 使用独立包名和测试密钥，能够与正式版并行安装。

## 计划步骤

1. [Release 与 Nightly 双签](./01_NightlyAndReleaseDualSigning.md)
2. [Debug 独立包名与测试密钥](./02_DebugPackageAndTestKey.md)
