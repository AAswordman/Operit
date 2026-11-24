# 步骤 6：字符串资源与测试

[返回总计划](index.md)

## 旧实现情况

### 字符串

`values` 与 `values-en` 中没有 `subagent_*` 系列键，也没有 `screen_title_subagent_config` / `screen_title_subagent_editor` / `settings_section_subagent` / `settings_subagent`。

既有键 `permission_review_open_subagent_details` 等属于"审核子代理"概念，与本功能无关，不复用。

### 测试

`app/src/test/java/com/ai/assistance/operit/api/chat/enhance/SubtaskExecutionStateTest.kt` 第一条用例断言：

```kotlin
for (maximum in listOf(0, -1, 65, 999_999)) { ... assertTrue(message.contains("1 through 64")) }
```

步骤 3 把 `require` 放宽为 `>= 1` 后，`65` 与 `999_999` 不再抛异常，该断言必然失败。

参考分支把 `require` 改成 `>= 1` 却保留了这条测试，因此该测试在参考分支实际是不通过的。本次一并修正，不留红测试。

## 意图

补全中英文案，并把测试改成与新语义一致；同时给纯逻辑部分补单测，让收敛规则与并发分道有回归保护。

## 新实现

### 字符串

`values/strings.xml`（中文）与 `values-en/strings.xml`（英文）各加：

- 页面标题：`screen_title_subagent_config`、`screen_title_subagent_editor`
- 设置入口：`settings_section_subagent`、`settings_subagent`、`settings_subagent_subtitle`
- 配置页：`subagent_enable_toggle`、`subagent_tool_name_display`、`subagent_general_section`、`subagent_general_footer`、`subagent_specialized_section`、`subagent_add`、`subagent_limit_reached`、`subagent_badge_built_in`、`subagent_model_follow_session`、`subagent_model_fixed_config`、`subagent_move_up`、`subagent_move_down`、`subagent_error_duplicate_name`
- 编辑器：`subagent_editor_basic_section`、`subagent_editor_name_label`、`subagent_editor_builtin_name_locked`、`subagent_editor_description_label`、`subagent_editor_instructions_label`、`subagent_editor_model_section`、`subagent_editor_select_config`、`subagent_editor_delete_section`、`subagent_editor_delete`、`subagent_editor_save`、`subagent_editor_persist_hint`
- 卡片：`subagent_run_card_title`、`subagent_run_status_completed`、`subagent_run_status_blocked`、`subagent_run_status_failed`、`subagent_run_status_running`、`subagent_run_status_queued`、`subagent_run_card_detail_hint`、`subagent_run_live_progress`、`subagent_run_live_current_tool`
- 详情：`subagent_detail_agent`、`subagent_detail_status`、`subagent_detail_task`、`subagent_detail_tools`、`subagent_detail_evidence_missing`、`subagent_detail_summary`、`subagent_detail_full_in_evidence`、`subagent_detail_tool_not_executed`、`subagent_detail_tool_truncated`、`subagent_detail_progress`、`subagent_detail_no_tools_yet`、`subagent_detail_live_note`

其余 7 个 locale 不手写：本仓库既有的近期键（如 `permission_review_open_subagent_details`）同样只覆盖 zh/en，新增键留待 `tools/string/fill_missing_translations.py` 补。

### 测试

修正 `SubtaskExecutionStateTest` 第一条：

```kotlin
@Test
fun `subtask policy rejects non-positive budgets`() {
    for (maximum in listOf(0, -1)) {
        try {
            SubtaskExecutionState(maximum)
            org.junit.Assert.fail("non-positive budget must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("must be positive"))
        }
    }
    // 无配额语义：大预算必须可用，且不提前耗尽
    val unbounded = SubtaskExecutionState(Int.MAX_VALUE)
    assertTrue(unbounded.tryStartTool())
}
```

新增 `SubagentRosterTest`：

- 空列表 normalize 后内置行存在且置顶，名字为 `通用子代理`
- 用户把内置行改名后 normalize 恢复
- `isBuiltIn=false` 但 id 冒充内置 → 剔除
- 重名（含变音差异 `Café`/`Cafe`）去重保留先出现者
- 超长字段截断到上限
- 超过 `MAX_COUNT` 截断
- 脏数据（空名字、负 sortOrder）不抛异常
- `resolve(null)` 返回内置；未命中返回 null

新增 `AgentJobRegistryTest`：

- 同配置满额 → 第二任务 QUEUED；不同配置可同时 RUNNING
- `finish` 出队允许跨配置跳过（队首满额不阻塞后面的其它配置）
- `cancel` 排队任务会 `complete` 其 deferred，阻塞委派不会永久等待
- `normalizeConcurrency(0)` 与负数都返回 1

## 验收

- `values` 与 `values-en` 的键集合一致（无缺失、无多余）
- 单测全绿（`SubtaskExecutionStateTest` 首条不再是红的）

[DONE] 实现已完成，编译、单测与真机验证均通过（详见 [index.md](index.md) 的实施状态）。
