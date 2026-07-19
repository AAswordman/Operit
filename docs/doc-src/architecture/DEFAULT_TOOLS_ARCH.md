# 默认工具架构（Default Tools）与参数变更清单

本文档用于说明本项目“默认工具（default tools）”的整体架构，以及当你要**修改某个工具的参数/签名**时，需要同步修改哪些文件与位置。

目标：避免只改了执行逻辑但忘记改 schema / JS 封装 / examples / docs，导致运行时工具调用失败或脚本侧类型不一致。

---

## 1. 默认工具的组成（从上到下的数据流）

默认工具链路大致如下：

1. **工具 Prompt / Schema**（工具对 LLM 的“说明书”）
2. **工具注册**（把 toolName -> executor 绑定起来）
3. **工具执行实现**（Kotlin 侧真正做事的逻辑）
4. **脚本侧封装（JS Tools）**（给 JS/TS 脚本更好用的 API）
5. **示例与类型定义**（examples/types 与 examples/**）
6. **文档**（docs/doc-src/package-dev 等）
7. **打包资源 / 产物**（app/src/main/assets/packages/*.js 等）

其中 (1)(4)(5)(6) 是“对外契约”，(2)(3) 是“实现”。

---

## 2. 改参数时必须改哪些文件（Checklist）

下面按“必改/常见遗漏/可选校验”分组。

### 2.1 必改：工具 Schema / Prompt

- **文件**：`app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt`
- **你要做的事**：
  - 修改对应工具的 `parametersStructured`（新增/删除/改名/required）
  - 更新 `description` / `details`（尤其是规则、示例、参数解释）
  - 如果有中英文两份描述，务必两处都同步

为什么必须改：
- 这是 LLM 生成 tool call 的依据，不改会导致 LLM 继续按旧参数调用。


### 2.2 必改：工具注册（toolName -> executor）

- **文件**：`app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
- **你要做的事**：
  - 确认工具名不变时，一般无需改注册，但要确认调用到的 executor 没变
  - 若工具名/分组变更，需要同步调整注册项

为什么必须看：
- 改了工具名或拆分工具时，如果注册没同步，会出现“工具不存在/无法执行”。


### 2.3 必改：Kotlin 执行实现（参数读取与校验）

- **常见目录**：
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/*`
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/debugger/*`
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/admin/*`
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/root/*`
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/accessbility/*`
  - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/ToolGetter.kt`（按权限级别选择具体实现）
- **你要做的事**：
  - 将旧参数的读取逻辑替换为新参数（`tool.parameters.find { it.name == "..." }`）
  - 如果某个工具在 `debugger/root/admin/accessibility` 目录下有 override / 替代实现：这些实现里同样要同步更新参数读取与校验
  - 新增参数合法性校验：必填、互斥、默认值、兼容性策略
  - 更新错误消息（要能引导正确用法）

为什么必须改：
- 不改这里就算 schema 改了，执行层也拿不到参数或行为不对。


### 2.4 必改：JS 侧工具封装（Tools.*）

- **文件**：`app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
- **你要做的事**：
  - 更新对应的 JS wrapper 函数签名
  - 更新 wrapper 内部构造的 `params` 对象字段名
  - 注意 `undefined/null` 的处理（JS 传参常见问题）

为什么必须改：
- 许多脚本调用的是 `Tools.Files.xxx` 而不是直接 toolCall。


### 2.5 必改：TypeScript 类型定义（对脚本作者的契约）

- **文件**：`examples/types/*.d.ts`（尤其是 `examples/types/files.d.ts`、`examples/types/chat.d.ts`、`examples/types/core.d.ts`、`examples/types/system.d.ts` 等）
- **你要做的事**：
  - 更新函数签名与参数类型
  - 如果新增了枚举/联合类型（如 `"replace" | "delete" | "create"`），补充 type 定义
  - 确认返回类型与字段名仍然正确

为什么必须改：
- 这是脚本作者写 TS 时的类型提示来源。


### 2.6 必改：示例代码（TS/JS）

- **目录**：`examples/**`
- **你要做的事**：
  - 更新示例里对 `Tools.*` 的调用参数（通常优先改 `*.ts` 源码）
  - 如果仓库里存在 `*.ts -> *.js` 的编译产物：一般只需要改 TS 并重新构建产物，不建议手动修改 `*.js`
  - 若项目存在“编译后的 JS 产物/打包后的单文件”，需要确保重新构建后产物也被更新（见下一节）

为什么必须改：
- 示例是实际用法，会直接误导使用者；同时也可能被打包进入 app。

补充说明（advice-only 工具）：
- 若某个工具仅用于说明/提示（如 `usage_advice`），在 examples 的 metadata 里加入 `advice: true`。
- 标记为 `advice: true` 的工具不要求在运行时存在真实实现，可跳过“工具不存在”的校验。


### 2.7 必改：打包资源 / 产物文件

- **常见位置**：
  - `app/src/main/assets/packages/*.js`
  - `examples/*.js`（可能是构建产物或分发用 bundle）

你要做的事：
- 如果这些文件是由构建脚本生成：应该**重新构建**生成它们（优先）
- 如果当前仓库是直接提交产物：就需要手动同步修改这些产物中的调用签名

补充说明（packages 同步约定）：

- `examples/*.ts` 通常作为脚本包的**源代码**
- `examples/*.js` 作为**编译产物/分发产物**（不建议手改）
- `app/src/main/assets/packages/*.js` 作为 App 运行时加载的包文件
- 仓库提供 `tools/sync_example_packages.py`：会按 `tools/packages_whitelist.txt` 将 `examples/*.js` 复制到 `app/src/main/assets/packages/*.js`

同步执行约定（重要）：

- 做包同步时，只需要执行这一条命令：`python tools/sync_example_packages.py`
- 不要额外手动复制 `examples/*.js` 到 `assets/packages/`，避免源/产物不一致

因此：

- 修改脚本包功能时，优先改 `examples/<package>.ts`，然后通过构建/编译生成对应的 `examples/<package>.js`
- 最后再用 `tools/sync_example_packages.py` 同步到 `assets/packages/`
- **不建议直接手动修改** `examples/*.js` 或 `assets/packages/*.js`（避免被后续构建覆盖或造成源/产物不一致）

为什么必须改：
- App 实际运行时可能直接加载 assets 里的 JS 包；你只改了 TS 不改 assets，会导致运行仍调用旧参数。


### 2.8 必改：文档

- **常见位置**：
  - `docs/doc-src/package-dev/*.md`
  - `docs/doc-src/**/*.md`
- **你要做的事**：
  - 更新 API 描述、参数说明、示例
  - 更新“关键规则/注意事项”

为什么必须改：
- 文档是对外说明；很多问题其实来自文档与实现不一致。

---

## 3. 强烈建议：全局搜索旧参数名（找遗漏）

当你把某个参数 `content` 改为 `old/new/type` 时，建议做这些搜索（按需调整关键词）：

- 搜 `"apply_file"`（工具名）
- 搜旧参数名 `"content"`（确认没有遗留在 toolCall、schema、示例、assets）
- 搜新参数名 `"old"` / `"new"` / `"type"`（确认使用位置完整）

注意：如果你在 Kotlin/TS/JS 三端都封装了同一套工具接口，任何一端遗留都会导致不一致。

---

## 4. 编译/运行级别的自检（避免提交后才爆）

### 4.1 Kotlin 编译自检（推荐）

- 运行 `:app:compileDebugKotlin`（或等价任务）
- 目的：捕捉 `JsTools.kt`、`ToolRegistration.kt`、`Standard*Tools.kt` 的签名/引用错误

### 4.2 示例/脚本侧检查（按需）

- 如果 examples 有 TypeScript 构建流程：跑一次构建（例如 `npm run build` 或仓库内的 build 脚本）
- 如果 assets 由构建生成：重新打包生成 assets

---

## 5. 常见坑位总结

- **只改了执行层没改 prompt**：LLM 仍按旧参数调用
- **只改了 prompt 没改 JS wrapper**：脚本仍按旧参数组装 `params`
- **只改了 TS，忘了 assets 里的 bundle**：App 运行仍加载旧 bundle
- **参数名改动但错误提示没更新**：用户不知道正确用法，反复试错

---

## 6. 扩展：当你新增一个工具时（简版）

新增工具时通常需要：

- `SystemToolPrompts.kt`：新增 ToolPrompt
- `ToolRegistration.kt`：注册执行器
- `Standard*Tools.kt`：实现逻辑
- `JsTools.kt`：暴露给脚本侧（如需要）
- `examples/types/*.d.ts`：类型定义（如需要，`Tools.System.*` 记得同步 `examples/types/system.d.ts`）
- `docs/doc-src/`：补文档
