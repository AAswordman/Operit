# 根目录文件夹用途说明

本文说明仓库根目录布局，描述了其用途和当前状态

## 目录总览

```text
Operit-follow-up/
├── app/             主 Android 应用
├── ci/              CI 与本地自动化脚本
├── docs/            项目文档与文档资源
├── dragonbones/     DragonBones 动画 Android 库
├── examples/        JavaScript/TypeScript 工具包示例
├── fbx/             FBX 模型 Android 原生库
├── gradle/          Gradle Wrapper 与版本目录
├── llama/           llama.cpp 本地推理 Android 模块
├── mmd/             MMD 模型运行时与预览模块
├── mnn/             MNN 本地推理 Android 模块
├── quickjs/         QuickJS JNI 模块
├── showerclient/    Shower 虚拟显示客户端库
├── terminal/        OperitTerminalCore Git 子模块
├── tools/           开发、调试与构建辅助工具
└── web-chat/        React + Vite Web Chat 前端
```

## 各目录说明

### [`app`](app/)

这是 Operit 的主 Android 应用模块。应用界面、业务逻辑、Android 资源、工具系统、项目模板和 ObjectBox 模型都集中在这里，同时负责接入各个本地原生能力。最终 APK 的主要应用代码位于此目录。

### [`ci`](ci/)

这是 CI 和本地自动化脚本目录。目前包含用于修复 Windows 环境下 Git 子模块符号链接的脚本。约定统一使用 pixi 管理脚本依赖。

### [`docs`](docs/)

这是项目文档与文档资源目录，包含核心开发指南、脚本和工具包开发说明、架构与协议文档，以及 README 使用的图片等静态资源。

### [`dragonbones`](dragonbones/)

这是 DragonBones Android 库模块。它通过 C++、OpenGL 和 JNI 提供 DragonBones 骨骼动画模型的加载、渲染和动画控制，并向 Android View 与 Compose 使用场景提供封装。

### [`examples`](examples/)

这是工具包和脚本示例集合，主要使用 JavaScript 和 TypeScript，覆盖聊天、搜索、绘图、浏览器自动化、系统控制、地图等扩展场景。同名的 `.ts` 和 `.js` 文件通常分别用于源码示例和可执行版本。

### [`fbx`](fbx/)

这是 FBX Android 原生运行时模块。它通过 `ufbx` C 库解析 FBX 模型，为应用中的 FBX 虚拟形象和模型展示提供基础能力。`ufbx` 由 CMake 从上游仓库获取，默认使用 `main`。

### [`gradle`](gradle/)

这是 Gradle 构建支持目录，包含 Gradle Wrapper 配置和项目统一的版本目录 `libs.versions.toml`。

### [`llama`](llama/)

这是 llama.cpp Android 原生集成模块。它通过 CMake/JNI 接入 llama.cpp，为应用提供 GGUF 等本地大语言模型的推理能力。上游 llama.cpp 源码由 CMake 从 ggml-org 仓库获取，默认使用上游主分支。

### [`mmd`](mmd/)

这是 MMD 模型 Android 运行时和预览模块，包含模型渲染相关的原生集成，并使用 Bullet3 等第三方组件。Bullet3 由 CMake 从上游仓库获取，默认使用上游主分支。`UPSTREAM_SABA_VIEWER_MAPPING.md` 记录了上游 Saba Viewer 的映射关系。

### [`mnn`](mnn/)

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

- `app`、`dragonbones`、`terminal`、`mnn`、`llama`、`mmd`、`fbx`、`showerclient`、`quickjs` 是根 `settings.gradle.kts` 声明的 Gradle 模块。
- 仍作为 Git 子模块维护的第三方目录，以根目录的 `.gitmodules` 为准。
