---
fork: https://github.com/3316891527/Operit
status: prepared
---

# 1.12.2+16 发行集成

## 目标与范围

按维护者要求，将现存 PR #62、#63、#64、#65、#67 合并到 dev，删除对应远端分支，再将发行内容同步 main。版本更新为 1.12.2+16、versionCode 72，沿用固定签名与发行分仓，从已公开的 +15 生成增量包。

## 已完成的集成

五个 PR 均已合并，对应远端分支已删除。目录重构合并时解决 Skill 下载和市场详情页的导入冲突，并将新增市场进度组件与测试归入 extensions/packages。四个小 PR 的 Kotlin 文件按既有包名映射比较后，仅存在已实现的 UiModule 统一命名差异，原功能正文保留。

主分支历史另有八处冲突：六处为请求错误相关导入和测试，另两处为 Toast 队列及测试。对照确认 main 的实现均已包含，保留 dev 迁移后的引用和新增取消操作；历史同步前后源码树一致。

## 验证与交付

28 个集成 Kotlin 文件的 Tree-sitter 语法解析、27 份 XML 解析、差异格式和固定资源目录核对通过；不等于 Kotlin 类型检查或编译通过。本地未运行构建和测试。重构集成分支 Debug/JVM 已收到 HTTP 204 受理，未等待或轮询结果。

版本提交后的工作分支应派发仓库 JVM 测试，再合入 dev、同步 main；main 派发签名发行工作流，任务为 :app:verifyCiPreviewRelease，publish_release=true，complete_repository_migration=false，previous_tag=v1.12.2+15。工作流完成前不记录 APK 或公开发行通过。

固定资源目录沿用 resources-v2-f67fc0082a8e638c6cc2，本次不改 SDK、模板或固定资源载荷，不重复发布同一资源版本。发行附件由现有工作流生成并发布至 Operit-Kong-patch，源码仓库保留对应标签。

## 回归与未测项

保留各主题完整用例和结果表，不把新增源码或受理状态写成设备通过：

- [扩展删除 D01—D07](../../fix/package-delete-source/index.md)
- [终端菜单 T01—T04](../../fix/terminal-overflow-menu/index.md)
- [工作流错误 W01—W09](../../fix/workflow-error-details/index.md)
- [市场下载 M01—M12](../../feat/market-transfer-progress/index.md)
- [协议、流式显示及此前 D/U 完整回归](../../fix/streaming-bubble-freeze/verification.md)

流式气泡低频现场的完整切页及再次退回时序尚未复现；Android 仪器用例未执行。WASM、桌面组件及此前未测复杂路径继续记为未测。
