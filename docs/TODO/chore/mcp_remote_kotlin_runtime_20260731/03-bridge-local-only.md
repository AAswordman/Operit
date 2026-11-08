# 03 Bridge Local Only

Node bridge 原本还持有 HTTP Stream 和 SSE transport，并按 remote service 配置启动 helper。Android 端也存在对应的 bridge 远程注册 overload。

本步骤删除 bridge 的远程服务模型、远程 transport 和远程注册入口。bridge 只接受带 command 的本地 stdio 服务，helper 使用官方 TypeScript SDK 的 stdio transport。Android assets 已由 bridge TypeScript 构建重新生成。

预期结果：远程 MCP 的唯一运行实现是 Kotlin SDK；Node bridge 的服务注册只描述本地 stdio 进程。

[DONE]
