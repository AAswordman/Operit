# 双 Session 聊天 Runtime 上移到前台服务方案

## 计划清单

- [x] 建立前台服务 Runtime 宿主，负责持有聊天协程作用域和 Core 槽位
- [x] 新增 `ChatRuntimeSlot`，固定区分 `MAIN` 和 `FLOATING`
- [x] 在 Runtime 宿主中创建两份 `ChatServiceCore`
- [x] 让 `MAIN` Core 跟随全局当前 chat
- [x] 让 `FLOATING` Core 维护本地当前 chat，不再被全局切换覆盖
- [x] 将 `ChatViewModel` 改为只连接 `MAIN` Core，不再自己装配 delegate
- [x] 将 `FloatingChatService` 改为只连接 `FLOATING` Core，不再自己创建 `ChatServiceCore`
- [x] 清理 `FloatingWindowDelegate` 中主界面与悬浮窗之间的消息同步 / reload 同步逻辑
- [x] 移除 `MessageProcessingDelegate` 的 companion 共享 loading 状态，改为实例内状态
- [x] 删除悬浮窗外部推送消息入口，确保主界面和悬浮窗各自只读自己的 Core
- [ ] 验证主聊天和悬浮窗可同时停在不同 chat，且后台时聊天执行不依赖 `ViewModel`

## 已确认前提

- 当前版本未发布。
- 本次按方案更换处理，不保留旧方案文档。
- 不做向前兼容，不保留回退代码，不保留双实现长期并存。
- 用户明确需要两个大 Session：
  - `MAIN`：主聊天页面使用
  - `FLOATING`：悬浮窗使用
- 目标不是“主聊天和悬浮窗共用一个 Core”，而是“共用一个 Runtime 宿主，但宿主里持有两个 Core”。

---

## 一、这次真正要解决的问题

当前主聊天和悬浮窗聊天虽然复用了部分底层 delegate，但运行时入口仍然是两套：

- 主界面在 `ChatViewModel` 里直接组装聊天逻辑。
- 悬浮窗在 `FloatingChatService` 里再组装一套 `ChatServiceCore`。

这会带来三个直接问题：

1. 聊天执行链路仍然强依赖 `ViewModel` 生命周期。
2. 主界面和悬浮窗之间靠额外同步消息、刷新状态来“看起来一致”，结构反而更绕。
3. 如果用户想让主聊天和悬浮窗同时操作不同会话，当前结构不干净，也不稳定。

本次要做的事情不是把两边揉成一个 Core，而是：

- 把聊天运行时从 `ChatViewModel` 里拿出来。
- 让它跑在前台服务生命周期里。
- 由一个 Runtime 宿主统一管理两个 `ChatServiceCore` 实例。
- 主界面固定使用 `MAIN` Core。
- 悬浮窗固定使用 `FLOATING` Core。
- 删除主界面和悬浮窗之间那套手工消息同步逻辑。

一句话概括：

> 一个 Runtime，两个 Core，分别服务主聊天和悬浮窗。

---

## 二、目标

目标版本只追求以下结果：

- 聊天核心逻辑不再由 `ChatViewModel` 持有。
- 聊天核心逻辑改由前台服务持有。
- 前台服务内部持有两个 `ChatServiceCore` 实例。
- `ChatViewModel` 只接 `MAIN` Core。
- `FloatingChatService` 只接 `FLOATING` Core。
- 主聊天和悬浮窗可以同时处于不同 chat。
- 删除主界面 -> 悬浮窗的消息推送同步、reload 同步、双数据源逻辑。

---

## 三、明确不做的事

本次不做：

- 不把主聊天和悬浮窗合并成一个共享 Core
- 不把 `ChatServiceCore` 重命名成 `ChatSessionContext`
- 不先做大而全的 `ChatSessionHost` / `SessionUiBridge` / `ChatSessionContext` 体系
- 不为了“以后可能有用”提前引入一整套复杂宿主结构

本次只接受一个很薄的宿主层，职责仅限于：

- 持有两个 Core
- 提供按槽位获取 Core 的入口
- 负责生命周期

如果这个宿主开始承载太多 UI 逻辑，就说明又走回旧文档那种过重方案了。

---

## 四、推荐结构

## 1. 一个 Runtime 宿主

建议由现有 `AIForegroundService` 持有，或者由它持有一个很薄的 runtime holder。

建议最小职责：

- 持有两个 `ChatServiceCore`
- 持有聊天相关协程作用域
- 提供 `getCore(slot)` 入口
- 负责在服务生命周期内创建和释放 runtime

建议最小标识：

```kotlin
enum class ChatRuntimeSlot {
    MAIN,
    FLOATING
}
```

说明：

- 这里的 `slot` 只是 runtime 槽位标识。
- 它不是旧方案里那种大而全的状态域模型。
- 只用于告诉宿主“我要主聊天那份 Core，还是悬浮窗那份 Core”。

## 2. 两个 `ChatServiceCore`

保留 `ChatServiceCore` 名字，不改名、不迁目录。

Runtime 宿主内部持有：

- `mainCore`
- `floatingCore`

或者：

- `Map<ChatRuntimeSlot, ChatServiceCore>`

这两个 Core 必须各自独立持有：

- `ChatHistoryDelegate`
- `MessageProcessingDelegate`
- `MessageCoordinationDelegate`
- `AttachmentDelegate`
- `TokenStatisticsDelegate`

也就是说：

- 共享的是“宿主和生命周期”
- 不共享的是“会话状态和运行时实例”

## 3. 选择策略要分开

如果要让主聊天和悬浮窗真的能同时停在不同 chat，上层必须允许两个 Core 拥有不同的当前 chat 选择。

最小要求：

- `MAIN` Core 跟随全局当前 chat
- `FLOATING` Core 只维护自己的本地当前 chat

这一点不需要先搞一套大命名体系，但逻辑上必须成立。

可以接受的最小实现方式有两种：

1. 给 `ChatHistoryDelegate` 加一个简单配置，例如 `followGlobalCurrentChat: Boolean`
2. 或者加一个很小的枚举，例如：

```kotlin
enum class ChatSelectionMode {
    FOLLOW_GLOBAL,
    LOCAL_ONLY
}
```

无论选哪种，目标都一样：

- `MAIN` 使用全局当前 chat
- `FLOATING` 不被全局切 chat 覆盖

---

## 五、各层职责

## 1. `ChatViewModel`

保留职责：

- 页面 UI 状态
- 权限请求
- WebView / Workspace / 选择器 / 朗读等强 UI 行为
- 将 UI 操作转发给 `MAIN` Core

移除职责：

- 直接 new `ChatHistoryDelegate`
- 直接 new `MessageProcessingDelegate`
- 直接 new `MessageCoordinationDelegate`
- 直接充当聊天逻辑宿主

## 2. `FloatingChatService`

保留职责：

- 悬浮窗生命周期
- WindowManager
- 前台通知
- wake lock
- 输入焦点和模式切换
- 将悬浮窗 UI 操作转发给 `FLOATING` Core

移除职责：

- 自己 new `ChatServiceCore`
- 自己维护一套独立消息源

## 3. `FloatingWindowDelegate`

保留职责：

- 启动服务
- 绑定服务
- 关闭服务
- 模式切换

移除职责：

- 主界面消息推给悬浮窗
- 悬浮窗通知主界面 reload
- binder 双向同步消息列表

---

## 六、需要额外注意的两个点

## 1. `MessageProcessingDelegate` 不能继续用 companion 共享状态

如果 Runtime 里真的同时存在两个 Core，那么下面这些共享态就不应该继续跨实例混在一起：

- `sharedIsLoading`
- `sharedActiveStreamingChatIds`

否则会出现：

- 主聊天 loading 影响悬浮窗
- 悬浮窗 loading 影响主聊天

因此这部分要改成实例内状态。

如果以后确实需要全局聚合 loading，再由 Runtime 宿主显式计算。

## 2. `ChatHistoryDelegate` 不能继续默认全跟全局

如果 `FLOATING` 还持续订阅全局 `currentChatId`，那它就不是真正独立。

所以这次虽然不做大重构，但至少要补上：

- `MAIN` 跟全局
- `FLOATING` 本地保持

否则“两个 Core”只是表面上有两个实例，实际上 chat 选择还是串线。

---

## 七、实施方案

## Phase 1：建立双 Core Runtime 宿主

目标：

- 前台服务里持有两个 `ChatServiceCore`
- `ChatViewModel` 和 `FloatingChatService` 都不再自己 new Core

建议做法：

1. 在 `AIForegroundService` 中新增 Runtime 持有能力。
2. 新增 `ChatRuntimeSlot`。
3. 在服务内部创建：
   - `MAIN` Core
   - `FLOATING` Core
4. 对外提供按 `slot` 获取 Core 的入口。
5. 明确 Runtime 生命周期跟前台服务走，不跟 `ViewModel` 走。

## Phase 2：补齐 chat 选择隔离

目标：

- `MAIN` 和 `FLOATING` 可以停在不同 chat。

建议做法：

1. 修改 `ChatHistoryDelegate`，支持“跟随全局”或“本地保持”。
2. `MAIN` Core 继续跟随全局 `currentChatId`。
3. `FLOATING` Core 不自动跟随全局，只响应自己的切换。
4. 如有需要，再提供一个显式同步方法，把 `FLOATING` 当前 chat 写回全局。

## Phase 3：主界面接 `MAIN` Core

目标：

- `ChatViewModel` 只做 UI 协调，不再组装聊天运行时。

建议做法：

1. `ChatViewModel` 改为连接 Runtime 宿主。
2. 所有聊天状态改为从 `MAIN` Core 暴露。
3. `sendUserMessage`、`cancelMessage`、`createNewChat`、`switchChat` 等都直接转发给 `MAIN` Core。
4. 删除 `ChatViewModel` 内部那套 delegate 初始化代码。

## Phase 4：悬浮窗接 `FLOATING` Core

目标：

- `FloatingChatService` 不再创建自己的 Core。

建议做法：

1. 删除 `FloatingChatService` 里的 `chatCore = ChatServiceCore(...)`。
2. 悬浮窗改为获取 `FLOATING` Core。
3. 悬浮窗 UI 直接观察 `FLOATING` Core 的：
   - `chatHistory`
   - `currentChatId`
   - `isLoading`
   - `attachments`

## Phase 5：删除双向同步逻辑

目标：

- 让主界面和悬浮窗各自只读自己的 Core。

需要清理的方向：

- `FloatingWindowDelegate` 中的 `setReloadCallback`
- `FloatingWindowDelegate` 中的 `setChatSyncCallback`
- 主界面 `chatHistoryFlow -> floatingService.updateChatMessages(...)`
- 悬浮窗 turn complete 后通知主界面 reload 的逻辑
- `FloatingChatService.updateChatMessages(...)` 这种外部推送消息入口

完成后，消息来源必须唯一：

- 主界面只读 `MAIN` Core
- 悬浮窗只读 `FLOATING` Core

---

## 八、建议涉及文件

核心改动文件预计包括：

- `app/src/main/java/com/ai/assistance/operit/api/chat/AIForegroundService.kt`
- `app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/ChatHistoryDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/ai/assistance/operit/services/FloatingChatService.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/conversation/chat/viewmodel/FloatingWindowDelegate.kt`

必要时可以新增一个很薄的 runtime holder，例如：

- `app/src/main/java/com/ai/assistance/operit/core/chat/runtime/ChatRuntimeHolder.kt`

但它只能负责：

- 持有两个 Core
- 提供访问入口
- 管生命周期

不能继续膨胀成旧方案那种大宿主。

---

## 九、清理原则

因为当前版本未发布，本次清理按彻底替换执行：

- 删除旧文档
- 删除旧同步逻辑
- 删除不再需要的 binder 回调
- 删除悬浮窗外部推消息入口
- 删除 `ChatViewModel` 中不再需要的 runtime 装配代码

不保留：

- 兼容写法
- 双实现并存
- 过渡期回退分支

---

## 十、验收标准

完成后需要满足：

1. `ChatViewModel` 不再持有聊天核心装配逻辑。
2. `FloatingChatService` 不再创建独立 `ChatServiceCore`。
3. 前台服务内部持有两份 Core：`MAIN` 和 `FLOATING`。
4. 主聊天和悬浮窗可以同时停在不同 chat。
5. 主聊天和悬浮窗可以同时各自发送消息，loading 状态不串线。
6. 删除主界面和悬浮窗之间的手工消息同步逻辑。
7. 应用进入后台后，聊天执行不再依赖 `ViewModel` 生命周期。

---

## 十一、结论

要考虑做一个 Runtime 里面持有两个 Core。

而且这是现在最贴近需求、改动面也最可控的方案：

- 不是一个 Core 硬塞两边状态
- 不是两边各自继续乱装配
- 也不是一下子上旧文档那套过重的多 session 体系

最终收敛方向应当是：

> `AIForegroundService` 持有一个 Runtime，Runtime 内部持有 `MAIN` Core 和 `FLOATING` Core。
