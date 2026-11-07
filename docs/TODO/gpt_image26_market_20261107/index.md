---
title: GPT Image 2.6 动图与市场默认筛选
status: implemented_pending_device_validation
fork: https://github.com/luojiaping/Operit
branch: feat/gpt-image-26-market-upstream
date: 2026-11-07
---

# GPT Image 2.6 动图与市场默认筛选

[返回总计划](../README.md)

## 问题与改动范围

当前 OpenAI 绘图请求固定发送 GPT Image 不接受的 response_format，且保存文件固定使用 PNG 后缀。此改动修复参数兼容性，补齐 quality、background 传递，并支持 GPT Image 2.6 的 GIF 生成、参考图动画与对应输出格式。

市场筛选的默认状态同步改为不勾选精选，首次进入显示全部条目，保留用户主动筛选和排序能力。

## 官方接口依据

2026-11-07 查阅 [图片生成指南](https://developers.openai.com/api/docs/guides/image-generation#generate-animated-gif-output)、[生成接口](https://developers.openai.com/api/reference/resources/images/methods/generate)、[编辑接口](https://developers.openai.com/api/reference/python/resources/images/methods/edit) 和模型页面。

- 模型 ID 为 `gpt-image-2.6-sunburst`、`gpt-image-2.6-flare`
- 生成与编辑接口使用 `output_format=gif`，返回完整 GIF 的 `b64_json`
- `frame_count` 为 2–16，接口默认 9 帧；`frame_duration_ms` 以毫秒计，默认 200，编码按 10 ms 取整
- 指南推荐帧时长 20–2000 ms；API schema 只标注最小 20，并说明编码上限 655350 ms。本工具校验整数及最小值，不在本地改写时长
- GIF 尺寸可用 `auto` 或宽高均为 16 倍数的尺寸，最终帧数、尺寸和总像素预算由服务端检查
- GIF 不支持 streaming、partial images、mask 或自定义 output_compression；服务账号需具备对应模型的 GIF preview 访问权限
- `complexity` 为 low、medium、high，与渲染 quality 独立

## 实施与兼容性

1. draw_image 在现有参数之外增加可选的输出格式、帧数、帧时长和 complexity；既有静态图片调用方式继续使用。
2. 生成端点使用 JSON，编辑端点使用 multipart；参数类型遵循各端点契约。
3. 保存原始字节，以请求格式命名，结果新增 output_format 字段，保留既有路径和 Markdown 字段。
4. TypeScript、示例 JS、内置 JS 与中英文参数说明同步；应用使用现有 GIF 解码链路。

## 验证范围

已核对官方请求和返回协议、数字参数在宿主桥接层的转换，以及 JSON/multipart 两条请求路径；完成 Git 差异检查、两份 JS 和包元数据一致性检查。真实供应商接口、账号访问权限、GIF 播放和设备市场交互尚待按检查清单验收。

## 来源

- #1335：`b0a0252cf4b14f0a5a96868111ad3dcb42125065`
- #1279：`de0e22b8c087c981e65512914aa9813dca2fe0a9`

两项均无手工冲突，使用 cherry-pick -x -s 保留来源和签署。

## 实现记录

- draw_image 的中英文元数据与 TypeScript 入口增加 `output_format`、`frame_count`、`frame_duration_ms`、`complexity`
- JSON 生成请求中的帧数和帧时长为数字，multipart 编辑请求按字段要求转为文本；宿主 JsToolManager 按 metadata 的 number 类型转换模型工具参数
- 静态格式支持 png、jpeg、webp，动图使用 gif；返回文件与输出格式一致，增加 `output_format` 结果字段
- 对非 GIF 使用帧参数、非法帧数/时长、非法 complexity、透明背景与格式冲突、GIF 非法尺寸明确报错
- 原始 GIF 字节直接保存；校验 GIF87a/GIF89a 文件头，防止静态图片被错误命名为 GIF；未增加请求重放或替换模型逻辑
- TypeScript、示例 JS 与内置 JS 的元数据同步，两份 JS 文件完全一致
- 应用全局 ImageLoader 已注册 ImageDecoderDecoder/GifDecoder；Markdown 正文和全屏预览使用该加载器，沿用当前显示链路

接口用法与待真机验证项见 [API 与快速检查](1_api_and_device_checks.md)。
