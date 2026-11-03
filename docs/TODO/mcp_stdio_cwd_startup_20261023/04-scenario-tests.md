# 04 场景测试与构建验证

bridge 需要一组可重复的 stdio MCP 最小服务，覆盖正常启动、缺少配置、进程异常退出、握手失败和超时。测试不使用外部网络，全部通过本地 TCP 端口直接向 bridge 发送 `register`、`spawn`、`list`、`logs` 请求。

场景定义：

- `ok`：正常 `initialize` 与 `tools/list`，`spawn` 返回 `ready: true` 和工具数量。
- `missing-env`：服务启动即报 `TEST_MCP_TOKEN is required.` 并退出，bridge 返回包含该文本的失败原因。
- `crash`：进程在握手前 `exit 2`，bridge 返回退出信息，不能挂到超时。
- `handshake-fail`：`initialize` 返回 `-32603`，bridge 返回握手错误。
- `handshake-timeout`：`initialize` 不响应，`spawn` 在 `timeoutMs` 后返回超时。
- `no-config`：`spawn` 一个未注册服务且不带 command，返回明确的参数错误。

Android 侧使用 JVM 单元测试覆盖纯 Kotlin 逻辑：

- `McpStdioConfigResolverTest`：命令名、终端路径、应用托管判定、`cwd` 解析。
- `McpConfigImportParserTest`：`cwd` 的读取与空值拒绝。

构建命令与结果：

```text
pnpm --dir tools/mcp_bridge run build            通过，assets 同步并在 normalize-assets.js 中清理 ncc 尾部空格
pnpm --dir tools/mcp_bridge run test:scenarios   6/6 通过（ok / missing-env / crash / handshake-fail / handshake-timeout / no-config）
./gradlew :app:testDebugUnitTest                 160 个测试类全部通过，含 McpStdioConfigResolverTest 15 项、McpConfigImportParserTest 5 项
./gradlew :app:compileDebugKotlin                通过
./gradlew :app:assembleDebug                     未通过，:mnn:configureCMakeDebug 需要宿主 FlatBuffers 编译器，与本次改动无关
```

Linux CI 的 `assembleDebug` 由 `ci/script/download_android_dependencies.sh` 准备依赖后执行；本机 Windows 缺少 MNN schema 生成所需的宿主编译器，因此未能完成该任务。

[DONE]
