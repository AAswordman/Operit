# Operit 贡献指南

感谢你为 Operit 提交 Issue、文档、脚本、插件或代码。本文说明当前仓库的协作流程；构建细节请参考 [Android 编译指南](./BUILDING.md)，脚本和 ToolPkg 开发请参考 [脚本开发指南](../../SCRIPT_DEV_GUIDE.md)。

## 贡献类型

- Android 应用、工具调用、工作流、数据和 UI：主要位于 `app/`
- WebChat：位于 `web-chat/`，构建结果会同步到 Android assets
- 脚本、Skill、Plugin、MCP 和示例包：位于 `examples/`，格式说明见 [ToolPkg 指南](../../TOOLPKG_FORMAT_GUIDE.md)
- 文档和协作资料：位于 `docs/`
- 构建、检查和仓库自动化：位于 `.github/`、`ci/` 和 `tools/`

## 提交 Issue

请先在 [Issue 区](https://github.com/AAswordman/Operit/issues) 搜索相同问题，再选择对应的 Issue Form：

- Bug report：错误、崩溃和异常行为
- Feature request：功能和行为建议
- Plugin, Skill or MCP report：外部包、工具和 MCP 服务问题
- Question：使用、配置和行为咨询

提交时请填写完整版本号、运行环境、主要问题、复现步骤和相关配置。截图或日志字段用于补充证据，文字说明也可以。请勿提交 API Key、Token、Cookie、个人信息或其他敏感内容。

只有标题、没有正文和评论的 Issue 会被自动关闭。功能、Bug 和工具问题应尽量关联已有讨论，避免重复跟进。

## 开发前准备

项目是 Android 主应用，同时包含 native 子模块、WebChat 和脚本构建步骤。完整环境要求以 [Android 编译指南](./BUILDING.md) 为准，当前 CI 使用的主要版本包括：

- JDK 21
- Node.js 22、npm 和 pnpm
- Python 3
- Android SDK 34/36、Build Tools 34/35
- NDK 25.1.8937393 和 CMake 3.22.1

Fork 并克隆仓库后，建议保留 `upstream` 远程：

```bash
git clone https://github.com/<your-account>/Operit.git
cd Operit
git submodule update --init --recursive terminal
git remote add upstream https://github.com/AAswordman/Operit.git
git fetch upstream
git switch -c fix/short-description upstream/main
```

不要提交 `local.properties`、本地密钥、手动下载的模型和二进制依赖。修改 WebChat 或示例包时，先按照编译指南准备根目录和 `web-chat` 的依赖。

## 开发原则

- 先阅读相关模块和现有测试，再开始修改
- 保持 PR 聚焦，不把无关格式化、重命名和功能改动混在一起
- 变更持久化数据、配置格式、工具参数或公开行为时，说明兼容性和迁移影响
- 新增面向用户的文字时优先使用资源字符串或项目已有的多语言机制
- UI、行为或文档发生变化时，在 PR 中提供验证结果和必要的截图、日志或对比信息
- 不修改第三方子模块内容来解决主仓库问题；需要变更时单独说明来源和同步方式

## 本地检查

在仓库根目录可以复现主要的快速检查：

```bash
git fetch upstream main
BASE_SHA="$(git merge-base upstream/main HEAD)"
CANDIDATE_SHA="HEAD"

python3 -B -m unittest discover -s ci/test -p 'test_*.py'
python3 -B ci/script/check_repo_hygiene.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_markdown_links.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/check_localizations.py --base "$BASE_SHA" --candidate "$CANDIDATE_SHA"
python3 -B ci/script/normalize_lint_baseline.py --check
npm --prefix web-chat run typecheck
```

根据改动范围选择额外检查：

```bash
# WebChat
npm --prefix web-chat ci
npm --prefix web-chat run build

# 示例包或 ToolPkg
npm ci
npm run build:examples:github
git diff --exit-code -- examples/github.js
npm --prefix examples/toolpkg_wasm_demo ci
npm --prefix examples/toolpkg_wasm_demo run pack:toolpkg
python3 ./tools/sync_example_packages.py --mode test --no-hot-reload
python3 ./tools/sync_example_packages.py --no-hot-reload

# Android JVM 单测、lint 和构建
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew assembleDebug
```

本地构建需要手动依赖时，按 [Android 编译指南](./BUILDING.md) 下载并放置 `models.zip`、`subpack.zip`、`jniLibs.zip` 和 `libs.zip`，不要将这些文件提交到 Git。

## 创建 Pull Request

所有上游 PR 的目标分支是 `main`，不再使用旧的 `pr-branch` 流程。建议使用以下分支前缀：

- `feat/`：新功能
- `fix/`：问题修复
- `docs/`：文档
- `ci/`：构建和自动化
- `refactor/`：不改变行为的重构
- `test/`：测试改动

推送个人分支并创建 PR：

```bash
git fetch upstream
git rebase upstream/main
git push --set-upstream origin fix/short-description
```

PR 页面会自动加载 [PR 模板](../../../.github/PULL_REQUEST_TEMPLATE.md)。请保留并填写以下内容：

- 变更背景、动机和改动范围
- 关联 Issue；纯文档、CI 或维护性改动说明 `N/A` 及其背景
- 兼容性、数据、配置、性能和安全影响
- 运行过的命令、测试环境、构建变体和结果
- 必要的截图、录屏、日志或构建产物
- 提交前按检查清单自查；不适用项可在“验证方式”中说明原因

草稿 PR 转为 Ready 时会重新运行技术预审。标题、正文、Issue 引用和 checklist 由贡献者与维护者共同审阅，不作为自动技术检查的通过条件。

PR 标题建议使用 Conventional Commits 格式，例如：

```text
fix(chat): preserve scroll position
feat(tools): add package description
docs: update contribution guide
ci: add localization checks
```

功能、修复和性能改动建议关联已有 Issue 或讨论。不要把 API Key、Token、私有 URL 或本地路径写入 PR。

## CI 检查

PR 会进入 [PR Check workflow](../../../.github/workflows/pr-check.yml)，并只生成一个 `Candidate checks` 技术状态。检查运行在 GitHub 为 PR 与当前 `main` 生成的 merge candidate 上：

- 快速检查：差异空白、冲突标记、JSON/XML/YAML、Actions、本地 Markdown 链接和门禁单元测试
- 本地化：按 locale 和资源 key 归责类型、重复项、占位符及 locale 配置错误
- 翻译资源：执行 AAPT2 resource compile，不启动完整 Android 构建
- Kotlin/Java：执行 JVM 单测与 Android lint
- Native、Gradle 和构建输入：执行 assemble、JVM 单测与 Android lint
- WebChat 和 ToolPkg：对应路径变化时执行专项检查，完整 Android lane 也会准备最终打包输入

快速检查会在同一 job 中收集可修诊断，再统一给出一次结果。既有且未被本 PR 触碰的问题只作为计数提示。请查看 step summary 和文件 annotation 后更新 PR。

## 社区项目与衍生项目

欢迎基于 Operit 开发衍生项目。请在公开代码托管平台发布源代码，在项目文档中注明 Operit 的来源并链接回本仓库，方便社区审查、学习和继续贡献。
