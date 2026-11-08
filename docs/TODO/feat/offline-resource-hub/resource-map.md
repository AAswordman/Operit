# 资源与模型来源

本文件记录设计稿采用的真实目录和待确认事项。HTML 的安装状态为演示数据，不代表设备实际状态，也不代表 Android 已接入新增运行引擎。

## 当前四组离线资源

来源为 [资源清单](../../../../app/config/offline-resources.properties) 和 [下载与导入实现](../../../../app/src/main/java/com/ai/assistance/operit/data/resources/OfflineResources.kt)。

- NCNN STT：141346210 字节，约 134.8 MiB；移入本地模型中的语音识别分类
- Ubuntu 归档：64133552 字节，约 61.2 MiB；放在其他资源中的运行环境分类
- Android 网页导出模板：48139093 字节，约 45.9 MiB；对应 HTML 打包 APK
- Windows 网页导出模板：11412717 字节，约 10.9 MiB；对应 HTML 打包 Windows 程序

现有下载来源为 `https://github.com/3316891527/Operit/releases/download/resources-v1/{filename}`。NCNN 按清单中的七个文件准备，其他三组各一个文件。模型数据校验、ZIP 导入与导出必须继续保留，不用界面状态代替真实文件校验。

Ubuntu 运行环境已经安装，不代表安装归档已经保存。设计稿分别表达两者；“移除安装归档”的设计也只移除归档。实际实现时要与现有同时删除环境和归档的入口分清，不能直接调用旧删除操作冒充归档管理。

## Operit2 的原生模型候选

已读取 [LocalModelCatalog.rs](https://github.com/AAswordman/Operit2/blob/main/core/crates/provider/local-model/src/LocalModelCatalog.rs)、[LocalModelManifest.rs](https://github.com/AAswordman/Operit2/blob/main/core/crates/provider/local-model/src/LocalModelManifest.rs)、[LocalModelHub.rs](https://github.com/AAswordman/Operit2/blob/main/core/crates/provider/local-model/src/LocalModelHub.rs) 和 [LocalModelStorage.rs](https://github.com/AAswordman/Operit2/blob/main/core/crates/provider/local-model/src/LocalModelStorage.rs)。

读取时活动目录有九项：五项原生语音模型、两项浏览器模型和两项 Qwen3 聊天模型。本设计选用五项适用于 Android 的原生语音候选；另保留当前 NCNN STT 和 OCR，共七项模型入口。没有因目录中出现模型就宣称当前 Operit 已有相应引擎或识别准确率已改善。

<details><summary>五项候选的仓库与固定版本</summary>

1. Sherpa ONNX Zipformer 中英 STT

   HF 仓库：`csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`

   HF 版本：`98590b7ed6443e77b714204da2757d75e1a642f4`

   ModelScope 仓库为 `pkufool/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`，版本为 `master`。四个文件合计 199056205 字节，约 189.8 MiB；Apache-2.0。

2. VITS AISHELL-3 int8

   HF 仓库：`csukuangfj/vits-zh-aishell3`；ModelScope 仓库：`KuaiTec-Labs/kuaitec-model-suite`

   HF 版本：`e3e808eaab2385b812286c6707323362251bba65`；ModelScope 为 `master`，文件在 `vits-aishell3/` 子目录，下载基址为 `https://modelscope.cn/models/KuaiTec-Labs/kuaitec-model-suite/resolve/master/vits-aishell3`。

   三个文件合计 41914738 字节，约 40.0 MiB；Apache-2.0。

3. VITS zh-ll

   HF 仓库：`csukuangfj/sherpa-onnx-vits-zh-ll`

   HF 版本：`7ddf37bcacf05ed56afee360d96835be633a5265`

   ModelScope 仓库：`liaowenbin/sherpa-onnx-vits-zh-ll`，版本为 `master`。

   七个文件合计 121712242 字节，约 116.1 MiB；目录许可字段为 `community`，不能改写为 Apache-2.0。

4. Matcha Baker

   HF 仓库：`csukuangfj/matcha-icefall-zh-baker`

   HF 版本：`75e64a57a80fb370abb18a4e4d86bb090ed46e93`

   ModelScope 仓库：`liaowenbin/matcha-icefall-zh-baker`，版本为 `master`。

   模型与规则文件加独立声码器合计 131104197 字节，约 125.0 MiB；目录许可为 `non-commercial`。

   声码器 `vocos-22khz-univ.onnx` 的真实来源为 `https://github.com/k2-fsa/sherpa-onnx/releases/download/vocoder-models/vocos-22khz-univ.onnx`，不能按模型仓库地址拼接。

5. Kitten nano 英文 int8

   来源：`https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-int8.tar.bz2`

   归档 31220690 字节，约 29.8 MiB；Apache-2.0。它是直接归档来源，不随全局 HF / ModelScope 选择器切换。

</details>

### 下载地址与导入规则

- Hugging Face：`https://huggingface.co/{owner}/{repo}/resolve/{revision}/{relativePath}`
- HF-Mirror：`https://hf-mirror.com/{owner}/{repo}/resolve/{revision}/{relativePath}`
- ModelScope：`https://modelscope.cn/models/{owner}/{repo}/resolve/{revision}/{relativePath}`

各候选模型使用目录声明的仓库、文件大小和逐文件 SHA-256。ModelScope 的仓库名与 HF 不一定相同，不能只换域名。第三方直接发布的文件仍使用其自己的来源。

仓库输入支持 `owner/repo` 和完整网址，HF / HF-Mirror 未指定版本时用 `main`，ModelScope 用 `master`；网址中的 `tree/版本` 和 `resolve/版本` 可以携带版本信息。真实导入还必须识别文件结构、引擎与用途，不得只凭仓库名或扩展名认定模型可运行。

Operit2 的模型路径为 `runtime/models/local/{模型种类}/{引擎}/{模型ID}/{版本}`，由 `buildLocalModelStoragePath` 生成；引擎路径为 `runtime/models/local/engines/{引擎ID}/{版本}/{平台}`，另有独立的 manifests 和 registry 记录。当前 Operit 的 NCNN 模型路径由 `OperitPaths.sherpaNcnnModelsDir` 管理，后续接入应明确适配既有数据，不在 HTML 阶段迁移真实文件。

设计稿的目录搜索只搜索上述快照；仓库导入不联网拉文件。本地文件只使用浏览器提供的文件名和大小，不读取模型内容。未知仓库及本地包添加为待校验，不能直接设为当前模型。

## 新增其他资源

### APK 逆向工具

[现有 manifest](../../../../examples/apktool/manifest.json) 的插件 ID 为 `com.operit.apk_reverse_toolkit`，版本为 `1.1.0`，声明四个运行时 JAR：

- `resources/apktool/apktool-runtime-android.jar`
- `resources/apktool/android-framework.jar`
- `resources/apktool/jadx-runtime-android.jar`
- `resources/apktool/apk-reverse-helper-runtime-android.jar`

“内置插件”是目录与注册身份；“随 APK 提供 / 已下载 / 未准备”是资源来源与状态。身份与状态分开显示。实际完整包内资源可用时，不展示下载、导入、删除入口；精简包中资源缺失时，身份仍为内置插件，允许用户准备资源。必须避免同一插件被注册或装载两次。

下载只准备资源，插件启用、权限和工具接口与资源状态分开管理。设计稿中的约 25 MB 来自维护者估算，尚未测得最终外置归档体积；当前没有创建或发布该资源包。

### 九种工程模板

目录为 `app/src/main/assets/templates/`，真实子目录正好为 `android`、`flutter`、`go`、`java`、`node`、`office`、`python`、`typescript`、`web`。

设计为独立卡片，支持单独准备、整组准备、导入与备份管理。源码文件未压缩合计约 9.4 MiB，其中主要是 Android 与 Flutter；这不是最终下载大小。未生成归档前，界面不捏造九种模板各自的下载体积，也不把约 7 MB 估算直接当作 APK 减包结果。

### OCR 模型

当前 [Gradle 目录](../../../../gradle/libs.versions.toml) 声明 ML Kit 拉丁文、中文、日文、韩文及 Devanagari 文字识别依赖；[OCRUtils](../../../../app/src/main/java/com/ai/assistance/operit/util/OCRUtils.kt) 使用 ML Kit recognizer 接口。

这些资源随依赖打入 APK，并不是现有 `assets/models/` 下一个可直接搬走的普通模型文件。外置方案需要核对实际加载接口、语言范围与分发方式。设计稿保留包内可用和按需准备两种状态，大小标为待核验，不声称已完成约 5 MB 的拆包。

## 最后阶段：终端预装开发环境

Node.js、Python、Android 开发、Rust、Go 预装入 Ubuntu 归档，以及相关配置入口和接口的删除或调整，单独放到最后评估。本轮仅保留原有安装归档的状态表达，不展示“导入即全部环境配置完成”。## 原生实施补充

Android 实现与固定资源目录已加入本分支，当前行为以 [原生实现](01-native-implementation.md) 为准。前文“设计 / 候选 / 待拆分”保留 HTML 阶段的来源依据。工程模板、逆向资源及 OCR 资产已拆分，真实体积来自已生成归档；OCR 使用 Android 资产加载器保留 ML Kit 路径，兼容限制与未运行项见 [验证记录](02-verification.md)。文件就绪不代表推理效果已实测。
