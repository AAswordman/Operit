# CI checks

`script/` 存放可在本地复现的检查脚本，GitHub Actions 只负责编排检查和构建。

## Local checks

在仓库根目录运行：

```bash
python3 ci/script/check_repo_hygiene.py
python3 ci/script/normalize_lint_baseline.py --check
python3 ci/script/check_markdown_links.py
python3 ci/script/check_localizations.py
npm --prefix web-chat run typecheck
```

CI 在 PR 中会使用目标分支和 PR head 的 commit 对比，只检查本次改动相关的文件；不传 commit 参数时，脚本检查当前工作树。

手动触发 `PR Required` 时没有 PR 差异基线，因此会执行全仓审计。PR 中的 Actions 语法检查只接收本次新增或修改的 workflow，已有 workflow 不会阻断无关改动。

## CI layers

- `CI required`：PR 的聚合门禁
- `Repository hygiene`：差异、冲突标记和本地 Markdown 链接，并检查本次改动涉及的 YAML 与 GitHub Actions 语法
- `Localization`：Android 字符串资源结构、key、占位符和语言提示
- `WebChat checks`：TypeScript 类型检查和 WebChat 构建
- `Android Build`：涉及 Android 或构建输入时执行完整构建
- `Documentation advisory`：Markdown 风格和拼写提示，不阻断合并

## Android lint baseline

Android lint 使用 `app/lint-baseline.xml` 记录启用 Required CI 前已有的问题。新增 error 仍会使 `:app:lintDebug` 失败；新增 warning 会继续出现在报告中，但遵循 Android lint 默认策略，不阻断构建。

初始 baseline 使用 AGP 8.13.2 并启用依赖检查，从上游提交 `1fe3b5eddb1f5c6ed795465f80716dda8c36cc65` 生成，对应 [GitHub Actions 运行](https://github.com/luojiaping/Operit/actions/runs/29661867372)。归一化路径后的 SHA-256 为 `396e0383a86d7a46b2421d020c5c80efc82faf51ce926fb2864d0593c008d535`。

baseline 只能通过 `:app:updateLintBaseline` 显式更新。生成后运行 `python3 ci/script/normalize_lint_baseline.py` 清理环境相关路径，再记录生成基准、依赖环境与新校验和，并同步脚本中的 `EXPECTED_SHA256`。提交时应单独审查新增和移除的记录，避免把当前改动引入的问题登记为历史问题。
