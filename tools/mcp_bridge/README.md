# MCP TCP 桥接器

这个工具只负责基于 STDIO 的本地 MCP 服务器与 TCP 客户端之间的桥接，让无法直接访问命名管道(FIFO)的客户端，例如 Android 应用，能够与本地进程通信。

远程 HTTP Stream 和 SSE MCP 服务由 Android 端的 Kotlin MCP SDK 直接连接，不经过 bridge。

## 功能特点

- 将STDIO型MCP服务器桥接到TCP端口
- 支持并发连接
- 自动重启崩溃的MCP进程
- 优雅地处理连接错误和进程终止
- 详细的日志记录

## 安装

```bash
# 在项目根目录安装工作区依赖
pnpm install

# 编译 bridge 并同步 Android assets
pnpm --dir tools/mcp_bridge run build
```

`tools/mcp_bridge` 的依赖由根目录 `pnpm-lock.yaml` 统一锁定。构建会更新
`app/src/main/assets/bridge/index.js` 和 `app/src/main/assets/bridge/spawn-helper.js`。

## 测试

```bash
# 先构建，场景测试使用 dist/ 下的编译结果
pnpm --dir tools/mcp_bridge run build
pnpm --dir tools/mcp_bridge run test:scenarios
```

`test/scenarios.js` 会为每个场景启动一个独立端口的 bridge，覆盖正常启动、缺少 env、进程异常退出、握手失败、握手超时和未注册服务。测试服务位于 `test/fixtures/stdio-mcp-server.js`。

## 使用方法

### 基本用法

```bash
# 使用默认配置启动
pnpm --dir tools/mcp_bridge start
```

这将在默认端口8752上启动桥接器，连接到`../your-mcp-server.js`。

### 高级用法

您可以通过命令行参数自定义桥接器：

```bash
# 格式
pnpm --dir tools/mcp_bridge start -- [端口] [命令] [参数...]

# 示例：在端口9000上启动，连接到Python MCP服务器
pnpm --dir tools/mcp_bridge start -- 9000 python ../python_mcp_server.py

# 示例：连接到Java MCP服务器
pnpm --dir tools/mcp_bridge start -- 8752 java -jar ../java_mcp_server.jar
```

### 在代码中使用

您也可以在其他Node.js应用中使用这个桥接器：

```typescript
import McpBridge from './path/to/bridge';

const bridge = new McpBridge({
  port: 8752,
  host: '127.0.0.1',
  mcpCommand: 'node',
  mcpArgs: ['../your-mcp-server.js'],
  verbose: true
});

bridge.start();
```

## 在Termux中使用

在Termux环境中，可以按以下步骤设置：

```bash
# 安装Node.js
pkg install nodejs

# 克隆或下载此项目
git clone <项目URL>
cd <项目目录>

# 安装根工作区依赖
pnpm install

# 编译并同步 Android assets
pnpm --dir tools/mcp_bridge run build

# 启动桥接器
pnpm --dir tools/mcp_bridge start -- 8752 node ../your-mcp-server.js
```

## Android客户端示例

以下是Android客户端连接到MCP桥接器的示例代码：

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import org.json.JSONObject

class McpClient {
    private val host = "127.0.0.1"
    private val port = 8752
    
    suspend fun sendRequest(method: String, params: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        Socket(host, port).use { socket ->
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = PrintWriter(socket.getOutputStream(), true)
            
            // 创建JSONRPC请求
            val requestId = System.currentTimeMillis().toString()
            val request = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("method", method)
                put("params", params)
            }
            
            // 发送请求
            output.println(request.toString())
            
            // 读取响应
            val response = input.readLine()
            JSONObject(response)
        }
    }
}
```

## 注意事项

- 确保MCP服务器程序已正确安装并可执行
- 桥接器应当与内部网络一起使用，不要暴露到公共互联网
- 对于生产环境，考虑添加认证和加密层
- 在Android环境中，确保应用有INTERNET权限
