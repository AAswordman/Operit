# Presentation boundaries

- Android 静态和流式 think body 在隔离结构后解码
- rollback/replay 从 canonical snapshot 重建流式 decoder 状态
- 消息编辑器 visual 模式解码，重组时重新编码
- Web Chat structured block 输出语义正文并保留 canonical raw content
- TXT 和 HTML 可读导出显示解码后的 reasoning
- 未带精确 marker 的旧历史维持现有解释规则，不按新格式解码

展示层不得对 canonical 编码体做通用 HTML/XML 解码，也不得扩大 marker 匹配范围。
