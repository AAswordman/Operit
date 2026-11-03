# 固定离线资源与源码快照

普通 Debug 和 JVM 构建校验 `app/config/offline-packs.json` 中固定的已发布资源。`offline-pack-sources.json` 保存该资源版本对应的源码提交、白名单和输入文件 SHA-256，扩展指纹仍按原格式计算。开发分支新增类型声明时，当前索引不再被当作旧资源包的源码。

快照必须与目录的 releaseTag、每个扩展的 sourceFingerprint 一致。目录的组清单、文件清单、版本指纹、下载地址和总发行标签继续校验。快照内容直接随源码分发，浅克隆的 CI 无须读取旧 Git 提交或联网下载历史源码。

资源发布脚本显式调用严格检查，`verifyCiPreviewRelease` 与 `bundleRelease` 也依赖严格检查任务：

```sh
python3 ci/distribution/resources/verify_hub_catalog.py --current-source
```

严格模式还要求当前索引源码及白名单与固定资源一致。资源尚未更新时会阻止正式发行，普通开发构建通过并不表示更新后的扩展已经发布。

准备新资源时，先提交扩展及公共类型源码，在原有 prepare_hub.py 参数之外添加 `--source-snapshot app/config/offline-pack-sources.json`。该参数单独写入源码摘要，不往资源归档目录添加额外文件；同时把输出的 offline-packs.json 按既有流程更新到 app/config。快照只记录已提交输入，源码尚未提交时准备步骤会明确报错。

本轮快照来自现有固定目录的源码提交 `901471c562ced9aea321526a49fb01e13f5955df`，核对了 40 个内置扩展的原指纹。没有修改已发布资源的版本、地址或内容，也没有发布新资源。资源发布仍由维护者显式派发。
