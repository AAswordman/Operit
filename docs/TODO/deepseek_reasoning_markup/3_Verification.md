# Compatibility and verification

## Codec

- 覆盖 `&`、`<`、`>`、未知实体和非递归单次解码
- 覆盖任意 reasoning SSE chunk 切分，包括标签与实体被逐字符拆分
- 覆盖纯空白、换行、制表符和 Unicode 的逐字符 round-trip
- 验证旧无 marker 历史解释规则不变

## Provider

- 覆盖 DeepSeek 流式和非流式 `reasoning_content`
- 覆盖 `enableToolCall` 开启和关闭两种状态
- 验证编码输出不改变 token 统计所使用的原始 reasoning
- 验证工具子轮次恢复出的 `reasoning_content` 与原文逐字符一致
- 验证其他 OpenAI-compatible Provider 的 reasoning 和工具顺序不变

## Tool isolation

- 验证 reasoning 内字面量 `<tool>`、`</think>` 不进入工具执行器
- 验证 Provider 生成的真实原生工具调用只执行一次
- 验证真实工具调用位于已关闭的 canonical think block 外

## Presentation and build

- 覆盖静态 UI、流式 UI、rollback/replay 和消息编辑器 round-trip
- 检查 Web Chat、TXT 和 HTML 导出的语义显示
- 通过 fork 的 GitHub Action 运行专项回归测试并构建 debug APK

完成全部验证并记录结果后再在文档末尾添加 `[DONE]`。
