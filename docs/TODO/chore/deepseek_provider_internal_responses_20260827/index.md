---
fork: local
---

# DeepSeek Provider 内部 Responses 路由

## 原状

PR #1015 将 DeepSeek Responses 的端点判断、服务端搜索记录和工具状态展示扩散到了通用聊天链路、UI 渲染层和 Web Chat。协议细节被抬到了 `AIServiceFactory`，导致 DeepSeek 的私有能力影响了其他 provider 的公共路径。

## 意图

- 将 DeepSeek Chat Completions 与 Responses 的选择收敛到 `DeepseekProvider`
- 保持 `AIServiceFactory` 的 DeepSeek 分支只创建 DeepSeek provider
- 只在 DeepSeek Responses 请求体内声明 `web_search` 工具
- 移除 PR #1015 增加的通用层服务端工具事件、隐藏搜索记录和相关测试文档

## 作用域

- `DeepseekProvider` 内部路由与 Responses 子类
- DeepSeek Responses `web_search_call` 到 `<search>` 块的显示转换
- Responses 官方 `web_search_call` output item 的协议标记保存与下一轮 `input` 原样恢复
- DeepSeek 历史中的 Responses 协议标记保留到 provider 内部，由端点协议决定消费或移除
- Responses reasoning 与正文按 output item 边界输出，禁止在答案结束后追加思考块
- DeepSeek/Codex 搜索展示继续保存结构化 `<query>` 与 `<source>` 子节点；同一轮多个 `web_search_call` 聚合为一个展示块，Android 与 Web 优先渲染完整来源列表的网站图标和站点名，服务端只返回查询词时直接显示查询词，并跟随相邻思考与工具调用进入同一折叠组
- DeepSeek 网络搜索配置项和设置页开关
- Codex OAuth Responses 的服务端搜索声明和 `<search>` 块显示
- PR #1015 对公共聊天、渲染、Web Chat 与文档测试的扩散改动清理

[DONE]
