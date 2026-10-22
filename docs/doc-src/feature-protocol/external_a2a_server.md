# External A2A Server

Operit can expose an A2A 1.0 server through the existing external HTTP service. It uses the service's configured port and Bearer Token. The existing REST API, Web Chat, and Intent entry points remain separate.

For service setup, port selection, and access-token management, see [External HTTP Chat API](./external_http_chat.md).

## Discovery

Enable the external HTTP service in the app settings, then provide an A2A client with this Agent Card URL:

```text
http://DEVICE_IP:8094/.well-known/agent-card.json
```

The Agent Card is available without a Bearer Token so that an A2A client can discover the interface. It declares the JSON-RPC endpoint at `/a2a`, A2A protocol version `1.0`, text input and output, streaming, and HTTP Bearer authentication.

```bash
curl "http://DEVICE_IP:8094/.well-known/agent-card.json"
```

## Authentication

Every request to `/a2a` requires the same token as the external HTTP Chat API:

```http
Authorization: Bearer YOUR_TOKEN
```

The server accepts an `A2A-Version: 1.0` request header and returns the same header on A2A responses. A version other than `1.0` receives the A2A JSON-RPC `VersionNotSupportedError` code `-32009`.

## JSON-RPC Endpoint

Send JSON-RPC 2.0 requests to:

```text
POST http://DEVICE_IP:8094/a2a
```

The server supports these A2A 1.0 JSON-RPC methods:

- `SendMessage`
- `SendStreamingMessage`
- `GetTask`
- `ListTasks`
- `CancelTask`
- `SubscribeToTask`

Text input uses a Message with `ROLE_USER` and one or more text Parts. File and data Parts are not supported. A Part's kind follows the `text`, `raw`, `url`, or `data` member it sets, so `mediaType`, `filename`, and `metadata` are accepted next to `text` and do not change it into another kind.

## Send A Message

`SendMessage` waits for a terminal task state by default. Set `configuration.returnImmediately` to `true` when the caller intends to poll with `GetTask`.

```bash
curl -X POST "http://DEVICE_IP:8094/a2a" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "A2A-Version: 1.0" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "message-001",
        "role": "ROLE_USER",
        "parts": [{"text": "请总结今天的待办"}]
      },
      "configuration": {
        "returnImmediately": true,
        "acceptedOutputModes": ["text/plain"]
      }
    }
  }'
```

The response wraps the Task in the standard JSON-RPC result:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "task": {
      "id": "task-uuid",
      "contextId": "context-uuid",
      "status": {
        "state": "TASK_STATE_WORKING"
      }
    }
  }
}
```

An A2A context maps to an isolated Operit chat. Send a new message with the returned `contextId` to continue that chat in a new task. Operit does not implement `TASK_STATE_INPUT_REQUIRED`, so a Message containing an existing `taskId` is rejected.

## Stream A Task

`SendStreamingMessage` creates a task and returns `text/event-stream`. Every `data` value is a JSON-RPC 2.0 response envelope containing exactly one Stream Response member.

```bash
curl -N -X POST "http://DEVICE_IP:8094/a2a" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "A2A-Version: 1.0" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "SendStreamingMessage",
    "params": {
      "message": {
        "messageId": "message-002",
        "role": "ROLE_USER",
        "parts": [{"text": "解释一下这个方案"}]
      }
    }
  }'
```

The first event contains `result.task`. Subsequent events contain `result.statusUpdate` or `result.artifactUpdate`. The final `statusUpdate` carries `final: true`, then the SSE connection closes.

```text
data: {"jsonrpc":"2.0","id":2,"result":{"task":{"id":"task-uuid","contextId":"context-uuid","status":{"state":"TASK_STATE_WORKING"}}}}

data: {"jsonrpc":"2.0","id":2,"result":{"artifactUpdate":{"taskId":"task-uuid","contextId":"context-uuid","artifact":{"artifactId":"task-uuid-result","parts":[{"text":"这是回答的第一部分。"}]},"append":true,"lastChunk":false}}}

data: {"jsonrpc":"2.0","id":2,"result":{"statusUpdate":{"taskId":"task-uuid","contextId":"context-uuid","status":{"state":"TASK_STATE_COMPLETED"},"final":true}}}
```

## Read, List, Cancel, And Subscribe

`GetTask`, `CancelTask`, and `SubscribeToTask` take the task ID in `params.id`. `ListTasks` accepts `contextId`, `status`, `pageSize`, and `pageToken`.

```json
{"jsonrpc":"2.0","id":3,"method":"GetTask","params":{"id":"task-uuid"}}
```

```json
{"jsonrpc":"2.0","id":4,"method":"CancelTask","params":{"id":"task-uuid"}}
```

```json
{"jsonrpc":"2.0","id":5,"method":"SubscribeToTask","params":{"id":"task-uuid"}}
```

`SubscribeToTask` returns the same SSE format as `SendStreamingMessage`. It is available while a task is active. `CancelTask` returns the canceled Task and changes the task to `TASK_STATE_CANCELED`.

Tasks are held in memory for the lifetime of the external HTTP service. Restarting or disabling that service removes its A2A task records.

## Capability Limits

- Input and output mode: `text/plain`
- Push notifications: not supported
- A2A files and structured data Parts: not supported
- A2A task history: not returned
- A2A task state persistence across service restarts: not supported
