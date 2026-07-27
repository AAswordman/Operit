---
title: 后续步骤：OCR 与非自由/原生产物依赖
status: planned
document_type: implementation-step
depends_on: 2_BinaryAndBuildInputInventory.md
---
# OCR 与非自由/原生产物依赖

## 目标
使 F-Droid 编译图不包含 ML Kit，并闭环可能携带模型或 SO 的 Maven 依赖。

## 最小执行单元
1. 定位五个 ML Kit 依赖及全部 OCR 消费者。
2. 选择自由 OCR 替换，或从 F-Droid source set 和依赖配置完整排除 OCR。
3. 在干净环境生成直接/传递依赖图，检查 AAR 内 SO、模型和许可证。
4. 移除无消费者仓库，记录实际解析源和依赖验证信息。

## 验收
- 编译图和 APK 均不存在 ML Kit。
- 每个原生/模型依赖具备来源、许可证与 APK 结论。
- 未完成代码改造和验证前不添加 `[DONE]`。
