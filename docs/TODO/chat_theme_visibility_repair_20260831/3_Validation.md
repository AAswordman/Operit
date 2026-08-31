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
