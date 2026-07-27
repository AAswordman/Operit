# Sherpa-NCNN 中英双语 Zipformer 模型来源与许可证

- 固定模型仓库：`https://huggingface.co/csukuangfj/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13`
- 固定 revision：`05945efc40afe4b572542f01104ca5c413a9f6e1`
- SPDX 许可证声明：`Apache-2.0`
- 模型类型：用于 sherpa-ncnn 的 streaming zipformer 中英双语模型

## 来源证据

固定 revision 的 Hugging Face API 返回：

- 仓库 revision 与请求值一致；
- 标签包含 `license:apache-2.0`；
- `cardData.license` 为 `apache-2.0`。

固定 README：

`https://huggingface.co/csukuangfj/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/raw/05945efc40afe4b572542f01104ca5c413a9f6e1/README.md`

响应头 `x-repo-commit` 为 `05945efc40afe4b572542f01104ca5c413a9f6e1`。README 声明：

- 许可证为 Apache-2.0；
- TorchScript 模型来自 `https://huggingface.co/pfluo/k2fsa-zipformer-chinese-english-mixed`；
- 训练代码来自 Icefall 的 `pruned_transducer_stateless7_streaming` recipe。

被引用的原始模型仓库当前 API 元数据显示 `cardData.license = apache-2.0`，当前 revision 为 `6eb615ae77ecac05c5628d5c8ed7037c14a338d5`。该值用于溯源记录，不等同于证明 2023 年转换时使用的原始 revision。

## 固定文件

权威文件大小和 SHA-256 位于 `app/config/stt-model-assets.properties`，共包括：

- decoder 的 NCNN bin 与 param；
- encoder 的 NCNN bin 与 param；
- joiner 的 NCNN bin 与 param；
- `tokens.txt`。

这些文件在构建期从固定 revision 下载并逐项校验，不从浮动分支获取。

## 尚未闭环的生成链

许可证声明已经明确，但当前证据仍不能证明 NCNN 文件可由完整固定源码确定性重建：

- 固定 README 指向原始 TorchScript 模型仓库，但没有记录转换时使用的原始模型 revision；
- 训练代码链接使用 Icefall `master` 路径，没有固定 commit；
- 仓库含 `export-for-ncnn-bilingual.sh`，但 Operit 尚未记录该脚本的工具链版本、pnnx/ncnn revision 和完整生成命令；
- 训练数据来源、许可及模型权重合规性仍需逐项确认。

因此本模型的状态从“许可证未知”更新为“Apache-2.0 已声明，生成链和训练来源待闭环”。在 F-Droid 审核确认可接受固定上游预训练模型，或上述链条补齐前，F-Droid 变体仍不应默认携带这些文件。

## 再分发要求

如果最终打包，应保留 Apache-2.0 许可证文本、适用的版权与 NOTICE 信息，并在最终 APK 模型来源表中列出固定 revision、每个文件的 SHA-256 和修改/转换说明。