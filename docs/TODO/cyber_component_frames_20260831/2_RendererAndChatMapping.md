# Renderer 与聊天映射

## 渲染策略

`ThemeComponentSurfaceV2` 负责 surface 填充、内容色、padding 和 frame 绘制。frame 使用 Compose path 和 draw cache，保持组件原有的 modifier、点击语义和内容布局。

仅固定高度 chrome 使用轻量辉光。可滚动的消息条目只绘制静态线条，避免滚动期间的模糊和动画开销。

## 赛博映射

| 组件 | Frame | 视觉职责 |
| --- | --- | --- |
| app bar | round rect + scene rail | 壳层场景提供页面级青色轨道和洋红强调段 |
| section | segmented rail | 角色栏的青色轨道和洋红强调段 |
| composer | HUD notched | 输入区域的主视觉框 |
| input | corner brackets | 普通和 focused 输入状态的轻量定位角标 |
| message assistant | corner brackets | AI 内容的开放洋红框 |
| message user | cut corners | 右侧偏置的用户卡片 |
| icon button | corner brackets | 无卡片化的小型可点击框 |
| status/list item | cut corners / segmented rail | 次级信息层级 |

## 场景清理

`header_frame` 与 `composer_frame` 九宫格资源会和新的组件 frame 重叠。赛博 `chat.main` 场景将直接放置 header/composer slot；`outer_frame` 仍作为页面级外壳保留。

## 完成条件

- Agent、Classic、Cursor、Bubble 都通过统一 renderer 获得同类组件 frame。
- role name、图标和输入文字继续使用皮肤提供的 content 色。
- 组件尺寸、点击区域和文本滚动不因 path 绘制发生变化。

## 进展

[DONE] `ThemeComponentSurfaceV2` 使用 skin frame 的 Compose shape、draw cache 和预计算 render plan 绘制边框，滚动绘制阶段不再创建路径。

[DONE] 赛博聊天映射已落地：角色栏为分段轨道，composer 为 HUD 缺口，input、AI 消息和图标按钮为开放角括号，用户消息为斜切卡片。

[DONE] Agent 与 Classic 会在超 token 限制时选择 `ERROR` input skin，错误框不再是未消费的声明。
