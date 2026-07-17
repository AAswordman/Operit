# Codec and Provider boundary

- 新增 DeepSeek 专用、版本化的 `xml-text-v1` codec
- 编码顺序固定为 `&`、`<`、`>`，解码为严格、非递归的单次扫描
- OpenAI Provider 只提供默认无行为变化的 reasoning emission seam
- DeepSeek 流式与非流式输出统一编码，token 统计使用编码前原文
- DeepSeek 历史只对精确版本 marker 解码
- 工具子轮次逐字符保留 reasoning，不 trim、不插入换行
- 原生工具调用保持在已关闭的 think block 外
- `enableToolCall` 只决定工具协议，不改变 reasoning 的结构安全处理

## Storage contract

`streamBuffer`、`PromptTurn`、数据库和工具解析输入始终保持编码态。普通 `content` 不编码，以免改变现有 XML 工具协议。
