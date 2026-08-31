# 输入器与消息皮肤

## 旧实现

V2 runtime 可解析 component skin，但聊天代码只读取 Material 色板。Agent 输入器固定使用 20dp 顶圆角和 `surface`；Classic 输入器固定使用 14dp 编辑器外框；Cursor AI Markdown 根节点透明。赛博包中已有的 composer/input/message skin 是未被消费的数据。

## 修正

- 提供只面向 Operit 自有 Compose UI 的 V2 skin surface，统一绘制 container、content、outline、radius、elevation 与 padding。
- Agent/Classic 根输入区消费 `composer`；实际编辑器根据焦点消费 `input` normal/focused；输入器内部主要按钮和选择器消费 `button`/`icon_button`。
- Cursor/Bubble 的 user/AI 叶节点消费 `message_user`/`message_assistant`，消息标题、正文、Markdown 和附件标签使用皮肤内容色；完整消息容器拥有不透明皮肤背景。

## 验收

- 赛博 Agent 与 Classic 输入器均显示 package 指定的背景、轮廓和圆角。
- 四种消息路径（Cursor/Bubble x user/AI）在星云背景上可读。
- 主题切换后同一运行时立即驱动组件颜色和几何，不保留固定聊天视觉。

## 进展

[DONE] 新增 `ThemeComponentSurfaceV2`，以激活主题的 resolved skin 绘制 container、content、outline、radius、elevation 和 content padding。

[DONE] Agent/Classic composer 与编辑器消费 `composer` 和 `input` skin；焦点变化会选择 `input.focused`。

[DONE] Cursor/Bubble 的 user/AI 正文消费 `message_user` 和 `message_assistant` skin。Cursor AI 的透明 Markdown 现在位于 package-owned assistant surface 内，不再直接落在主题背景图片上。
