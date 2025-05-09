# Operit AI - 智能助手应用

<div align="center">
  <img src="app/src/main/res/playstore-icon.png" width="120" height="120" alt="Operit Logo">
</div>

## 🌟 项目简介

Operit AI 是一款 Android 智能助手应用，可以帮你管理文件、搜索网页、自动操作屏幕等。它通过集成多种工具和AI技术，让手机变得更加智能和自动化。使用 Operit AI，你可以通过文字交流完成复杂任务，而不必在手机上反复点击和切换应用。Operit AI 实现了与大型语言模型的高级交互和插件扩展能力。

## 🚀 核心功能

### 🛠️ 多功能工具集
- **文件管理** - 对文件进行管理、编辑、转换、压缩和解压缩
- **网页搜索** - 集成网络搜索功能，直接获取互联网信息
- **屏幕自动化** - 通过无障碍服务实现对手机屏幕的自动化操作
- **系统集成** - 深度整合 Shizuku 特权 API，实现系统级操作

### 🔌 MCP 插件系统
- **首个移动端 MCP 支持** - 作为第一款支持 Model Context Protocol 的移动应用
- **插件部署与管理** - 完整的 MCP 服务部署、启动和监控功能
- **TypeScript 支持** - 内置编译和运行环境，支持复杂的 TypeScript 插件项目
- **自动化操作** - 可设置自动启动和部署选项，提高使用效率

### 📦 拓展包系统
- 支持用户添加他人/自己制作或内置的拓展包
- 实现特定软件的全自动处理流程
- 自定义功能扩展，提高适应性

### 🧠 增强 AI 能力
- **问题库** - 拥有向量索引的持久化数据库，专注于解决问题能力的积累
- **计划模式** - 增强上下文记忆能力，实现更精细的任务处理
- **偏好学习** - 根据对话自动总结并调整用户偏好，可选择锁定

### 🔒 权限控制
- 全面的权限模块，确保所有操作在用户控制范围内
- 自定义权限级别，确保系统安全
- 透明的权限管理机制

### 🧰 独立工具箱
- 单独的工具箱页面，可独立使用各种工具功能
- 快速访问常用工具，提高效率
- 界面友好，操作直观


## 📋 系统需求

- Android 8.0+ (API 级别 26+)
- 内存 4GB+（推荐 6GB 以上）
- 存储空间 200MB+
- 可选：Shizuku 环境（启用高级特权功能）
- 推荐：Termux 应用（用于 MCP 插件部署和执行）

## 🔧 快速上手

1. 从 Release 页面下载最新构建的 APK
2. 根据需要配置用户偏好和工具权限
3. 可选：安装 Termux 应用以启用 MCP 功能
4. 可选：搭建 Shizuku 环境以启用高级特权功能

## 👨‍💻 开源共创

欢迎加入 Operit 开源生态！我们欢迎两种类型的贡献者：

### 第三方脚本开发者

如果你希望为 Operit AI 开发第三方脚本和拓展包，我们已经为你准备好了开发环境：

- 项目的 `.vscode` 文件夹已配置好相关开发设置
- 已预先配置好 `package.json` 和 `tsconfig.json`
- 提供了 `index.d.ts` 类型定义文件，方便开发时获得类型提示

**快速开始开发脚本：**

1. 确保你的电脑上安装了 VSCode 和 ADB 环境
2. 克隆本仓库
3. 使用 VSCode 打开项目
4. 通过 ADB 连接你的 Android 设备
5. 在设备上打开 Operit AI 应用
6. 运行并调试你的 TypeScript 脚本，它将直接在手机上执行

可以参考 `examples` 目录下的 `tester` 示例了解如何开发脚本。我们计划将脚本开发框架单独拉出一个仓库，专门用于快速开始脚本开发。

### MCP 插件开发者

Operit AI 现已支持 MCP (Model Context Protocol) 插件系统，你可以开发自己的 MCP 插件：

1. 使用 TypeScript 创建你的 MCP 插件项目
2. 在 Operit AI 中导入你的插件仓库或直接上传 zip 文件
3. 自定义部署参数和环境变量
4. 一键部署并启动你的 MCP 服务

MCP 插件可以大幅扩展 AI 助手的能力，例如添加网页浏览、图像处理、数据分析等功能。

### 软件开发者

想参与 Operit AI 本体开发？很简单，提个 PR 就行了！（不是，其实还有点别的）

项目结构也很简单（~~乱七八糟~~）：

- `app/src/main/java/com/ai/assistance/operit/` - 主要代码都在这
  - `core/` - 核心功能（工具、配置什么的都在这）
  - `data/` - 数据层（DB啊，Bean啊）
  - `ui/` - 界面相关（好看的 UI 都在这）
  - `services/` - 各种服务（后台运行的那些）
  - `util/` - 工具类（各种骚操作都在这）
  - `api/` - API接口（和外部世界交流的地方）

关于代码风格，嗯...怎么说呢...（尴尬笑）

- commit 信息非常"创意丰富" ~~（完全不规范）~~
- 注释语言混搭风，中英文随心切换 ~~（想到啥写啥）~~
- 代码风格多元化 ~~（各写各的）~~

如果你是代码洁癖，可能需要做一下心理准备（或者直接帮我们重构？）。但好处是提交代码超简单，改好了提交 PR 就行，没那么多条条框框！

我们相信代码是写给人看的，只是偶尔让计算机执行一下。（所以...能跑就行？）

我们期待您的贡献，无论是提交 Issue、PR，还是参与讨论。请遵循项目的贡献指南（~~其实也没啥指南~~），共同打造更智能的 AI 助手。

> **关于项目维护**: 由于时间和精力有限，项目的活跃度和更新频率将很大程度上取决于社区的关注和参与。如果你觉得这个项目有价值，你的每一份贡献都会让它变得更好。无论是代码贡献、问题反馈还是使用推广，都能帮助项目持续发展。没有社区，就没有开源的未来。

## 📞 联系我们

- 项目主页：[Github Repository]
- 问题反馈：[Issue Tracker]
- 开发者社区：[Slack Channel]

## 📄 许可证

**特别说明：** 目前本项目为版权所有 AAswordsman，保留所有权利。由于参加比赛需要，暂时采用专有许可。我们计划在未来将许可证更改为 GNU 通用公共许可证(GPL)，届时将开放源代码并允许在 GPL 条款下进行使用和修改。

一旦项目转为 GNU 许可证，任何基于本项目的衍生作品也必须开源并采用相同许可证，从而使社区可以审查代码，确保没有恶意行为，为用户提供更高的安全保障。

请查看 [LICENSE](LICENSE) 文件了解当前的版权详情。

## 📝 TODO 清单

以下是我们正在计划的功能（咕咕咕...）：

- 接入多模态 AI，让它能看懂图片（毕竟文字只是世界的一小部分）
- 支持 Claude 的双向 stream（让对话像聊天一样自然）
- 支持语音输入和文本朗读（有时候手指真的很累啊）
- 加入更多的第三方包（梦想是成为万能的瑞士军刀）
- 优化问题库并增强其持久记忆能力（比鱼的记忆力强一点）
- 把问题库的搜索优化，支持 TF 生成向量（让搜索变得更聪明）

## 🐛 已知 BUG

目前存在的一些小问题（好吧，有些可能不那么小）：

- 对话取消可能需要点两次：因为匹配 bug，无法区分正常取消和 bug 取消（它有点听不懂"不"字）

如果你发现了其他 bug，欢迎提交 issue 告诉我们！我们会尽快修复...除非我们太忙或者太懒或者实在改不动（开个玩笑，我们真的会尽力的）。
