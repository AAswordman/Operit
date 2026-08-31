---
title: 外置主题包仓库与发布
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: superseded
---

# 外置主题包仓库与发布

## 背景

当前开发分支把 `operit.reference` 作为宿主代码中的内置 manifest，并把赛博样例归档和源素材放进 APK assets。该布局不能让主题以独立仓库、独立提交和独立 Release 演进。

此方案尚未发布，旧的内置赛博包、硬编码参考 manifest 和其选择模型不形成兼容边界，直接清理。

## 目标

- `luojiaping/operit-theme-default` 是默认主题的唯一开发源和 Release 发布仓库
- 默认主题以真实 `.otheme` 归档随 APK 交付，宿主只固定上游 release 的坐标和 SHA-256
- `luojiaping/operit-theme-cyber-grid` 是赛博主题的唯一开发源和 Release 发布仓库
- 赛博主题绝不进入 APK assets，也不在启动时安装；用户通过主题页导入其 GitHub Release 的 `.otheme`
- 两个主题仓库都具备可复现打包脚本、Release 工作流、校验和与贡献说明

## 范围

- 主应用删除 `ThemePackageReferenceV1.BuiltIn`、硬编码 `operit.reference` manifest、赛博 bundled installer、赛博 APK asset 和专用测试
- 主应用将默认主题作为固定坐标的已安装包，并在 Application 创建阶段先校验、安装其 bundled release artifact
- 默认主题和赛博主题在各自 Git 仓库维护 manifest、素材、许可证归因、打包脚本和 GitHub Release
- `.otheme` 仍由主应用的严格校验器作为最终消费者校验，不引入主题代码执行或联网自动安装

## 目录

1. [分发模型与宿主迁移](./1_DistributionModelAndHostMigration.md)
2. [默认主题仓库](./2_DefaultThemeRepository.md)
3. [赛博主题仓库与发布](./3_CyberThemeRepositoryAndRelease.md)
4. [验证与交付](./4_VerificationAndDelivery.md)

## 取代说明

本计划的 V1 包格式、`operit.default@1.0.2` 与 `operit.cyber_grid@1.0.2` 已被 [global_theme_ownership_20260831](../global_theme_ownership_20260831/index.md) 的 V2 全应用主题所有权方案整体取代；对应 GitHub Release 已删除。仓库拆分与外置分发原则保留并在 V2 下继续生效。
