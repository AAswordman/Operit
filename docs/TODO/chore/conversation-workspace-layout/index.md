---
fork: https://github.com/3316891527/Operit
status: implemented-awaiting-ci
---

# 会话工作区按职责归类

原 `conversation.chat.webview` 同时包含工作区、代码编辑器、文件浏览器、网页预览、聊天提及候选面板和终端入口。原 `conversation.chat.workspace` 另存放工作区主页与导航。目录归属仍以早期网页预览实现为中心，无法反映这些功能的现有职责。

本批将工作区提升到 `ui.features.conversation.workspace`，沿用项目已有的复数 `features`，与 `chat` 平级。现有浏览、编辑、搜索、链接、处理和文件操作子包保留各自职责，网页预览归入 `workspace.preview`，主页与导航归入 `workspace.hub`。

- [x] 建立逐文件迁移清单，包含 72 个主源码文件、8 个 JVM 测试文件与 2 个 Android 测试文件
- [x] 移除旧 `chat.webview` 源码目录，工作区主页与导航一起迁出 `chat.workspace`
- [x] 提及候选面板归入 `chat.components.mentions` 并按实际组件改名为 `MentionSuggestionPanel.kt`；终端入口归入现有 `automation.terminal`
- [x] 同步所有调用、包声明、显式导入、星号导入和原同包隐式引用
- [x] 核对迁移前后声明体，确认仅包名、导入和显式路径发生变化
- [x] 更新当前开发文档中的路径，保留既往迁移清单的历史记录
- [x] 完成实施阶段本地迁移，后续交付记录由聊天目录整理分支和 PR 衔接

此次不改变应用身份、用户工作区目录、配置 JSON、预览服务端口、编辑器行为或文件操作规则。复制与删除的实现和回归用例随目录一起迁移。

后续[聊天职责整理](../chat-responsibility-layout/index.md)已继续归类聊天代码，提及候选面板的当前位置为 `chat.modules.input.mentions`。本页和 `files.json` 保留工作区迁移阶段的映射，最终位置由后一阶段清单继续衔接。

逐文件映射见 [files.json](files.json)，本批静态核对见[迁移验证记录](01-validation.md)，完整文件操作回归范围见[文件操作验证](../../fix/workspace-copy-streaming-progress/01-validation.md)。

[DONE] 本地迁移与静态核对；编译、测试和 CI 尚未验证本次迁移。
