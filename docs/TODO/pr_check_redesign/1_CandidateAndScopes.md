---
title: 步骤 1：候选树与作用域契约
status: completed
---

# 步骤 1：候选树与作用域契约

## 旧实现

PR workflow 检出贡献者 head，并比较事件中的 base tip 与 head tip。贡献者分支落后时，上游后续提交会污染差异范围，head 也不包含最终合并树中的修复和构建入口。

## 新实现

`pull_request` 事件只检出 `github.sha`。检查入口验证该提交恰有两个父提交，第一父提交等于事件 base，第二父提交等于事件 head。

路径分类只使用：

```text
base = candidate^1
diff = base..candidate
workspace = candidate
```

rename 按删除旧路径和新增新路径同时分类，路径传递使用 NUL 分隔。分类规则只保存在 `ci/script/pr_check.py`。

## 验收

- 过时 fork 不会把上游新增文档归入贡献者改动
- candidate 父提交与事件不一致时直接失败
- 翻译、WebChat、JVM、native、ToolPkg 和文档路径得到确定作用域
- rename 和 delete 不会漏掉原路径对应的检查

[DONE]
