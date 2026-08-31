# 验证与设备验收

## 自动验证

- 为 scene scaffold 的真实 composer 尺寸补充 Compose 回归测试。
- 为 skin surface 的 normal/focused 与 message container 补充 JVM/Compose 测试。
- 覆盖 Agent、Classic、Cursor、Bubble 四条聊天视觉路径。

## 设备验收

1. 赛博主题下分别切换 Agent/Classic 输入器，检查空输入、多行输入、附件、发送、语音、设置与模型选择。
2. 发送用户消息并接收流式 AI 回复，检查 Cursor/Bubble 两种样式的标题、正文、代码块、工具输出和长消息滚动。
3. 打开输入法、收起输入法、切换主题、旋转设备并截取整页截图。
4. 验证 TalkBack 焦点、按钮可点击性和文本输入语义未受影响。

## 进展

[DONE] 已新增 `ThemeComponentSurfaceV2AndroidTest`，以像素断言确认 `input.normal` 与 `input.focused` 的 package container 分别参与绘制。

[DONE] 构建服务已完成 `67fe288d` 的 release 编译和签名：`operit-release-feat_plugin-interface-67fe288d.apk`，SHA-256 `9dbba58925951b98c26bcca73377c9f11e3e3e2ca2874b2546e58a3deb04f600`。

[TODO] 本机未安装 `adb`，新增 Android instrumentation 用例与设备矩阵尚未执行；需要在真机导入赛博 V2 包后验证 Agent/Classic、Cursor/Bubble、流式消息、IME、长文本滚动与 TalkBack，并提供整页截图。
