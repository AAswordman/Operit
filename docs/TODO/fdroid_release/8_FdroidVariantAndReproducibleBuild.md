---
title: 后续步骤：F-Droid 变体与可复现构建
status: planned
document_type: implementation-step
depends_on:
  - 3_ExternalArchivesAndModels.md
  - 4_EmbeddedExecutablesAndHelpers.md
  - 5_OcrAndNonFreeDependencies.md
  - 6_NativeSourcePinningAndSubmodules.md
  - 7_DynamicCodeAndGeneratedAssets.md
---
# F-Droid 变体与可复现构建

## 目标
汇总前述改造，建立非 debuggable F-Droid 变体并在干净环境验证。

## 最小执行单元
1. 在编译层排除不合规组件、依赖、权限和入口。
2. 生成依赖图和 APK 内容清单，逐项对应原生库、模型及生成资产。
3. 执行 `fdroid lint`、`scanner` 和 server build。
4. 进行两次独立未签名 APK 构建，以哈希和 diffoscope 检查可复现性。

## 验收
- 干净 server build 通过且不依赖 Google Drive、本地缓存或私有仓库。
- APK 内容与清单完全对应，scanner 无未处置发现。
- 两次独立构建结果的可复现性已有证据；此前不添加 `[DONE]`。
