---
fork: https://github.com/3316891527/Operit
status: prepared
---

# 1.12.2+18 发行集成

## 范围

按维护者要求整合 PR #86 的离线语音修复与 PR #87 的补丁 18 功能，合入 dev 后同步 main。版本为 `1.12.2+18`、versionCode 74，沿用 Kong 包名与已有固定签名，从补丁仓库正式 `v1.12.2+17` 生成增量包。

两项改动在中英文 offline_resource_hub.xml 的末尾新增了不同资源键；集成时完整保留说话人选择、MiMo 参数说明和插件删除提示。其他源码采用正常合并结果。终端固定引用 `857af28a490716feefbb7b78e98273e77a3f0cfe`，当前资源清单与 +17 完全一致，继续使用 `resources-v2-d16730007f8d94095264`，不重新打包已有资源。

## 仓库与发行配置

离线资源仅保存在 `3316891527/Operit-Kong-resources`，应用完整 APK、增量包和摘要仅保存在 `3316891527/Operit-Kong-patch`。源码仓库不创建本次应用或资源 Release。先前误建的历史资源复制品及对应标签已按维护者要求删除，资源仓库中的历史原件保持完整。

发行使用 main 上的 Android Build，参数为 `gradle_task=:app:verifyCiPreviewRelease`、`publish_release=true`、`complete_repository_migration=false`、`previous_tag=v1.12.2+17`。当前工作流的发行步骤固定单一补丁仓库目的地，且不创建源码仓库版本标签。

## 验证与边界

合并前读取到 PR #87 的 Fast checks、Android build、Android JVM tests 与 Candidate checks 全部成功；PR #86 的 Fast checks、Android JVM tests 与 Candidate checks 成功，Android build 按路径规则跳过。集成本地仅进行差异空白、XML 解析、资源键及固定清单核对，没有执行编译、构建或测试；合并后的签名构建与增量还原交由发行工作流执行。派发受理不等于已完成发行。

保留[完整 182 项操作与预期](../../feat/patch18-integration/regression.md)、[独立结果表](../../feat/patch18-integration/results.md)及 PR #86 的离线语音设备检查。此前没有取得结果的设备与真实服务路径仍待验证，不能因集成或发布而记为通过；5003 实际连接中止来源及工具汇总偶发不一致仍需对应请求或复现证据。
