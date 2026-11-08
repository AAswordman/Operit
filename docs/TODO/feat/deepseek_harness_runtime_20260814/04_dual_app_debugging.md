---
title: ADB 双 applicationId 调试支持
status: completed
---

# ADB 双 applicationId 调试支持

## 原状

ADB JS 执行器、ToolPkg 调试安装器和示例包热更新脚本把 Release applicationId
`com.ai.assistance.operit`、对应的 action、receiver 和外部目录全部写死。使用
Debug APK `com.ai.assistance.operit.debug` 时，脚本会把文件推送到错误的目录，广播也
不会被 Debug APK 接收。

## 修改

- 所有 ADB JS 执行器（单文件、目录、sandbox）和 Compose DSL 调试导出都支持两个 applicationId。
- `tools/toolpkg/debug_toolpkg.py` 增加 `--app-package`，并读取
  `OPERIT_APP_PACKAGE`。
- `tools/example_packages/sync_example_packages.py` 的热更新增加 `--app-package`，并
  读取 `OPERIT_APP_PACKAGE`。
- 未显式指定时查询设备已安装包，选择顺序为 Debug、Release。
- action、receiver、`js_temp` 和 `files/packages` 均由最终选择的 applicationId 动态生成。
- 示例包热更新签名按“设备序列号 + applicationId”隔离，切换 Debug/Release 时会分别同步。
- 每次调试前打印实际使用的 applicationId，避免调试工具和手机上的 APK 不一致。

## 验收标准

- 仅安装 Debug APK 时，所有脚本使用 `com.ai.assistance.operit.debug`。
- 仅安装 Release APK 时，所有脚本使用 `com.ai.assistance.operit`。
- 两个 APK 同时安装时，未指定配置使用 Debug；设置 `OPERIT_APP_PACKAGE` 或传入
  `--app-package` 后使用指定版本。
- 指定的 applicationId 未安装或不是受支持的两个值时，脚本直接报告错误并停止。
- 旧的 Release 调试路径保持不变，只是从固定值改为解析结果。

[DONE]
