---
title: 步骤 2：差异归责与检查器
status: completed
---

# 步骤 2：差异归责与检查器

## 旧实现

本地化检查从新增 XML 行提取 key，再把所有语言中的同名历史错误提升为本次错误。仓库卫生和 Markdown 检查在缺少 base 参数时切换为全仓扫描，PR 与本地调用的语义并不稳定。

## 新实现

- 所有 PR 检查显式接收 base 和 candidate
- 本地化资源分别从两个 Git object 解析，按 locale、类型和 key 比较
- 同一 locale 的组合 qualifier 资源按文件路径独立比较，不会互相覆盖
- 只有 candidate 新增的诊断，或 PR 实际触碰的资源实体仍存在的诊断，才阻断
- 缺失翻译、相同文本和历史问题只汇总为提示
- 仓库卫生统一检查候选净差异中的空白、冲突标记及 JSON/XML 语法
- 诊断写入 Actions annotation 和 step summary，并限制重复输出数量

## 验收

- 修改罗马尼亚语不会触发英文或葡萄牙语历史占位符错误
- 多行 string、plurals、array 和删除资源能被语义比较识别
- `locales_config.xml` 与实际 values 目录保持双向一致
- `locale-config` 的根元素、子项、默认 locale 和重复项均经过结构校验
- 快速检查一次展示全部可修问题，不因首个错误提前终止

[DONE]
