# 步骤 1：配置层

[返回总计划](index.md)

## 旧实现情况

无。宿主此前没有任何子代理档案概念，`examples/subagent` 的 toolpkg 只接受模型传入的 `task`/`context_text`，没有可配置的代理身份。

## 意图

建立可持久化的子代理档案体系，并让"名单"成为模型选人的唯一依据。

成本模型决定字段拆分：`name` + `description` 每请求随名单注入主对话，是固定成本，各自设上限；`instructions` 只进子任务上下文，与名单成本无关，给大额度。

## 新实现

### `data/model/SubagentProfile.kt`

`@Serializable` 数据类，字段：`id` / `name` / `description` / `instructions` / `modelBindingMode` / `modelConfigId` / `modelIndex` / `enabled` / `sortOrder` / `isBuiltIn` / `updatedAt`。

常量与上限：

- `BUILT_IN_ID = "builtin.general"`、`BUILT_IN_NAME = "通用子代理"`、`DRAFT_ID = "__new__"`
- `ModelBindingMode.FOLLOW_SESSION` / `FIXED_CONFIG`，`normalize` 只产出这两值
- `SubagentLimits`：`MAX_COUNT = 10`、`NAME = 40`、`DESCRIPTION = 200`、`INSTRUCTIONS = 4000`

`id` 与 `name` 分离：`id` 是存储主键，`name` 是模型可见标识。改名不影响历史任务显示（任务里存的是名字快照）。

### `data/model/SubagentRoster.kt`

`normalize` 是唯一入库口径，规则固定：

1. 内置行置顶且强制存在，`name` 恢复为 `BUILT_IN_NAME`，`isBuiltIn=false` 的冒充行剔除
2. 用户行按 `nameKey` 去重（保留先出现者），无名行丢弃
3. 字段截断到上限
4. 总数截断到 `MAX_COUNT`（含内置）
5. `sortOrder` 重排为 `0..n-1`
6. 永不抛异常——DataStore 读回任何历史脏数据都能收敛

`nameKey` 做变音折叠：NFD 分解 + 去组合标记 + `lowercase()`，让 `Café` 与 `Cafe` 视为同名。

`resolve(profiles, name)`：`name` 为空返回内置；未命中返回 null，由调用方报 `unknown_agent` 并列出有效名，不做猜测。

### `data/preferences/SubagentProfileManager.kt`

`versionedPreferencesDataStore(name = "subagent_profiles", currentVersion = 1)`，0→1 空迁移只写版本号。

键：`profile_list`（stringSet 存 id）、`profile_$id`（完整 JSON）、`subagents_enabled`。

CRUD 全部在单次 `dataStore.edit {}` 内读改写，避免并发丢更新。`deleteProfile` 对 `BUILT_IN_ID` 用 `require` 显式拒绝。单条 JSON 损坏只丢该行，不影响整个 DataStore。

### `ui/features/settings/screens/SubagentConfigScreen.kt`

总开关、内置行、专用子代理列表、上下移排序、新增入口。关闭开关后列表区降透明度但仍可编辑——开关管的是"是否暴露给模型"，不是"是否允许配置"。

### `ui/features/settings/screens/SubagentProfileEditorScreen.kt`

新建/编辑共用。草稿用 `DRAFT_ID` 打开，首次有效保存才落库。

编辑字段必须抽到 `EditorBody`，只在名单加载完成后挂载：

```kotlin
if (!loaded || profiles.isEmpty()) { CircularProgressIndicator(); return@CustomScaffold }
EditorBody(profileId = profileId, existing = profiles.firstOrNull { it.id == profileId }, ...)
```

若把加载守卫放在字段 `remember` 之后，`remember(profileId)` 会以空档案固化字段，key 不变时不再重新初始化——表现为"选了模型也保存不了"（名字空白导致保存按钮禁用）。这是参考实现踩过的坑。

## 依赖

- `ModelConfigManager.getAllConfigSummaries()` 提供模型配置下拉
- `ModelConfigSummary` + `getModelByIndex` 显示当前绑定
- `CustomScaffold`、`material-icons-extended`

## 验收

- 杀进程重开：名单完好，内置行仍在且名字被恢复为 `通用子代理`
- 手工写入重名行/超长字段/孤儿 JSON：读取后自动收敛，不崩
- 编辑器指定模型后保存按钮可用，保存后列表显示"指定模型"

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
