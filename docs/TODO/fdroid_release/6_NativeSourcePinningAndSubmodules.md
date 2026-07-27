---
title: 后续步骤：原生源码固定与子模块
status: planned
document_type: implementation-step
depends_on: 2_BinaryAndBuildInputInventory.md
---
# 原生源码固定与子模块

## 目标
把所有 CMake/子模块输入固定到公开、可审计 commit，并确保干净检出可准备。

## 最小执行单元
1. 将 sherpa-ncnn、WAMR、MNN、Saba、Bullet3、ufbx 等浮动 ref 固定到 commit。
2. 记录每项源码许可证、archive hash、补丁和构建参数。
3. 审计 terminal 固定 commit 及其嵌套输入。
4. 从 F-Droid 源码准备路径移除私有 SSH 发布子模块。

## 验收
- 干净检出不解析浮动分支，不要求私有仓库权限。
- 每个原生产物能对应固定源码和构建参数。
- 未完成代码改造和验证前不添加 `[DONE]`。
