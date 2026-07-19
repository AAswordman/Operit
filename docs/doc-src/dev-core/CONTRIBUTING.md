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
git clone --recurse-submodules https://github.com/<your-account>/Operit.git
cd Operit
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
python3 ci/script/check_repo_hygiene.py
python3 ci/script/check_markdown_links.py
python3 ci/script/check_localizations.py
npm --prefix web-chat run typecheck
```

根据改动范围选择额外检查：

```bash
# WebChat
npm --prefix web-chat install
npm --prefix web-chat run build

# 示例包或 ToolPkg
python3 ./tools/sync_example_packages.py

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
- 四项必填检查清单全部勾选；不适用项在“验证方式”中写明 `N/A` 和原因

草稿 PR 转为 Ready 时会重新运行完整的 PR Required 检查。单独编辑标题或正文不会触发耗时构建，PR policy 会在下一次提交同步时重新校验。

PR 标题需要使用 Conventional Commits 格式，例如：

```text
fix(chat): preserve scroll position
feat(tools): add package description
docs: update contribution guide
ci: add localization checks
```

`feat`、`fix` 和 `perf` 类型的 PR 必须关联 Issue。不要把 API Key、Token、私有 URL 或本地路径写入 PR。

## CI 检查

PR 会进入 [PR Required workflow](../../../.github/workflows/pr-required.yml)，并生成一个 `CI required` 聚合检查：

- `Repository hygiene`：差异空白、冲突标记、JSON/XML/YAML 语法、Actions 语法和本地 Markdown 链接
- `Localization`：字符串 key、资源类型、重复项和占位符结构；既有缺失翻译先作为提示
- `WebChat checks`：TypeScript 类型检查和 WebChat 构建
- `Android Build`：涉及 Android、native、资源、构建输入或 WebChat 时执行构建、JVM 单测和 Android lint
- `Documentation advisory`：Markdown 风格和拼写提示，目前不作为阻断条件

请先查看失败 job 的具体日志，再更新 PR。同步上游后，应重新确认 CI 使用的是最新 `main` 和当前 PR head。

## 社区项目与衍生项目

欢迎基于 Operit 开发衍生项目。请在公开代码托管平台发布源代码，在项目文档中注明 Operit 的来源并链接回本仓库，方便社区审查、学习和继续贡献。
