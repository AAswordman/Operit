# 公共终端命令构建与校验层

## 原有结构

`apply_patch` 的资源脚本承担了两个命令的汇总校验，公共 composite action 也仍位于 `native-apply-patch` 目录。命令已分别拥有资源清单、安装器和打包脚本，但公共入口的名称和依赖方向没有分开。

## 调整范围

- 在 `tools/terminal_commands/` 放置公共资源校验入口和共享 Rust 工具链声明。
- 公共校验入口分别调用各命令的独立校验脚本，任一命令缺失、损坏或与 APK 不一致时均失败。
- 恢复 `tools/native_apply_patch/package_assets.py` 的单命令职责，不再由它调用 `web_chat_cli`。
- 将共享 composite action 移到 `.github/actions/terminal-commands/`，同步更新工作流、Gradle、检查范围和文档引用。
- 保留补丁引擎、两个命令自身的安装与打包语义，以及现有资源目录格式。

## 验证

检查差异、语法、资源入口引用、补丁源码一致性和本次文档链接。构建和测试交由仓库工作流，不在本地执行。此步骤只处理公共构建结构，不改变 UID 检查或命令执行行为。

状态：公共构建、校验、工具链及调用引用已完成迁移；脚本、YAML/TOML 与 shell 语法和调用路径检查通过，补丁源码及独立打包脚本已静态核对。仓库构建和测试结果待确认。

[DONE]