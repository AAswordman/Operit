<h1 align="center">
  <img src="app/src/main/assets/logo.svg" width="48" height="48" align="absmiddle" alt=""> Operit AI
</h1>

<div align="center">
  <span>中文</span> | <a href="README(E).md">English</a>
  <br>
  <img src="https://img.shields.io/github/last-commit/AAswordman/Operit" alt="最近提交">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="平台">
  <a href="https://github.com/AAswordman/Operit/releases/latest"><img src="https://img.shields.io/github/v/release/AAswordman/Operit" alt="最新版本"></a>
  <a href="https://github.com/AAswordman/Operit/stargazers"><img src="https://img.shields.io/github/stars/AAswordman/Operit" alt="GitHub Star"></a>
  <br>
  <a href="https://operit.app/"><img src="https://img.shields.io/badge/📖-用户指南-blue.svg" alt="用户指南"></a>
  <a href="docs/doc-src/dev-core/CONTRIBUTING.md"><img src="https://img.shields.io/badge/contributions-welcome-brightgreen.svg" alt="欢迎贡献"></a>
</div>

<div align="center">
  <img src="docs/assets/readme/operit-ai-banner-zh-cn.webp" width="100%" alt="Operit AI - Android 史上最强大、功能最完整、持续迭代最久的开源 AI Agent">
</div>

## 🚀 Operit 2：Operit 的跨平台后续版本

本仓库是 Operit 的 Android 版。Operit 2 是独立的第二代实现，以 Rust 共享运行时、Flutter 客户端和 `operit2` CLI/TUI 为核心，目前包含 Android、iOS、Windows、macOS、Linux 与 Web 的实现或构建路径，并持续推进 OpenHarmony 适配。想关注跨平台版本，请访问 [Operit 2](https://github.com/AAswordman/Operit2)。

<a href="https://github.com/AAswordman/Operit2">
  <img src="docs/assets/readme/operit2-matrix-cards-zh-cn.png" width="100%" alt="Operit 2 跨平台开源 AI Agent">
</a>

## 项目简介

**Operit AI** 是一个运行在 Android 上的开源 AI Agent 平台，支持接入云端或本地模型，并将模型与 Android 系统、终端、浏览器、文件和项目工作区连接起来，通过工具调用、工作流以及 ToolPkg、MCP、Skill 等扩展能力，完成信息检索、文件处理、代码开发和设备自动化等实际任务。

## 核心特点

- **面向任务的 Agent 工作台**：支持附件、工作区上下文、工具执行过程、文件变更和多轮任务管理
- **自主选择模型与服务**：连接主流云模型、自定义兼容端点和密钥池，也可使用 MNN 模型或通过 llama.cpp 运行 GGUF 模型
- **深度集成 Android 与网页环境**：在授权后调用系统能力、操作应用界面，或通过内置浏览器读取和操作网页
- **移动开发与终端环境**：在手机上管理项目、编辑代码、预览网页，并使用 Ubuntu 24.04 用户空间、SSH 和 SFTP
- **长期记忆与角色体系**：管理图谱化记忆、对话历史、角色卡和多角色对话，并为不同角色绑定独立能力
- **可组合的扩展与自动化**：通过统一市场、工作流和系统集成组合工具，构建可重复执行的任务流程

## 功能展示

<a href="docs/assets/readme/operit-agent-task-flow-2400x1000-v3.png">
  <img src="docs/assets/readme/operit-agent-task-flow-2400x1000-v3.png" width="100%" alt="Agent 任务执行：从需求输入、工具执行与 Diff 审查，到实时预览调试和结果交付">
</a>

<a href="docs/assets/readme/operit-android-automation-2400x1000-v2.png">
  <img src="docs/assets/readme/operit-android-automation-2400x1000-v2.png" width="100%" alt="Android 自动化演示">
</a>
<a href="docs/assets/readme/operit-memory-multicharacter-chat-2400x1000-v2.png">
  <img src="docs/assets/readme/operit-memory-multicharacter-chat-2400x1000-v2.png" width="100%" alt="记忆与多角色对话演示">
</a>
<a href="docs/assets/readme/operit-workspace-ubuntu-workflow-2400x1000.png">
  <img src="docs/assets/readme/operit-workspace-ubuntu-workflow-2400x1000.png" width="100%" alt="工作区与 Ubuntu 工作流演示">
</a>
<a href="docs/assets/readme/operit-plugin-ecosystem-agent-creation-2400x1000-v3.png">
  <img src="docs/assets/readme/operit-plugin-ecosystem-agent-creation-2400x1000-v3.png" width="100%" alt="插件生态与 Agent 创建演示">
</a>

## 主要功能

<details>
<summary><b>AI 对话与模型</b></summary>

- 对话支持图片、音频、视频、文档、工作区文件等上下文
- 支持消息分支、历史分组与迁移、自动总结、上下文限制和并行对话
- 支持 OpenAI Chat/Responses、Anthropic、Gemini 及多种兼容服务，并可通过 ToolPkg 扩展模型提供方
- 支持多套模型配置、参数设置、密钥池和连接测试
- 可分别为聊天、记忆、总结、UI 控制等任务指定模型
- 内置 MNN 本地推理，并支持通过 llama.cpp 运行 GGUF 模型
- 可连接 Ollama、LM Studio 等本地模型服务

</details>

<details>
<summary><b>工具、设备与浏览器自动化</b></summary>

- 内置文件、网络、搜索、媒体、系统、软件管理和开发工具
- 提供自动允许、每次询问和禁止三档工具权限设置，默认采用询问模式
- UI 自动化支持无障碍、Shizuku 提供的 ADB 级调试权限和 Root 通道
- PhoneAgent/AutoGLM 可结合屏幕视觉执行操作，虚拟显示等能力需要相应权限和设备支持
- 内置浏览器提供多标签、历史、书签、下载、权限、多窗口和用户脚本
- 浏览器 Agent 可读取页面结构并执行点击、输入、滚动、按键和截图等操作
- 支持 OCR、图像理解、相机、FFmpeg、网页访问和文件传输

</details>

<details>
<summary><b>项目工作区与终端</b></summary>

- 支持 Web、Android、Flutter、Node.js、TypeScript、Python、Java、Go 等项目模板
- 提供文件树、代码编辑、语法高亮、实时预览、变更追踪、备份和导出
- 聊天可以绑定工作区，让 AI 读取项目规则、引用文件并修改代码
- 工作区支持应用内部目录、SAF、SFTP 和 SSH 文件系统
- 内置 Ubuntu 24.04 ARM64 用户空间，默认通过 PRoot 运行，在具备相应条件时也可使用 chroot
- 终端支持多会话、Python、Node.js、vim、SSH、tmux、自定义按键和软件源
- 提供 Logcat、SQLite、Git、APKTool、HTML 打包等开发工具

</details>

<details>
<summary><b>记忆、角色与对话管理</b></summary>

- 支持多个记忆空间、文档导入与分块、节点关系编辑和混合搜索
- 可从对话和附件中提取信息，并按时间、语义及关系检索长期记忆
- 支持角色卡导入、导出、备份和二维码分享，兼容 Tavern JSON/PNG 等格式
- 每个角色可绑定独立模型、记忆空间、工具包、Skill 和 MCP
- 支持多角色群聊、@ 交互、独立对话历史和角色间协作
- 支持聊天记录导入导出、锁定、分支、批量迁移、备份与恢复

</details>

<details>
<summary><b>扩展市场与工作流</b></summary>

- 统一市场支持脚本、ToolPkg、Skill 和 MCP 的搜索、安装与管理
- 另有提示词与标签市场，以及项目和 Artifact 的管理与发布流程
- ToolPkg 可提供工具、界面、模型提供方、Hook 和运行时能力
- MCP 支持本地与远程服务，以及 uvx、npx 等启动方式
- 可视化工作流包含触发、执行、条件、逻辑、数据提取等节点
- 工作流支持手动、定时、Tasker、Intent、语音和应用启动等触发方式
- 提供工作流执行日志、统计、取消和批量管理

</details>

<details>
<summary><b>语音、形象与界面</b></summary>

- STT 支持本地中英文识别以及 OpenAI、Deepgram 等云端接口
- TTS 支持 Android 系统、本地 ONNX VITS、自定义 HTTP 和多种云服务
- 支持连续语音对话、后台唤醒、自定义唤醒模板、自动朗读和音乐播放队列
- 可通过悬浮窗、气泡、桌面组件和 Android 默认助理入口调用 Operit
- 虚拟形象支持 DragonBones、WebP、MP4、MMD、glTF/GLB 和 FBX 等格式
- 支持主题、字体、聊天气泡、背景、工具栏、Markdown 渲染和布局定制
- 界面支持中文、英语、韩语、西班牙语、马来语、印尼语、巴西葡萄牙语和罗马尼亚语，可跟随系统或手动切换
- 可选的局域网 Web Chat 和 HTTP API 默认关闭，启用时需要配置 Bearer Token 并评估局域网暴露风险

</details>

## 快速开始

| 项目 | 说明 |
|------|------|
| **系统要求** | Android 8.0（API 26）或更高版本，仅支持 ARM64（`arm64-v8a`）设备 |
| **资源需求** | 内存和存储占用取决于终端环境、已安装工具包及本地模型；下载模型前请按模型说明预留空间 |
| **下载安装** | 从 [Releases 页面](https://github.com/AAswordman/Operit/releases) 下载最新 APK |
| **使用指南** | 访问 [Operit 官方网站](https://operit.app/) 查看教程和示例 |

> **安全提示：** 请仅从官方 [Releases 页面](https://github.com/AAswordman/Operit/releases) 或 [Operit 官方网站](https://operit.app/) 下载安装包。未知渠道的安装包可能被修改，造成数据和设备安全风险。

安装流程：下载 APK → 安装并启动 → 按引导配置模型与权限 → 开始使用

> **数据与网络边界：** 聊天、角色、记忆和模型配置由应用保存在本地；使用云模型时，请求从设备发送到您配置的服务商，Operit 不托管聊天推理。市场、MCP、Skill、语音和绘图等功能可能连接第三方服务；Web Chat/HTTP API 默认关闭，启用前请确认网络暴露与 Bearer Token 设置，Android Intent/广播集成也只应交给受信应用。

## 发展历程

- **2025 年 4 月至 8 月 · 从 AI 对话到工具执行**：逐步加入工具调用、MCP、语音、悬浮窗、Web 开发和角色卡
- **2025 年 9 月至 12 月 · 深入设备与开发环境**：完成 Ubuntu 终端、记忆系统、MNN、SSH 工作区、GUI 自动化和 Skill 生态
- **2026 年至今 · 形成移动 Agent 平台**：扩展工作流、GGUF 本地推理、浏览器 Agent、角色群聊、Web Chat、ToolPkg 和统一市场

<details>
<summary><b>查看主要版本更新摘要</b></summary>

- **v1.12.0 · 2026-07-01**：统一市场与 Artifact 流程，增强项目工作区和 ToolPkg 运行时，扩展多语言、语音与媒体能力，并提升崩溃恢复能力
- **v1.11.0 · 2026-05-16**：加入 Web Chat、Artifact 市场、ToolPkg AI Provider 与 Hook，并增强记忆、上下文和浏览器自动化
- **v1.10.1 · 2026-04-17**：升级内置浏览器与网页自动化，加入 FBX 形象、液态玻璃主题和本地 HTTP 对话入口
- **v1.10.0 · 2026-03-18**：加入角色群聊、AI 自配置、Ollama/NVIDIA/OpenAI Responses 模式和更多开发工具
- **v1.9.1 · 2026-02-20**：集中修复终端、严格工具调用、远程 MCP、记忆库和工作流问题
- **v1.9.0 · 2026-02-17**：加入移动网页自动操作、Windows 终端控制、SQLite 查看器和 Android 工作区模板
- **v1.8.1 · 2026-02-03**：加入 llama.cpp GGUF 本地推理，并增强界面、备份、Skill 和工作区能力
- **v1.8.0 · 2026-01-13**：推出工作流、语音唤醒触发、并行对话和自动备份
- **v1.7.1 · 2025-12-31**：加入 Root 虚拟显示、Skill 协议与市场、圈选识屏和对话锁定
- **v1.7.0 · 2025-12-19**：推出 AutoGLM GUI 自动化和虚拟显示
- **v1.6.4 · 2025-12-12**：接入 AutoGLM，优化自动化状态展示、工具包环境变量和调试日志
- **v1.6.3 · 2025-12-07**：支持原生 Tool Call、多模型配置、SSH 文件系统和工作区项目模板
- **v1.6.2 · 2025-11-22**：增强对话分支与迁移、上下文绑定和学术检索
- **v1.6.1 · 2025-11-16**：重构 UI 渲染，加入视觉理解、SSH 终端、自动总结和深度搜索
- **v1.6.0 · 2025-10-21**：加入 MNN 本地模型、智能记忆库、Tasker 集成和桌宠
- **v1.5.2 · 2025-10-05**：增强 MCP、工作区 `.gitignore` 支持、相机、HTML 渲染和正则过滤
- **v1.5.1 · 2025-09-25**：加入 MCP 市场、密钥池、对话重发时的工作区回档和记忆库手动刷新
- **v1.5.0 · 2025-09-20**：集成 Ubuntu 24.04 终端和深度搜索模式
- **v1.4.0 · 2025-09-01**：加入多工具并行、角色卡系统和 PNG 角色卡导入
- **v1.3.0 · 2025-08-04**：加入 Web 开发、主题选择器和 Anthropic Claude
- **v1.2.x · 2025-07**：加入语音对话、知识库和 DragonBones 动画
- **v1.1.x · 2025-05 至 06**：加入 MCP、OCR、悬浮窗和 Gemini 支持
- **v1.0.0 · 2025-04-11**：发布基础 AI 对话、工具调用及 Shizuku/Root 集成

</details>

完整更新内容请查看 [Releases 页面](https://github.com/AAswordman/Operit/releases)。

## 开源共创

欢迎参与 Operit 的脚本、扩展、文档和核心功能开发。

- [贡献指南](docs/doc-src/dev-core/CONTRIBUTING.md)
- [构建指南](docs/doc-src/dev-core/BUILDING.md)
- [脚本开发指南](docs/SCRIPT_DEV_GUIDE.md)
- [ToolPkg 格式指南](docs/TOOLPKG_FORMAT_GUIDE.md)
- 构建项目需准备 `subpack.zip`、`jniLibs.zip` 和 `libs.zip` 三份外部依赖归档，并初始化 `terminal` 子模块；默认本地 STT 模型会按照 `app/config/stt-model-assets.properties` 自动获取并校验
- 社区讨论：[QQ群](https://qm.qq.com/q/Sa4fKEH7sO) | [Discord](https://discord.gg/YnV9MWurRF)

### 贡献者

<a href="https://github.com/AAswordman/Operit/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=AAswordman/Operit" alt="Operit 贡献者">
</a>

## 支持开发

如果 Operit 对您有帮助，可以自愿支持项目持续开发与基础运营：

- 海外支持：[Patreon](https://www.patreon.com/c/aaswordsman)
- 境内支持：[爱发电](https://afdian.com/a/aaswordsman)
- 赞助完全自愿，不与任何功能、额度、更新、答疑或其他权益挂钩
- 不赞助不会影响正常使用、获取更新或访问开源代码

## 许可证

仓库主体代码采用 [GNU LGPL v3（LGPL-3.0-only）](https://spdx.org/licenses/LGPL-3.0-only.html)。仓库中的工具、示例、模板和第三方依赖可能采用其他许可证，具体条款以 [LICENSE](LICENSE)、对应目录中的许可证文件和包元数据为准。

## Star History

<a href="https://www.star-history.com/?repos=AAswordman%2FOperit&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=AAswordman/Operit&type=date&theme=dark&legend=top-left&sealed_token=CtZ-YNyNp2_W0AUVXHyAzBNeMlpGVkDx0RjuJPYdalj2Kvy7HkPBhb-uzKQ8t8jbMDGjRvnjHo7Fi-FtGIv-NaS0gqafGHDjgoGufAgx8yXt3gi8f4vV3aP8KskKramok4_vcKfo8Ii4AohtLRdX13mCJz0ABDqwtmec2SoK0j1kLuPyLz6oH3k4lhY4" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=AAswordman/Operit&type=date&legend=top-left&sealed_token=CtZ-YNyNp2_W0AUVXHyAzBNeMlpGVkDx0RjuJPYdalj2Kvy7HkPBhb-uzKQ8t8jbMDGjRvnjHo7Fi-FtGIv-NaS0gqafGHDjgoGufAgx8yXt3gi8f4vV3aP8KskKramok4_vcKfo8Ii4AohtLRdX13mCJz0ABDqwtmec2SoK0j1kLuPyLz6oH3k4lhY4" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=AAswordman/Operit&type=date&legend=top-left&sealed_token=CtZ-YNyNp2_W0AUVXHyAzBNeMlpGVkDx0RjuJPYdalj2Kvy7HkPBhb-uzKQ8t8jbMDGjRvnjHo7Fi-FtGIv-NaS0gqafGHDjgoGufAgx8yXt3gi8f4vV3aP8KskKramok4_vcKfo8Ii4AohtLRdX13mCJz0ABDqwtmec2SoK0j1kLuPyLz6oH3k4lhY4" />
 </picture>
</a>
---

<div align="center">
  <sub>Made with ❤️ by the Operit Team</sub>
</div>
