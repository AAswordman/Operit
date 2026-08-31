# 主题包与验证

## 主题源

默认主题在新 contract 中为所有组件写入 `round_rect` frame。赛博主题为聊天组件写入各自的异形 frame，并删除不再由场景引用的 `header_frame`、`composer_frame` 声明和资源。

主题源的 release artifact、主应用内置归档、锁定坐标与 README 必须在同一批次更新。

## 自动验证

1. manifest/linker 测试覆盖所有 frame 类型和 token 引用。
2. Compose 像素测试断言切角透明区、缺口位置、角括号和分段轨道颜色。
3. 既有 surface 内容色测试继续覆盖 normal/focused 状态。

## 设备验收

1. 在赛博主题下检查 Agent 与 Classic composer 的 HUD 框和 focused 输入角标。
2. 发送用户消息并接收 AI 消息，检查 Cursor 与 Bubble 的斜切/开放角框、长文本和代码块。
3. 检查角色栏、历史、悬浮按钮及 IME 打开后的 frame 对齐。

## 执行约束

本次先完成源代码、主题源和测试用例。编译、主题归档打包与 APK 构建仅在用户明确要求后执行。

## 进展

[DONE] 默认主题已为 24 个 skin state 写入显式 `round_rect` frame；赛博主题已为相同覆盖面写入异形 frame。

[DONE] 赛博 `chat.main` 已删除 header/composer 九宫格层，仅保留页面级 `outer_frame`，避免与组件 frame 重复描边。

[DONE] 两个主题打包脚本现在校验每个 frame 类型的必填几何和 stroke 字段。JSON 解析和 shell 语法检查已通过。

[DONE] `ThemeComponentSurfaceV2AndroidTest` 新增 HUD 缺口和主/强调角括号的像素断言。

[DONE] 默认主题已打包为 `operit-default-2.1.0.otheme`，SHA-256 为 `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`；APK 内置归档、锁定坐标与 Cyber Grid basis 均使用该摘要。

[DONE] 赛博主题已打包为 `operit-cyber-grid-2.1.0.otheme`，SHA-256 为 `e60316ce282ffd7b035645217647ad28a67b9a575ad975841e5b59d6a17b0b1e`。两个归档均通过 ZIP 完整性与 comment 检查。

[DONE] 三个工作树已提交并推送：默认主题 `6a1bddd`、赛博主题 `9a2ff52`、主应用 `ad0cb6bf`。

[DONE] 构建服务已同步 `ad0cb6bf` 并完成 release 编译和签名：`operit-release-feat_plugin-interface-ad0cb6bf.apk`，SHA-256 `6913f01dd8cb1d5ab162f1ff9b2cceef1be4827b9e791dd9b33f6513c8d9390a`。

[TODO] 在真机导入 `operit-cyber-grid-2.1.0.otheme` 后，提供 Agent/Classic、Cursor/Bubble、角色栏、focused/error input 的整页截图，确认异形 frame 在真实设备尺寸和 IME 状态下对齐。
