# 独立发行的 Release 与增量更新

客户端统一查询 `3316891527/Operit-Kong-patch` 的 GitHub Releases。该仓库保存所有正式版和补丁版；`3316891527/Operit` 只保留源码和开发协作，不发布应用或离线资源；固定离线资源位于 `3316891527/Operit-Kong-resources`。更新检查、补丁生成和 APK 还原仍在源码仓库实现。

`1.12.2+15` 已完成发行分仓，本次发行版本为 `1.12.2+22`、versionCode 78。沿用统一更新仓库与现有签名，使用 `v1.12.2+21` 的完整 APK 生成增量包。仓库创建、历史迁移、权限和完整回归见[分仓计划](../../../docs/TODO/chore/release-repositories/index.md)。

## 渠道与安装

自动检查与手动检查共用 Beta 计划开关：关闭时仅选择 `x.y.z` 正式版，开启后也选择 `x.y.z+n` 补丁版。先过滤渠道，再比较数字版本。退出 Beta 不降级已有补丁版，仍可安装更高基础版本的正式版。开关变化后清除旧检查结果。

首次切换通过完整 APK 安装。包名保持 `com.ai.assistance.operit.kong`，沿用已有固定签名，versionCode 递增。仅创建标签或上传 Actions artifact 不会形成可发现的应用更新。完整 APK 必须随 Release 保存，供新用户安装和后续生成补丁。

## 发布方法

源码仓库的 [Build 工作流](../../../.github/workflows/build.yml)选择 `main`、任务 `:app:verifyCiPreviewRelease`，勾选 `publish_release`，填写非空 Markdown 更新说明和可选 `previous_tag`。本次使用已发布的 `v1.12.2+21` 为基础；留空时选择补丁仓库中同一基础版本且低于目标的最新已公开 APK。普通构建保持 `publish_release=false`。

工作流需要源码仓库的 Actions secret `OPERIT_DISTRIBUTION_TOKEN`，授予源码、补丁、资源仓库的 Contents 写入权限及源码仓库 Actions 读取权限。内置 `github.token` 不能向另外两个仓库发布。权限预检在签名构建前执行；缺少配置时明确失败。既有发行仓库和专用 secret 继续使用；本次保持 `complete_repository_migration=false`，不重复执行历史迁移清理。

发布脚本读取 APK 的真实版本，检查 Kong 包名、固定证书和递增内部版本号。所有应用版本均只发往补丁仓库，先完成草稿上传及核验再公开；源码仓库不发布应用副本。已有 Release 不覆盖。

当前 Build 发行步骤仅向统一更新仓库发布，不创建源码仓库版本标签，发行摘要也不再声明 sourceTag。源码提交和源码归档地址仍记录在正文与摘要中。发行仓库自己的 tag 继续承载 Release，已有标签不删除或移动。直接调用未变换的 publish_release.py 仍保留历史源码标签逻辑，正式发行应通过当前工作流。

## 不公开版本的准备

可以继续使用当前版本号构建内部验证 APK，不需要为验证提前递增公开版本。发布脚本的 `--prepare-only` 允许准备已有版本，但不会创建任何标签、草稿或公开 Release；实际发布仍拒绝已有版本。

```bash
python3 ci/distribution/updates/publish_release.py \
  --apk app/build/outputs/apk/release/app-kong.apk \
  --build-tools "$ANDROID_HOME/build-tools/35.0.0" \
  --repository 3316891527/Operit \
  --commit "$GITHUB_SHA" \
  --notes '内部发行准备' \
  --prepare-only
```

此模式执行 APK 校验、基础包下载、补丁生成和逐字节还原，输出 `release-plan.json`、完整 APK 对应摘要及可用增量包。只准备模式的产物不代表已经发布的版本。

## 补丁协议与校验

继续使用 `tools/hotbuild/build_patch.py` 和 `apply_patch.py` 的 `apkraw-1` 协议，复制未变化 ZIP 原始记录，保留 APK 签名区和完整文件布局。发布前还原并比对目标 APK 的 SHA-256，客户端也核对基础 APK、补丁和目标 APK 摘要。

补丁仅连接同一基础版本的递增版本，基础 Release 标签必须与其 APK 内版本一致。跨基础版本使用完整 APK。客户端从补丁及元数据附件地址解析仓库，直接增量和跨版本链自动使用新仓库，不增加旧仓库查询分支。

发行附件包括完整包 `app-kong.apk`、存在基线时的 `apkrawpatch_*.zip` 和配对 `patch_*.json`，以及 `distribution-release.json`。摘要保留版本、哈希、更新说明、统一更新仓库和源码身份。正文使用非空可读文本或 Markdown，JSON 仅作为附件；历史 JSON 正文继续由既有客户端解析逻辑读取。

安装器最多扫描最近 100 个 Release，补丁链最多连接 64 步。保留链上全部版本和配套文件，历史迁移时 APK 和增量 ZIP 字节保持不变；重新签名或打包的 APK 不适用原链。

## 验证状态

原有发布回归继续覆盖版本、基线、包名、签名、原始记录复用及逐字节还原。新增回归覆盖发行分流、草稿上传顺序、只准备模式、源码标签保护、迁移完整性、资源清单改址及产物检查。客户端 JVM 回归继续覆盖完整包、增量、渠道、数字排序、草稿过滤和文案提取。

本次未在本地执行编译、构建或测试。工作分支派发、不公开版本的构建结果及真实迁移收据分别记录；准备好的回归用例不等于通过结果。早期更新实现的已通过验证属于历史证据：[Android Build](https://github.com/3316891527/Operit/actions/runs/40199253010) 与 [Android Tests](https://github.com/3316891527/Operit/actions/runs/40198980173)，不覆盖本次分仓。


## 发布后清理本轮工作分支

本次 `1.12.2+22` 从 `main` 发布时启用 `cleanup_merged_branches`。该选项默认关闭，只在应用及配套资源正式公开后执行，按 branch-cleanup-plan.json 清理 15 个功能修复分支、联合测试分支和本轮发行准备分支。保留 `dev`、`main`、1.13.0 日志草稿及本轮未列出的分支。

清理先核对公开 Release 与本次 release-plan.json 的版本、源码提交、附件大小和 SHA-256；仍有开放 PR 或包含本次发行未合入提交的分支会保留。实际删除使用观察到的分支头作为租约，构建期间新增的提交不会被误删。结果写入 branch-cleanup-report.json 并随发行报告上传，不需要持续轮询工作流。
