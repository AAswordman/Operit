---
fork: https://github.com/3316891527/Operit
branch: feat/integrated-distribution
status: implemented-user-verified
---
# 独立发行集成

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](06-验证结果补录.md)。

以 [Release 38675165272](https://github.com/3316891527/Operit/actions/runs/38675165272) 的固定提交 `39a0abaaf23446e5122f7951b5350f4f40b9d52d` 为起点，按用户给定的 52 个 PR 编号顺序整合当前分支代码。来源提交保存在 [pr-sources.json](./pr-sources.json)。

原基点已包含聊天 UI 改版、Waifu Markdown 修复及可与正式版共存的 Kong Release 打包配置。此分支承载独立发行整合，原有功能 PR 分支继续保留各自用途。

## 集成步骤

1. 固定全部 PR 来源提交，调查 #1338 的范围扩大原因
2. 按编号逐项合并，记录已包含项与人工冲突处理
3. 统一数据库迁移、资源与调用关系，保留全部已选功能
4. 提交最终记录并从本分支派发 Release 和 Tests，实际结果以运行链接为准

## #1338 调查

#1338 的源分支 `fix/waifu-markdown-rendering-1280` 后来兼作 APK 集成分支，并合入 #1372 的聊天 UI 改动及 Kong 打包配置，因此 PR 相对 `dev` 扩大到 285 个文件。Waifu 修复本身并不需要如此多的改动。其当前源提交正是本次发行基点，顺序整合时按已包含记录。

## 完成范围

52 项来源已按清单顺序处理。51 项生成按序合并提交，#1338 的固定来源已在基点中；全部来源提交都可在最终分支历史中追溯。保留原有功能分支，不把本次发行集成反向同步到各个上游 PR。

[集成与兼容记录](./01-集成与兼容记录.md)、[完整集成回归清单](./02-完整集成回归清单.md) 和 [逐项合并结果](./merge-results.json) 记录来源、人工协调与验证边界。

## 验证状态

来源数量、顺序与提交可追溯性、资源 XML、重复资源、孤立翻译及 diff 空白检查已通过。对手动协调的 Kotlin 文件及原生围栏插件做了辅助语法扫描；静态检查不能代替 Android 编译和运行验证。

新增数据库联合迁移和 CSV 兼容回归，尚未运行。本轮不在本地执行 Android 构建或 JVM 测试，由本分支 [Android Build](https://github.com/3316891527/Operit/actions/workflows/build.yml?query=branch%3Afeat%2Fintegrated-distribution) 和 [Android Tests](https://github.com/3316891527/Operit/actions/workflows/android-tests.yml?query=branch%3Afeat%2Fintegrated-distribution) 工作流执行，实际通过与 APK 产出以对应运行结果为准。

设备功能回归已按用户本次确认补录为通过。[聊天 UI 原有 216 项清单](../../feat/chat_ui_redesign/24-回归清单.md) 和本轮集成清单均保留，历史通过不等于本轮集成版本通过。

[DONE] 按指定基点和编号顺序完成源码整合；功能验收已按用户确认补录为通过；CI 状态见验证结果补录。

## 后续发行调整

[设置入口与终端核心更新](03-设置入口与终端核心更新.md) 记录第二个固定签名发行版的入口调整、终端子模块来源和完整增补回归。

当前发行版：`1.12.2+1`、versionCode 53。本轮同时修复插件市场与沙盒包不接受 `personal` 版本后缀的问题，并更新设置入口和终端核心。详见 [设置入口、终端核心与版本格式更新](./03-设置入口与终端核心更新.md)。

[独立发行增量更新](./04-独立发行增量更新.md) 接入个人仓库 Releases，保留既有补丁还原协议，并补充发布工作流。设备功能验收已按用户确认补录为通过；实际 Release 发布记录单独保留。

[工具超时结果](./05-工具超时结果.md) 记录 `+5` 的紧急补充：终端、浏览器动作等待和 BLE 读取超时按失败处理，包含字符串资源与完整回归清单。
