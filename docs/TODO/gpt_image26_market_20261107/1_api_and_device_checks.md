---
title: GPT Image 2.6 参数与快速验证
status: pending_device_validation
date: 2026-11-07
---

# GPT Image 2.6 参数与快速验证

[返回本轮计划](index.md)

## 调用方式

在 OpenAI 绘图包中配置自己的 API Key 和 API 地址，调用 `openai_draw:draw_image`：

```json
{
  "prompt": "制作一段无缝循环动图：一只小猫缓慢挥手，固定镜头，纯色背景，最后一帧回到起始姿势。",
  "model": "gpt-image-2.6-sunburst",
  "output_format": "gif",
  "size": "auto",
  "frame_count": 9,
  "frame_duration_ms": 200,
  "complexity": "medium",
  "file_name": "cat_wave"
}
```

也可使用 `gpt-image-2.6-flare`，或将模型写入包环境变量 `OPENAI_IMAGE_MODEL`。GIF preview 的账号访问权限由供应商控制；接口拒绝时工具返回原错误。

文生动图走 `/v1/images/generations`。增加 `image_paths` 或 `image_urls` 可基于静态参考图生成动图，走 `/v1/images/edits`，本地和远程参考图均作为 multipart 文件上传。

```json
{
  "prompt": "保留参考图角色和构图，让角色轻轻眨眼，首尾衔接为循环动图。",
  "model": "gpt-image-2.6-flare",
  "output_format": "gif",
  "frame_count": 9,
  "frame_duration_ms": 200,
  "image_paths": ["/sdcard/Download/reference.png"],
  "file_name": "reference_blink"
}
```

`frame_count` 和 `frame_duration_ms` 是可选数字，只用于 GIF；省略时分别由 API 使用 9 和 200。时长实际编码由 API 按 10 ms 取整。指南推荐 20–2000 ms，API schema 声明最小 20 ms，并说明 GIF 编码最大延迟为 655350 ms；工具不自行改变用户传入的合法整数。

`complexity` 控制规划，`quality` 控制渲染；均可省略。`size` 对 GIF 表示单帧尺寸，建议使用 `auto`，自定义宽高必须为正数且为 16 的倍数；总像素预算由服务端判定。

静态图片可以用 `output_format=png/jpeg/webp`；透明背景应配合 png 或 webp。GIF 不传 streaming、partial_images、mask 或 output_compression。

返回结果保留 `file_path`、`file_uri`、`markdown`、`model` 等字段，新增 `output_format`。上例保存为 `cat_wave.gif`，返回可用于对话展示的 Markdown。文件内容为接口返回的原始字节，不解码为单帧或转码为 PNG。

## 待真机快速检查

1. 文生 GIF：使用第一个示例，确认工具成功、后缀为 .gif、结果 output_format 为 gif；对话图片与点击后的全屏预览均连续播放。
2. 参考图动图：使用第二个示例，确认人物特征和构图被参考、文件为可播放 GIF；再用一个公网参考图 URL 检查远程下载上传路径。
3. 静态回归：原模型配置下省略新增参数生成 PNG；显式选择 jpeg、webp 检查扩展名及显示；透明背景使用 png 或 webp。
4. 参数错误：非 GIF 传 frame_count、frame_count=1、frame_duration_ms=0、GIF size=513x512，均应明确失败，不发起图片生成请求。
5. 格式不符：供应商返回的 output_format 与请求不符，或请求 GIF 却返回 PNG 字节，应明确失败，不能把静态图保存成 .gif 后报告成功。
6. 市场：重新进入市场默认不选中精选，展示全部条目；手动打开精选仍可过滤，搜索、排序和分页继续工作。

上述设备和真实供应商接口项目尚未执行，按维护者后续实际验收结果记录。
