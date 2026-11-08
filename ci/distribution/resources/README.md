# 固定离线资源与源码快照

普通 Debug 和 JVM 构建共同校验 `app/config/offline-packs.json` 与 `app/config/builtin-extensions.json` 中固定的已发布资源。`offline-pack-sources.json` 保存该资源版本对应的源码提交、白名单和输入文件 SHA-256，扩展指纹仍按原格式计算。开发分支新增类型声明时，当前索引不再被当作旧资源包的源码。

快照必须与目录的 releaseTag、每个扩展的 sourceFingerprint 一致。目录的组清单、文件清单、版本指纹、下载地址和总发行标签继续校验。快照内容直接随源码分发，浅克隆的 CI 无须读取旧 Git 提交或联网下载历史源码。

资源发布脚本显式调用严格检查，`verifyCiPreviewRelease` 与 `bundleRelease` 也依赖严格检查任务：

```sh
python3 ci/distribution/resources/verify_hub_catalog.py --current-source
```

严格模式还要求当前索引源码及白名单与固定资源一致。资源尚未更新时会阻止正式发行，普通开发构建通过并不表示更新后的扩展已经发布。

准备新资源时，先提交扩展及公共类型源码，在原有 prepare_hub.py 参数之外添加 `--source-snapshot app/config/offline-pack-sources.json`。该参数单独写入源码摘要，不往资源归档目录添加额外文件；同时把输出的 offline-packs.json 和 builtin-extensions.json 按既有流程更新到 app/config。快照只记录已提交输入，源码尚未提交时准备步骤会明确报错。

源码中的开发固定快照来自现有资源目录对应提交 `901471c562ced9aea321526a49fb01e13f5955df`。普通开发构建继续使用该目录；签名发行按下面的同轮准备流程生成打包输入。

## 签名发行时准备当前源码资源

Build 工作流在白名单扩展构建后调用 `prepare_signing.py`，签名入口随后运行 `prepare_current_resources.py`，在 Gradle 前准备当前提交的扩展和模板。引擎与 OCR 从原固定清单读取下载地址，核对大小及 SHA-256 后复用归档。新目录与源码快照写入本次 runner 的 app/config，随后执行原有严格检查；生成输入不自动提交到源码仓库。

签名候选只准备和校验，不上传资源；它引用的资源版本需要正式发布成功后才能下载。正式发布由已有 `publish_release.py` 入口在检查 APK、生成并还原增量包后核对准备记录，要求源码提交、目录摘要和 APK 内的目录完全一致，再调用不可变资源发布器。资源全部公开并核对成功后才创建和公开应用 Release；资源失败直接停止应用发布。`--prepare-only` 只核对本轮资源与 APK，不触发资源发布。

发行权限预检同时检查源码、统一更新和资源仓库，资源凭据只在既有正式发布步骤中使用。准备阶段不读取发布凭据，普通 Debug/JVM 构建继续使用开发固定快照。

目录、源码快照、总校验清单和准备摘要保存到 `app/build/reports/offline-resources`，由既有 Android reports artifact 上传。正式发行的 `distribution-release.json` 另附 `offlineResources`，记录同轮资源标签、源码提交、摘要和公开结果。手动 `publishOfflineResources` 的固定清单及产物入口保留既有参数和校验规则。


## 内置扩展市场目录

插件与脚本的 40 项元数据位于 app/config/builtin-extensions.json，由市场内置来源展示；offline-packs.json 保留模板、引擎和模型。运行时沿用既有已验证安装目录。两份目录共享发行标签和源码快照，资源附件继续按文件大小与 SHA-256 校验。

资源准备生成 resources-v3 标签，作为双目录布局的独立发行；既有 resources-v2 公开附件不改写。签名构建的资源准备、产物校验与发布复用都必须包括 builtin-extensions.json。应用显式检查更新时选择维护者资源仓库最新正式资源发行，v2 读取 offline-packs.json 中的扩展，v3 读取 builtin-extensions.json。选择的发行必须带对应目录，目录缺失、标签不符或身份校验失败时报告错误。
