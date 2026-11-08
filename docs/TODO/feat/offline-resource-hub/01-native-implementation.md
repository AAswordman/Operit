# 原生实现与兼容

页面采用两个固定等宽栏目、分类搜索、来源选择、详情面板和模板网格。状态来自文件校验，下载与导入完成后才更新可用状态；旋转屏幕保留当前操作，文件选择器恢复时按保存的资源 ID 解析目标。

大小统一使用十进制 B、KB、MB、GB 等单位；底部任务栏不随列表滚走，卡片和详情同步显示当前下载反馈。网速使用五秒滑动窗口，停滞后归零；缓存校验、传输、校验安装分阶段处理，剩余时间只按实际网络传输速度估算。详情显示可选择复制的真实位置，模板就绪补充勾选。包管理的插件与沙盒列表均支持“内置”筛选，模型上下文协议页签保留全称与四等宽布局。

## 模型与语音设置

新增 ONNX Zipformer STT，以及 AISHELL-3 int8、VITS zh-ll、Matcha Baker、Kitten nano int8 四种 TTS，使用固定版本、逐文件大小与 SHA-256。ModelScope 使用独立仓库与子目录；Matcha 声码器保留直接来源；Kitten 收录完整 espeak 数据树。目录资料参考 Operit2，Android 管理与推理代码在本项目实现；JNI 绑定来自 Apache-2.0 的 Sherpa ONNX v1.13.2，保留来源和许可证。

新增模型目录为 `runtime/models/local/{stt|tts}/sherpa_onnx/{id}/{version}`，记录为目录内的 `operit-model.json`。原 NCNN 的已发布路径和 ZIP 格式保持兼容。语音设置增加离线识别与合成提供方，只列出通过文件校验的安装记录；同页测试继续使用当前编辑快照。原 VITS/Piper 包配置作为明确的旧版入口保留。新增字段有默认值，已有档案无需迁移即可读取。后台唤醒继续使用原 NCNN。

共用引擎按需准备一次，使用独立 SONAME 和固定加载顺序，避免与宿主已有 ONNX Runtime 混用。加载后保留进程级引用，需重启应用后管理引擎。原生运行、音质与识别效果尚未实机验证。

仓库输入支持 Hugging Face、HF-Mirror、ModelScope。目录仓库匹配固定版本，自定义仓库必须提供包含驱动与摘要的 `operit-model.json`。本地包支持 ZIP、tar.gz、tar.bz2，识别已知模型或支持的 Sherpa 文件结构；NCNN 使用原资源卡导入。任意 ONNX 文件不会直接标记为可运行模型。

## 资源外置

九种模板、30 个沙盒脚本和 10 个 ToolPkg 从默认 APK 资产中移出，白名单与完整源码保留。下载包以固定清单校验后从私有资源目录扫描，来源标记为 RESOURCE，仍显示内置身份。未准备的扩展不进入已安装列表；准备、导入或移除后重扫来源，返回包管理时更新列表。原包 ID、启停记录、子包开关及外部覆盖规则保留。启用的扩展持有资源引用，关闭开关后才能移除。模板复制接资源安装目录，转换 gitignore 并保留可执行位；缺资源时不绑定工作区，创建面板提供准备入口。逆向 ToolPkg 保留同一插件 ID、工具入口和启用状态，插件本体与四个固定运行资源属于同一个下载项，为四个资源声明 `external_group=apk-reverse`；解析器与查找器接资源目录，导出时持有运行引用。

ML Kit 五个固定版本 16.0.0 的 AAR 去重后包含 61 个模型资产、5395925 字节。构建时仅剥离模型资产，保留描述符、JNI、语言 API 与原始许可。独立资源包保留原资产路径，内层采用 ZIP 存储模式支持 openFd/mmap。Android 11 及以上使用 ResourcesLoader，Android 8～10 使用这些版本既有的 addAssetPath 接口；该路径有仪器回归源码，尚未实机验证。模型继续由原 ML Kit 接口读取，不引入 Google Play 按需模块。OCR 资产加载后保持进程级占用。

默认外置以上资源；显式 `-Poperit.bundleProjectResources=true` 配合完整扩展生成步骤保留包内模板、扩展及 OCR。页面检测包内资源后只显示可用状态，不重复下载、导入或删除。完整构建选项尚未运行验证。

## 安装、取消与备份

下载使用独立缓存，校验完成后进入 staging；归档限制路径越界、重复文件、链接条目、展开体积与条目数量。替换安装在运行时注册表独占标记内切换目录，阻止新使用者进入；切换失败撤销本次变更，保留旧安装。取消清理未完成 staging，保留已完成缓存或资源。文件替换步骤不接收中途取消。

语音备份可携带共用引擎，导入预览同时校验引擎，恢复后不在模型目录保留重复副本；从特定模型卡导入时检查目标一致。导出失败或取消会清理未完成的 SAF 文档。Ubuntu 归档与已安装环境分别管理，仅移除归档不触碰环境；完整删除及未完成删除恢复继续使用原终端维护流程。

## 维护与分发

固定清单为 `app/config/offline-packs.json`，使用 Python 3.12、patchelf 0.18.0，以及固定上游归档和 OCR AAR 摘要。内容或模板改变时重新准备并发布新标签，不改写已发布版本；更新逆向运行 JAR 时还需同步插件版本及其工作目录缓存约定。

```bash
# 先取得与当前扩展源码一致的完整白名单产物，再准备固定资源
python3 ci/distribution/resources/prepare_hub.py --output resource-hub-assets --packages built-packages
python3 ci/distribution/resources/publish_hub.py --assets resource-hub-assets --repository 3316891527/Operit --commit <提交SHA>
```

资源包通过上面的独立发布脚本准备与发布；原 `publishOfflineResources` 继续管理旧四组资源。现有 Debug 与 JVM 工作流通过 Gradle 资产任务核对资源目录和源码；最终 APK 检查核对固定目录、VAD 和外置边界，拒绝任何内嵌脚本或 ToolPkg。白名单源码指纹改变后必须生成并发布新的资源目录。`--base-assets` 可复用经大小、归档摘要及逐文件摘要核对的既有引擎与 OCR 包。

原生实现 [DONE]。CI 与设备状态以验证文档为准。
