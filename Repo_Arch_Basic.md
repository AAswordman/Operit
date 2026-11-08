# 根目录文件夹用途说明

本文说明仓库根目录布局，描述了其用途和当前状态

## 目录总览

```text
Operit/
	app/             主 Android 应用
	ci/              CI 与本地自动化脚本
	docs/            项目文档与文档资源
	examples/        JavaScript/TypeScript 工具包示例
	gradle/          Gradle Wrapper 与版本目录
	llm/             本地大模型 Android 模块
		llama/        llama.cpp 本地推理 Android 模块
		mnn/          MNN 本地推理 Android 模块
	quickjs/         QuickJS JNI 模块
	showerclient/    Shower 虚拟显示客户端库
	terminal/        OperitTerminalCore Git 子模块
	tools/           开发、调试与构建辅助工具
	web-chat/        应用集成的 Web Chat 界面
```

## 各目录说明

### [`app`](app/)

这是 Operit 的主 Android 应用模块。应用界面、业务逻辑、Android 资源、工具系统、项目模板和 ObjectBox 模型都集中在这里，同时负责接入各个本地原生能力。最终 APK 的主要应用代码位于此目录。

### [`ci`](ci/)

这是 CI 和本地自动化脚本目录，包含 PR merge candidate 分类、仓库卫生、本地化、Markdown、Android 依赖准备及对应单元测试。GitHub Actions 负责准备 runner、汇总快速检查，并按作用域编排 WebChat、ToolPkg 与 Android 构建命令。

### [`docs`](docs/)

这是项目文档与文档资源目录，包含核心开发指南、脚本和工具包开发说明、架构与协议文档，以及 README 使用的图片等静态资源。

### [`examples`](examples/)

这是工具包和脚本示例集合，主要使用 JavaScript 和 TypeScript，覆盖聊天、搜索、绘图、浏览器自动化、系统控制、地图等扩展场景。同名的 `.ts` 和 `.js` 文件通常分别用于源码示例和可执行版本。

### [`gradle`](gradle/)

这是 Gradle 构建支持目录，包含 Gradle Wrapper 配置和项目统一的版本目录 `libs.versions.toml`。

### [`llm`](llm/)

这是本地大模型 Android 模块的集中目录。Gradle 仍以原来的模块名接入这些子目录。

### [`llm/llama`](llm/llama/)

这是 llama.cpp Android 原生集成模块。它通过 CMake/JNI 接入 llama.cpp，为应用提供 GGUF 等本地大语言模型的推理能力。上游 llama.cpp 源码由 CMake 从 ggml-org 仓库获取，默认使用上游主分支。

### [`llm/mnn`](llm/mnn/)

这是 MNN Android 原生集成模块。它通过 CMake/Gradle 集成 Alibaba MNN，为应用提供本地模型推理及相关 AI 原生能力。MNN 源码由 CMake 从上游仓库获取，默认使用上游主分支。

### [`quickjs`](quickjs/)

这是 QuickJS JNI 模块本体，包含 QuickJS C 源码、JNI Runtime、Kotlin 封装和 Host Bridge。

当前 README 明确说明该模块尚未接入 `app`。

### [`showerclient`](showerclient/)

这是 Shower 客户端 Android 库，负责连接 Shower server、建立虚拟显示、发送触控和按键事件、请求截图，并提供可选的视频解码渲染组件。宿主应用需要注入 `ShellRunner` 并处理 Binder 广播。

### [`terminal`](terminal/)

这是 [OperitTerminalCore](https://github.com/AAswordman/OperitTerminalCore) Git 子模块，对应 Gradle 中的 `:terminal` 

### [`tools`](tools/)

这是开发、调试、构建和运行辅助工具集合，包含 JavaScript/ADB 执行脚本、ToolPkg 调试与 Hook、Shower 服务工具、Compose DSL 生成器、MCP Bridge、原生 ripgrep、字符串处理、内存分析以及桌面和 Android 辅助工程。（其中构建与测试内容会逐渐迁移到ci：python脚本会迁移到/ci/script下的命名文件夹，工具迁移到/ci/tools; 而app内置工具将使用tools_built-in目录）

### [`web-chat`](web-chat/)

这是APP集成的 Web Chat 前端，使用 React、Vite 和 TypeScript。构建后的静态文件会通过同步脚本放入 `app/src/main/assets/web-chat`

## 相关说明

- `app`、`terminal`、`llm/mnn`、`llm/llama`、`showerclient`、`quickjs` 是根 `settings.gradle.kts` 声明的 Gradle 模块。
- 仍作为 Git 子模块维护的第三方目录，以根目录的 `.gitmodules` 为准。
