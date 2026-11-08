---
fork: https://github.com/3316891527/Operit
status: prepared
---

# 1.12.2+17 紧急发行

## 范围

PR #72 修正 use_package 激活失败协议，PR #73 修正升级后的外置扩展安装识别；两项独立修复均已合入 dev。版本为 1.12.2+17、versionCode 73，上一版本 +16，沿用已有包名、签名和资源仓库。

当前固定资源清单与载荷不变。APK 仅新增 80 条历史扩展清单元数据，未重新捆绑插件。两项修复各自派发 Debug/JVM，均 HTTP 204 受理；本地只完成静态检查，未执行构建或测试，未等待/轮询 CI。

## 发行配置

版本工作分支派发仓库 JVM 工作流后合入 dev，再同步 main；main 使用 :app:verifyCiPreviewRelease、publish_release=true、complete_repository_migration=false、previous_tag=v1.12.2+16。现有工作流不创建主仓库源码 tag，只向 Operit-Kong-patch 发布完整 APK、增量和摘要。

## 完整回归与已知边界

[安装保留、激活协议及设备检查完整表](../../fix/upgrade-package-preservation/verification.md)保留 P01—P16、A01—A04、E01—E06，以及此前协议、流式、UX、市场、删除和工作流错误覆盖的引用。

+15 到 +16 的 40 组扩展版本全部变化，其中 33 组载荷相同，源码确认运行时只查新版本目录会遗漏旧安装。未证明现场实际删除了文件；旧目录已实际删除时不能恢复原字节。MCP 独立配置的删除反馈尚未复现。真实升级、设备恢复、WASM 和此前未测复杂路径继续未测。
