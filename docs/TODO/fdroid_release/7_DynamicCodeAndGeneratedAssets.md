---
title: 后续步骤：动态代码与生成资产
status: planned
document_type: implementation-step
depends_on: 2_BinaryAndBuildInputInventory.md
---
# 动态代码与生成资产

## 目标
审计外部 DEX/JAR、ToolPkg、脚本安装能力，并让 WebChat/示例包输出可重现。

## 最小执行单元
1. 列出运行时加载或执行的 DEX/JAR/JS/WASM、插件市场和包管理入口。
2. 依据 F-Droid 动态代码政策决定编译保留或排除，不使用空实现掩盖。
3. 固定 Node/pnpm 输入并验证 WebChat、ToolPkg 同步输出。
4. 为 APKTool/JADX 资源建立源码到打包资产的对应表。

## 验收
- 每项动态行为有明确政策结论和编译处置。
- 生成资产能从锁定输入重现且无未声明二进制。
- 未完成代码改造和验证前不添加 `[DONE]`。
