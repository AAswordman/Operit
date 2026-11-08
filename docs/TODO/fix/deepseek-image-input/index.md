---
fork: https://github.com/3316891527/Operit
status: 源码修改完成，运行验证待执行
---

# DeepSeek 图片输入与 read_file 读图

## 问题与目标

DeepSeek 专用 Responses 适配器未处理上传得到的 `file` 块，导致 `file_id` 图片在构造请求时丢失。`read_file` 的 `direct_image` 参数被无条件隐藏。后端识图提示仅将 `intent` 描述为可选分析方式，未明确区分图片分析与本机 OCR。

目标是让开启图片直传的当前模型通过工具取得图片，让使用后端图片识别的模型携带具体分析问题，并保留 DeepSeek Responses 中的图片引用。

## 作用域与实施

- [x] 为 DeepSeek Responses 增加 `file` 到 `input_image + file_id` 的转换，保留已经规范化的 `input_image` 内容
- [x] 按当前功能模型能力暴露 `direct_image`，在中英文提示中说明原生识图、后端 `intent` 分析及本机 OCR 的区别
- [x] 补充用户图片、工具输出图片、规范化图片和内联图片的转换回归代码，以及中英文工具能力组合回归代码
- [x] 更新文件工具文档

保留现有能力测试的短码判定；没有增加 DeepSeek API 层面的 `intent` 字段，分析问题仍以文本与图片一起发送。多 Key 文件引用作用域、音视频直传和 Linux 媒体读取不在本次修改范围。

初期排查沿用现有工作树；后续按维护者授权移入集成分支，通过草稿 PR 和远程工作流验证。

## 验证状态

已按原文核对 Android 图片直传分支，其返回的图片链接正常；工具展示时会处理媒体标签，不能据此判定源码返回空字符串。同步纠正旧文件工具文档的相关描述。

新增 8 项 JVM 回归代码，尚未执行。本轮按既有约束不运行本地编译、构建、测试，也不调用真实模型接口。已通过 git diff --check，并核对两个新增文件没有差异格式错误。最终作用域为 2 个生产源码文件、2 个回归文件和 2 个文档文件。

真机验收需覆盖：图片能力短码测试；无原生识图的聊天模型通过 `intent` 请求 DeepSeek 进行场景分析；支持原生识图的聊天模型通过 `read_file(direct_image=true)` 分析图片；用户图片附件通过 DeepSeek Responses 发送。

官方接口依据：[DeepSeek Vision](https://api-docs.deepseek.com/guides/vision)。Responses 图片使用 `input_image`，文件引用用 `file_id`，与 `image_url` 互斥。
