# 1. 基线、发布状态与决策门

## 旧实现与现状

- 上游：`https://github.com/AAswordman/Operit`
- 审计基线：`a57857263f3291b41cf306a95ebcbe0e5c2b1373`
- 基线提交时间：`2026-07-26T08:26:22+08:00`
- 本地 `main` 与 `origin/main` 的 ahead/behind 为 `0/0`。
- 正式版 applicationId 为 `com.ai.assistance.operit`。
- 当前 `main` 的版本为 `versionName = "1.12.0+4"`、`versionCode = 44`。
- 最新正式 GitHub Release 是 `v1.12.0`，发布时间为 `2026-07-01T14:47:17Z`；标签指向 `fc76cf5b5086c9ca85eba54384588dccd729315c`，该标签内版本为 `1.12.0 (44)`。
- 当前 `main` 的 `1.12.0+4 (44)` 没有对应正式标签或 GitHub Release，因此不能直接作为新的 F-Droid 版本发布。
- 历史标签存在重复 versionCode：例如 `v1.0.0` 至 `v1.1.3` 多次使用 2，`v1.7.1` 至 `v1.9.1` 多次使用 39；部分标签内 versionName 也与标签不一致。F-Droid 首次提交可以只选择合规的新版本，但后续必须执行唯一递增策略。
- `.gitmodules` 仍含私有 SSH 子模块 `tools/hotbuild/OperitNightlyRelease`。

## 上游最新改造核实

- `main` 已不再把整个 `models.zip` 作为唯一模型输入：`app/build.gradle.kts` 定义了 `syncSttModelAssets`，从清单下载模型并校验字节数与 SHA-256。
- 固定清单位于 `app/config/stt-model-assets.properties`，来源已改为固定 Hugging Face commit URL。
- Silero VAD 条目标注为 MIT，并固定来源 commit 与 SHA-256。
- Sherpa-NCNN 模型条目虽然固定了来源 commit、大小和 SHA-256，但清单明确写着 `upstream license not declared in model metadata`，因此目前仍不能作为 F-Droid APK 的合规构建输入。
- `app/build.gradle.kts` 仍直接引用 `app/libs/ffmpeg-kit-local.aar`；`.gitignore` 仍忽略 `app/libs/*`、`app/src/main/jniLibs`、模型目录和 `subpack`。干净检出仍缺少部分构建输入。
- ML Kit OCR 依赖仍在正式依赖图中。
- 当前改造只解决了部分 STT 模型下载与校验，没有完成 F-Droid 闭环。

## 结论

1. 已确认 `v1.12.0` 是正式发布版本。
2. 已确认当前 `main` 的 `1.12.0+4 (44)` 尚不是可供 F-Droid 使用的新正式发布版本。
3. applicationId 暂按任务默认值保持 `com.ai.assistance.operit`。
4. 新 F-Droid 发布必须分配大于 44 且从未使用过的 versionCode，并创建指向精确审计提交的新标签。
5. 在负责人确认最终版本号、标签以及发布权限前，可以继续审计和改造，但不能创建正式标签或宣称发布完成。

## 待负责人确认

- 当前开发线是否按“已经向真实使用者发布”处理，以决定接口改造时是否必须保留向前兼容。
- F-Droid 首发拟使用的最终 versionName、唯一 versionCode 和标签；建议等改造完成后再确定，versionCode 至少为 45。
- 是否确认继续使用 `com.ai.assistance.operit`。
- 可推送的上游 fork、fdroiddata fork，以及 PR/MR/Release 权限。

## 验证证据

```text
HEAD: a57857263f3291b41cf306a95ebcbe0e5c2b1373
HEAD...origin/main: 0 0
latest release: v1.12.0
release tag commit: fc76cf5b5086c9ca85eba54384588dccd729315c
main version: 1.12.0+4 (44)
v1.12.0 version: 1.12.0 (44)
```

本步骤仅执行读取、远端同步和静态审计；未运行 Gradle 构建或测试。

[DONE]
