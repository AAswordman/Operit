# Frame 契约

## 旧实现

`ThemeComponentStateSkinV2` 以 `cornerRadiusDp`、`outlineToken` 和 `outlineWidthDp` 表达全部组件边框。运行时将其转换为 `RoundedCornerShape` 和 `BorderStroke`，无法描述缺口、开放角括号或分段轨道。

## 新实现

1. 定义带 discriminator 的 `ThemeComponentFrameSpecV2`：`round_rect`、`cut_corners`、`hud_notched`、`corner_brackets` 与 `segmented_rail`。
2. 让每个 state skin 强制声明一种 frame，而非继续使用扁平圆角/outline 属性。
3. 解析 token 引用为运行时 frame，保持 container、content、elevation 与内容内边距的职责不变。
4. 为无效尺寸、缺失 token 和不符合 style 约束的 manifest 提供严格校验。

## 完成条件

- 两个主题 manifest 的每个 skin state 都显式声明 frame。
- V2 解析器不再包含旧边框字段。
- 单元测试覆盖每种 frame 的序列化和校验。

## 进展

[DONE] `ThemeComponentFrameSpecV2` 已定义 `none`、`round_rect`、`cut_corners`、`hud_notched`、`corner_brackets` 与 `segmented_rail`，并让 state skin 强制声明 frame。

[DONE] linker 与 archive validator 现在验证 frame stroke 的颜色 token；旧组件 outline 字段已从 V2 runtime 与 renderer 移除。

[DONE] `ThemeComponentFrameSpecV2Test` 覆盖 HUD frame JSON 解码、token 收集和非法缺口宽度。
