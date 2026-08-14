# CI checks

`script/` 保存可复现的检查入口，`test/` 保存门禁逻辑的标准库单元测试。GitHub Actions 负责准备 runner，并按变更范围调用这些入口。

## Candidate contract

PR 技术预审只运行在 GitHub 为当前 PR 和最新目标分支生成的 merge candidate 上：

```text
base = candidate^1
head = candidate^2
changed paths = git diff base..candidate
workspace = candidate
```

`ci/script/pr_check.py` 会验证两个父提交与 PR 事件中的 base/head 完全一致。路径分类、静态检查、测试和构建都使用同一个 candidate，不检出贡献者 head，也不比较 base tip 与过时 head。

## Local checks

本地检查需要显式指定比较边界。在干净的功能分支上运行：

```bash
git fetch upstream main
BASE_SHA="$(git merge-base upstream/main HEAD)"
CANDIDATE_SHA="HEAD"

python3 -B -m unittest discover -s ci/test -p 'test_*.py'
python3 -B ci/script/check_repo_hygiene.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_markdown_links.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_localizations.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
```

本地分支不是 GitHub merge candidate，因此本地结果用于提交前检查；PR 页面上的 `Candidate checks` 才验证与当前 `main` 合并后的实际树。

## PR check lanes

每个 PR 保留一个 `Candidate checks` 聚合技术状态，并在适用时显示独立的 `Android build`
和 `Android JVM tests` job。快速检查会先收集全部诊断，失败后不启动耗时阶段。

- 所有改动：空白、冲突标记、JSON/XML 语法和门禁单元测试；变更 YAML 时使用 Psych AST 与 actionlint 1.7.12 检查
- 所有改动：比较 base/candidate 两棵 Git tree，只阻断 candidate 新增的本地断链；删除被文档引用的非 Markdown 文件也会检查
- 本地化：按 locale、资源类型和 key 比较，只阻断 candidate 引入或实际触碰的错误
- 翻译资源：运行 AAPT2 resource compile 检查资源语法，不执行 resource link 或完整 Android 构建
- Kotlin/Java 和普通 Android 资源：运行 JVM unit tests
- Kotlin/Java 和普通 Android 资源：由 `Android JVM tests` job 运行 JVM unit tests
- Native、Gradle 和构建输入：由 `Android build` job 单独运行 assemble；同一作用域的 JVM unit tests 在独立 job 运行
- WebChat：运行 TypeScript typecheck 与 Vite build
- ToolPkg：重建并核对 GitHub 示例，按独立锁文件编译 WASM 示例，再构建测试集合和生产白名单集合；JSON manifest 声明的入口与 WASM 文件必须存在且进入归档

根项目、`web-chat` 和独立的 `examples/toolpkg_wasm_demo` 分别提交 `package-lock.json`，CI 使用 `npm ci` 安装确定的依赖树。

PR workflow 只有 `contents: read` 权限，不读取仓库 secret，也不上传 APK/AAB。`Android Build`
是只负责编译和打包的可信 main/手工构建 workflow，`Android Tests` 单独负责可信 main/手工 JVM 单测。

## Diagnostics

检查器在日志中按规则汇总错误，并通过 GitHub annotation 标记首批文件位置。Step summary 会记录 base、head、candidate、路径作用域及快速检查结果。历史问题只显示计数，不归责给未触碰它们的 PR。

## Android dependencies

MNN 使用官方 `3.6.1` 发布提交 `d407447ed56c4121a11ccbd266dc184ca1ead0c2`，不跟随 `master`；其余原生源码依赖仍由各模块通过 `cmake/operit_git_source.cmake` 下载固定 GitHub archive。

JVM lane 只下载 `libs.zip`，完整 Android lane 下载 `libs.zip`、`subpack.zip` 和 `jniLibs.zip` 三个固定归档。`download_android_dependencies.sh` 使用固定 Google Drive file ID；`prepare_android_dependencies.py` 限制成员数量、解压大小、压缩比和文件类型，重建固定输出根目录，只验证本次实际解出的文件，并拒绝越界路径、重复成员及符号链接。DragonBones 等原生源码依赖由各模块通过 `cmake/operit_git_source.cmake` 下载固定 GitHub archive。`arsc.jar` 已无消费者，`smart-exception` 改由 Maven Central 提供，`android-gif-drawable` 的重复 native 库也不会从归档写入构建目录。应用打包前，Gradle 会检查外部构建的 `liboperit_ripgrep.so`，以及本地 FFmpegKit AAR 中 10 个 arm64 native 库；缺失或零长度文件会使打包失败。默认本地 STT 模型不再来自 Drive 归档，由 Gradle 按 `app/config/stt-model-assets.properties` 获取到生成 assets 目录并校验 SHA-256。

这些 Drive 归档目前还没有内容 hash。归档内容寻址与许可证清单继续由[外部制品清单计划](../docs/TODO/refactor_building_sys/3_ExternalArtifactManifest.md)跟踪，在取得并审计真实归档前不记录推测值。STT 模型资产已有逐文件来源和 hash，但 Sherpa NCNN 模型上游元数据尚未声明许可证，发布前仍需完成许可证审计。
