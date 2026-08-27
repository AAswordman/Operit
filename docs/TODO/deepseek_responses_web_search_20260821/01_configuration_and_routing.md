# 配置兼容与运行时路由

## 兼容契约

- API 端点是协议的唯一配置来源。`/responses` 端点使用 Responses，其余已发布 DeepSeek 端点形式保持 Chat Completions。
- 搜索开关仅在 Responses 端点生效；Chat Completions 不读取该开关。
- Responses 端点复用 `ApiProviderType.DEEPSEEK`，保持模型统计、价格和配置归属不变。
- 设置界面直接保存用户选择的完整端点，不额外保存协议字段，也不在运行时互相改写 Chat 与 Responses 路径。

## 实现单元

- DeepSeek Chat 与 Responses 端点选项
- 集中的 DeepSeek 端点协议判定
- AI service factory 的 Chat/Responses 分流
- 端点准备状态与相关单元测试代码

[DONE]
