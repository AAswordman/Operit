# 06 文档、示例包与单测

## 旧实现

- toolpkg 开发者文档：`docs/doc-src/toolpkg_developer_documentation/`，
  `04_modules/toolpkg.md` 覆盖 Registry 全部成员与 API 版本说明
  （1.0.0/1.0.1 门禁语义）。
- 示例包在 `examples/` 与 `app/src/main/assets/packages/`；
  Operit2 侧有 `floating_windows_smoke.dart` 冒烟参照。

## 意图修正

按 AGENTS.md 维护相称文档（写前过一遍
`docs/doc-src/before_docing.md`）；提供可装示例包与纯 JVM 单测。

## 期待的新实现

- 文档：
  - `04_modules/toolpkg.md`：API 版本节补 `1.1.0`（引入
    `ui.floatingWindows`，说明与 Operit2 v2 同形但版本号独立）；Registry
    节补 `ui` 成员
  - 版本说明明确：Operit 的 1.1.0 ≠ Operit2 的 2.0.0，跨平台包在两端的
    manifest `api_version` 门禁各自独立判断
- 示例：`examples/` 增加 smoke 包（或扩展现有包），覆盖
  open → update(options+state) → show/hide → list → close 全流程，
  附 capabilities 探测
- 单测（纯 JVM，不依赖 WindowManager）：
  - ID 校验（trim/长度/重复）
  - 16 窗上限
  - update 浅合并（options 补丁 + state 浅合并）
  - 属主隔离（A 插件看不到/操作不了 B 的窗口）
  - 审计回收（模拟禁用后窗口被关）
- 默认不执行编译/测试（AGENTS.md），验证由用户触发



[DONE]
