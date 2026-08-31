---
fork: https://github.com/luojiaping/Operit
status: active
---

# 异形组件边框

## 背景

赛博主题已经覆盖颜色、场景背景和基础描边，但聊天组件仍由统一的圆角矩形 surface 绘制。输入器、角色栏、消息和图标按钮因此缺少可辨识的 HUD 几何层级。

当前 V2 主题包尚未对外发布，可以直接替换 skin 的边框描述，不保留旧的圆角和整圈 outline 字段。

## 目标

建立由主题包显式声明的 frame 契约，并在 Compose 中绘制以下组件边框：

- HUD 缺口框：composer、dialog、sheet
- 开放角括号框：角色栏、AI 消息、图标按钮
- 斜切卡片框：用户消息、列表项、状态块
- 分段轨道框：app bar 和页面分区

默认主题也必须通过新的显式 round-rect frame 继续表达常规 Material 外观。

## 范围

- 主应用的 V2 manifest、运行时解析器和组件 surface renderer
- Agent、Classic、Cursor、Bubble 聊天路径
- 默认和赛博主题源仓库的 manifest、归档锁和必要资源清理
- 契约和 Compose 像素回归测试

## 非目标

- 不复制外部仓库源代码
- 不给长消息增加持续动画或模糊滤镜
- 不改变聊天操作、语义、焦点顺序或输入行为

## 参考

- [Cyberpunk UI HUD card](https://github.com/rintran720/cyberpunk-ui/blob/main/src/components/card.css)
- [CyberTechUI](https://ehsanpo.github.io/CyberTechUI/)
- [Cosmic UI Lite](https://github.com/furic/cosmic-ui-lite/blob/main/demo.html)

## 步骤

1. [Frame contract](1_FrameContract.md)
2. [Renderer and chat mapping](2_RendererAndChatMapping.md)
3. [Theme packages and verification](3_ThemePackagesAndVerification.md)
