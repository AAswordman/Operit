---
For_Agent: ToolPkg API 1.0.1 消息长按菜单与 Compose DSL 弹窗扩展记录
---

# ToolPkg 消息长按菜单与弹窗 DSL

## 原状

- 消息长按菜单由宿主硬编码，ToolPkg 无法向单条消息菜单注册动作。
- PR1066 分支里的消息翻译倾向于新增宿主侧专用弹窗，扩展方式不够通用。
- Compose DSL 适合承载轻量原生 UI，但缺少可从消息菜单打开的弹窗入口。

## 意图

- 在 ToolPkg API `1.0.1` 中开放 `ToolPkg.registerChatMessageMenuItem(...)`。
- 点击菜单项后把当前 `chatId`、消息索引和消息快照传给插件函数。
- 插件可以声明 `dialog.screen`，由宿主以 Compose DSL 弹窗 Host 加载包内 UI 资源。
- 内置“消息翻译”作为示例包实现，不在聊天宿主里硬编码翻译业务。

## 作用域

- ToolPkg 注册捕获、manifest 解析和运行时 bridge。
- ChatArea 长按菜单 registry 接入。
- Compose DSL `AlertDialog` / `Dialog` 节点与消息菜单弹窗 Host。
- `examples/message_translation` 示例与内置 assets `.toolpkg`。
- `examples/types` 和 ToolPkg 开发文档。
