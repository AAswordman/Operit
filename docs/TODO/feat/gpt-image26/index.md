---
fork: https://github.com/3316891527/Operit
status: 源码同步完成，远程验证待执行
---

# GPT Image 2.6 动态 GIF

同步 [上游 PR #1476](https://github.com/AAswordman/Operit/pull/1476) 的绘图部分，来源提交为 `c6b65b1adc5c7f04fd807f65f7e1f210f38de846` 和 `1957624dfb0366fd1c79e5445806f9ae6f9a071e`，保留原作者归属。此次范围是 OpenAI 绘图脚本，不含该 PR 的聊天文件夹与市场调整。

## 改动

- GPT Image 请求不再发送会被其拒绝的 response_format 参数
- 文生图及参考图编辑支持 quality、background、output_format、frame_count、frame_duration_ms 和 complexity
- 输出格式支持 png、jpeg、webp 和 gif；JSON 请求保留数值帧参数，multipart 请求使用文本字段
- 根据请求格式保存扩展名，保留 GIF 原始数据，并拒绝将非 GIF 返回值声明为动图
- 同步 TypeScript 源码、JavaScript 示例与内置脚本三个入口

同步采用上游的参数校验：GIF 帧数为 2 到 16 的整数；帧间隔为至少 20 ms 的安全整数；自定义 GIF 尺寸的宽高必须为 16 的正整数倍；透明背景限 png 或 webp。实际模型支持范围及账号权限仍由服务端决定，本轮没有调用真实绘图接口。

## 验证与资源

源码同步后核对三个文件与选定上游版本一致。本地不执行编译、构建或测试；通过远程 PR 检查、Android Tests 和构建工作流验证。预览发行构建会按当前源码准备固定资源目录及白名单扩展，避免新绘图脚本仍指向旧资源版本。资源公开发布与 APK Release 不属于本次构建请求。
