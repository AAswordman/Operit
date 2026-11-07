---
fork: https://github.com/3316891527/Operit
status: 源码修改完成，界面验收待执行
---

# 模型 API 设置端点布局与默认模型

## 调整目标

API 端点卡片按标题、实际请求地址、输入框排列，删除示例端点；填写提示移动到输入框下方。固定端点提供商也显示实际地址，其固定说明放在下方。

删除 Codex 和 Antigravity 的媒体说明；海外网络提醒及 DeepSeek 的媒体说明使用可换行的小字，不再显示独立背景和信息图标。网络警告使用主题错误色，普通说明使用次要文字色。其他页面使用的提示组件保持原样。

## 默认模型

- OpenAI、Responses、Codex 及 OpenAI 通用接口：`gpt-6.1-sol`
- Google、Gemini 通用及 Antigravity：`gemini-3.8-flash-high`
- Anthropic 及 Claude 通用：`claude-opus-4.6`
- Grok：`grok-4.7`
- GLM：`glm-5.3`
- DeepSeek：`deepseek-flash,deepseek-v4-pro`
- Kimi：`kimi-k3`

同步修改全新默认配置的 DeepSeek 模型列表。其他提供商、聚合平台预设、端点专用模型别名和已保存的用户配置不做迁移。

## 作用域与验证

- [x] 调整可编辑和固定 API 端点的信息顺序
- [x] 将指定页面的网络提醒及媒体说明改为小字
- [x] 更新上述系列的提供商默认值和全新配置默认值
- [x] 清理不再使用的各语言示例端点文案

Gemini 通用的模型列表请求问题另行排查，本次不调整接口路径或请求逻辑。按照当前工作约定，不执行本地编译、构建、测试或联网请求。源码差异已审阅，git diff --check 通过，9 个资源文件的 XML 解析通过；已核对 14 个指定提供商的新默认值、其余提供商预设及上一项 DeepSeek 修改。界面实际展示尚待安装后验收。
