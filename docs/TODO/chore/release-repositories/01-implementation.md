# 客户端与发布分流

## 原有行为与改动

检查更新通过 `DistributionReleaseResolver` 查询 GitHub Releases。现在仅将查询仓库改为 `3316891527/Operit-Kong-patch`，继续先按 Beta 开关过滤渠道，再比较数字版本；更新页使用附件返回的真实下载地址。补丁安装器原本就从附件地址解析所属仓库，因此直接增量与跨版本补丁链沿用现有实现。

旧 NCNN、终端 Ubuntu、Android 与 Windows 导出资源使用 `resources-v1` 的固定路径，整体改指资源仓库。工程模板、40 个内置扩展、Sherpa ONNX 和 ML Kit 使用 `offline-packs.json`，其中 51 个地址整体改指资源仓库。归档名称、大小、SHA-256、资源版本及总发行标签不变，已安装内容仍可按原指纹识别。新语音模型的上游模型地址及镜像规则沿用原清单。

`ci/distribution/repositories/config.py` 固定三个仓库的职责。发布脚本仍由源码仓库工作流调用，`--repository` 校验来源仓库；所有应用发行进入补丁仓库，不带加号的版本还会在主仓库发布相同文件。基础 APK 只从补丁仓库取，包名、签名、versionCode 递增、同基础版本限制及逐字节补丁还原继续检查。

新仓库不含应用代码，不能把主仓库提交 SHA 当成新仓库的目标提交。发布脚本在主仓库保留指向构建提交的版本标签，新仓库的发行标签指向自身默认分支。发行说明和摘要附件另存主仓库提交与源码归档链接，已有源码标签禁止移动。

所有目的地均先上传完整草稿，再依次公开，统一更新仓库最后公开。`--prepare-only` 生成完整发布计划，并执行同样的 APK 和补丁校验，但不创建标签或 Release。

## 离线资源发行

历史资源由迁移工具直接复制，归档逐字节保留。仅资源目录中的下载地址和对应总校验清单发生变化。未来新资源仍由 `prepare_hub.py` 准备、`publish_hub.py` 按当前固定清单核对并发布到资源仓库。

`publishOfflineResources` 工作流入口改为接收 `resource_artifact_id`。该产物须由所选源码提交的 Actions 运行上传，内容为准备目录中的资源 ZIP、`offline-packs.json` 和 `SHA256SUMS.txt`，不再固定使用某次旧 APK 产物及旧 Release ID。入口先核对产物来源，再检查平铺路径、重复文件、文件大小、资源 SHA-256、清单和远端摘要；发布完成后不改写。

## 权限与激活条件

跨仓库发布使用源码仓库 Actions secret `OPERIT_DISTRIBUTION_TOKEN`。这是工作流引用的 secret 名称，已有权限足够的 PAT 可以直接保存到该名称，无需生成特殊类型的 Token。工具自身使用的 PAT 配置不会自动同步到 GitHub Actions。凭据需能读取源码仓库的 Actions 产物，并能写入源码、补丁及资源三个仓库的 Contents。正常 Debug 构建及 JVM 验证不读取这个 secret。

配置时打开源码仓库 Settings → Secrets and variables → Actions → New repository secret，名称填 `OPERIT_DISTRIBUTION_TOKEN`，值填已有 PAT。

现有工作流内置 Token 的写入范围限于源码仓库，不能继续拿它向另外两个仓库发布。发布入口在构建前检查凭据和目标仓库，缺少配置会明确失败。维护者应使用专用、限制仓库范围的凭据，不把个人登录凭据复制进源码或文档。

本次切换发行设为 `1.12.2+15`、versionCode 71，基线使用已迁移的 `v1.12.2+14`。

[DONE] 客户端、固定清单、发行分流、无发布准备模式和资源产物校验已实现；工作分支已接入发布工作流，真实分仓迁移和凭据配置状态以索引和交付记录为准。
