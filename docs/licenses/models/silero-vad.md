# Silero VAD 模型来源与许可证

- APK 路径：`models/silero_vad.onnx`
- 上游项目：`https://github.com/snakers4/silero-vad`
- 固定镜像仓库：`https://huggingface.co/safestack/silero-vad`
- 固定 revision：`8a63e2e86cf654d7ba19fbedbccce5ff55de3c60`
- 固定文件：`data/silero_vad_16k_op15.onnx`
- 文件大小：`1289603` 字节
- SHA-256：`7ed98ddbad84ccac4cd0aeb3099049280713df825c610a8ed34543318f1b2c49`
- SPDX 许可证：`MIT`
- 作者与版权声明：`Copyright (c) 2020-present Silero Team`

## 来源证据

固定 revision 的 Hugging Face API 返回：

- 仓库 revision 与请求值一致；
- `cardData.license` 为 `mit`；
- 仓库包含 `LICENSE`；
- README 声明该仓库是官方 Silero VAD 预训练模型文件的镜像，模型来自 Silero Team。

许可证原文 URL：

`https://huggingface.co/safestack/silero-vad/raw/8a63e2e86cf654d7ba19fbedbccce5ff55de3c60/LICENSE`

响应头 `x-repo-commit` 为 `8a63e2e86cf654d7ba19fbedbccce5ff55de3c60`，许可证正文为标准 MIT License。

## 再分发要求

MIT 要求在软件副本或实质性部分中保留版权声明和许可声明。F-Droid APK 如果携带该模型，应同时携带或在应用的第三方许可页面展示 MIT 声明；源码仓库也应保留本记录。

## 构建与打包

模型不是在 Operit 构建中训练或转换的，而是按 `app/config/stt-model-assets.properties` 中的固定 URL、大小和 SHA-256 下载并校验，然后复制到生成的 app assets。

本记录只证明固定文件的来源和再分发许可，不声称模型训练过程可由 Operit 仓库复现。