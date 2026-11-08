# 离线资源实现与维护

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

## 用户可见行为

“设置 → 离线资源”分别管理 Sherpa-NCNN、Ubuntu 安装归档和两个网页应用导出模板。页面展示分组大小及校验结果；点击下载确认流量后从个人仓库的 resources-v1 Release 下载。失败或取消后可重试，已校验完成的文件不会重复下载，未完成的单个文件重新下载。

资源检查不自动打开页面，后台初始化不会打断应用启动。缺少资源时原操作报告资源未准备好，用户从“设置 → 离线资源”下载或导入后重试原操作。下载不会占用终端现有的 30 秒会话初始化等待时间。

现有 Sherpa-NCNN 模型保持原目录，校验通过后直接使用。模型准备、备份和加载均接入 LocalModelRuntimeRegistry，使用中的模型不能被资源导入覆盖，也不能被存储清理并发删除。

终端子模块不再打包 Ubuntu 归档。Application 在创建终端单例之前注册 setUbuntuArchivePreparer；只有 Ubuntu 尚未安装时，终端调用宿主提供已校验归档。原有安装标记和旧版 etc/issue.net 的识别规则与安装脚本一致。已安装的 Ubuntu 环境继续离线工作。资源缓存内保留原始归档，安装脚本删除的只是传给安装流程的工作副本。

Android 和 Windows 导出功能保留。实际导出时将已验证模板复制到现有工作目录，然后执行原有的封装、图标和签名逻辑。普通聊天及共用 Silero VAD 不需要这两个模板。

## 离线保存与重装

每个分组可用系统文件选择器导出 ZIP，并在重装后选择对应分组导入。请保存到“下载”等共享目录，应用私有目录会随卸载清除。删除下载安装 APK 不会清除应用数据内的资源。

导出的 ZIP 只包含该组清单中的平铺文件。导入先写入应用内暂存目录，逐个验证固定大小及 SHA-256；拒绝未知、缺失、重复和越界名称，整组校验通过后逐文件原子替换，失败及取消会清理暂存文件。系统文件选择器提供文件访问授权，不需要新增存储权限。

资源归档不包含用户的 Ubuntu 工作环境和文件，仅用于重新安装系统。保留工作环境数据应继续使用原有终端环境备份功能。

## 渠道与固定清单

资源地址为 https://github.com/3316891527/Operit/releases/tag/resources-v1 。应用使用该 Release 下的单文件链接；页面也提供浏览器入口，手动下载的 operit-*-resources-v1.zip 可直接导入。GitHub 网络需要可达，当前没有自建 CDN 或默认第三方代理。

app/config/offline-resources.properties 保存每个资源的分组、文件名、字节数、SHA-256、原始来源和许可说明。该清单随 APK 分发，更新清单之前不会接受内容不同的资源。resources-v1 作为固定资源版本保留，替换内容应使用新的资源标签，并同步修改代码中的下载基址及归档名称。

维护者将原始资源以清单中的文件名放入独立目录，运行：

```bash
python3 ci/distribution/resources/prepare_release.py --sources /path/to/resources --output /path/to/release-assets
```

脚本核对全部文件后生成单文件附件、四组 ZIP、清单和 SHA256SUMS.txt。将产物发布为非 latest 的固定资源 Release。应用更新选择器只接收规范版本标签及安装 APK，因此资源 Release 不会被当成应用更新。

## APK 与 CI

Gradle 的 STT 同步任务只生成共用 Silero VAD。主资产同步排除旧模型目录和 subpack，随后加入 VAD 与固定资源清单。完整 Android 依赖准备仅下载 libs.zip 和 jniLibs.zip；终端子模块删除随包的 Ubuntu 大归档。

Build 在产出 APK 后执行 ci/distribution/resources/verify_packaged_resources.py，确认上述大资源未打包、VAD 完整并且清单与源码一致，再把真实 APK 字节数及 MiB 写入工作流摘要。

JVM 回归覆盖资源 ZIP 往返、错误哈希、截断、超长、缺失、越界、未知、重复与取消。未执行本地 Gradle 构建和本地测试；构建和测试由仓库工作流执行。手动回归见 [完整回归清单](02-regression.md)。

[DONE] 客户端、固定资源清单、离线备份与导入、终端接口及打包调整已实现；功能验收已按用户确认补录为通过；CI 状态见验证结果补录。

## 云端发布资源

Android Build 的 publishOfflineResources 选项只运行资源发布作业，跳过 Android 构建。作业读取固定的旧版 APK artifact，提取并校验模型和模板，按固定提交取得 Ubuntu，生成确定性的 ZIP 和校验清单，再上传至本轮准备的 resources-v1 草稿。全部附件通过大小与 GitHub SHA-256 核对后自动发布，且不标记为应用 latest。

草稿内未完成的传输及与清单不符的旧附件可在发布前清理。正式资源 Release 不允许此脚本覆盖。APK artifact 仅作为本轮准备资源的来源；应用下载与重装复用依赖发布后的固定资源附件和用户保存的本地 ZIP。
