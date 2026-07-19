---
title: 步骤 4：Gradle 输入规范化
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 4
depends_on:
  - 3_ExternalArtifactManifest.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 4：Gradle 输入规范化

[上一步：外部制品清单](./3_ExternalArtifactManifest.md) · [返回总计划](./index.md) · [下一步：工具目录迁移](./5_ToolingDirectoryMigration.md)

## 旧实现情况

- `app/libs` 通过 `implementation(fileTree(...))` 无条件接收全部 AAR 和 JAR
- `packaging.resources` 中存在宽泛的 `pickFirsts += "**/*.so"`
- 本地二进制与 native 库没有 Gradle 输入校验
- Gradle Wrapper 使用单一镜像地址；PR 门禁重构已补入 Gradle 8.13 官方 `distributionSha256Sum`
- 当前依赖声明无法从 Gradle 图追溯到步骤 3 的制品 manifest

## 预期的新实现情况

- 本地 AAR 和 JAR 使用精确文件名声明
- JNI 与 native 冲突通过删除重复提供者解决
- Gradle 在解析相关任务前校验制品文件名、hash、ABI 和所有者
- Wrapper 使用项目确认的单一地址与 Gradle 官方 SHA-256
- Gradle 只消费步骤 3 manifest 中已经确认的文件

## 修改作用域

计划修改：

- `app/build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `config/build-system/artifacts/`
- 与 Gradle 输入校验有关的 build logic

本步骤不修改：

- Android 源码能力边界
- product flavor 和 build type
- JS assets 生产任务

## 计划

1. 将 `fileTree` 替换为 manifest 已确认文件的精确依赖声明。
2. 删除宽泛 `.so` 选择规则，并删除重复 native 提供者。
3. 将无消费者的 `arsc.jar` 和其他文件移出构建输入。
4. 增加只读的 Gradle 输入校验任务，输出可审计报告。
5. 写入 Wrapper 官方 SHA-256，并核对当前分发地址对应的内容。
6. 静态扫描 Gradle 配置，确认不存在其他本地目录依赖扫描。

## 验收

- Gradle 依赖声明中不存在本地 AAR 或 JAR 通配扫描
- `.so` 没有宽泛 `pickFirst` 规则
- manifest 与 Gradle 消费文件一一对应
- Wrapper URL 与 SHA-256 指向同一发行内容
- 输入错误会明确终止，不会选择其他文件或地址

## 完成记录

状态：部分准备。Wrapper 校验和已写入；本步骤其余输入规范化尚未开始，完成前仍需记录 Gradle 解析与编译验证结果。
