---
title: 步骤 3：外部制品清单
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 3
depends_on:
  - 2_JsWorkspaceAndLockfile.md
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 3：外部制品清单

[上一步：pnpm workspace 与锁文件](./2_JsWorkspaceAndLockfile.md) · [返回总计划](./index.md) · [下一步：Gradle 输入规范化](./4_GradleInputNormalization.md)

## 旧实现情况

- `libs.zip`、`jniLibs.zip` 和 `subpack.zip` 由仓库外流程准备；默认 STT 模型已迁移到 Gradle 生成 assets 流程
- 当前工作树中的三个非模型目标目录只有 `.keep`
- Gradle 无法说明本地文件的来源、版本、许可证、hash 和唯一消费者
- 原计划记录了部分归档内容，但当前工作树不能直接证明这些记录
- `arsc.jar` 已无当前消费者；GIF native 库由 Maven 的 `android-gif-drawable` 提供且与归档副本一致
- `libc++_shared.so` 的唯一所有者仍需完成审计

## 预期的新实现情况

- 一个受版本控制的 manifest 登记全部外部归档和解包文件
- 每个条目具有来源、版本、许可证、SHA-256、大小、ABI 和唯一消费者
- 未声明文件、重复所有者、hash 不符、ABI 不符和越界归档都会被拒绝
- Sherpa 源码构建目标与 `jniLibs.zip` 中的预编译库分别登记

## 修改作用域

计划修改：

- `config/build-system/artifacts/`
- `docs/licenses/` 中与外部制品对应的记录
- `docs/TODO/ci/refactor_building_sys/`

本步骤不修改：

- `app/build.gradle.kts` 的依赖声明
- feature module 目录
- 外部制品的业务内容

## 计划

1. 取得三个实际归档，并对归档本身与解包文件计算 SHA-256 和大小。
2. 检查 AAR、JAR、Manifest、classes、native 库、ABI 和许可证。
3. 搜索 `arsc.jar` 的源码 import、字节码引用和运行时加载点。[DONE: 当前无消费者]
4. 列出每个同名 `.so` 的全部提供者，确认 GIF native 库与 C++ runtime 的唯一所有者。[部分完成：GIF 库与 Maven 副本 SHA-256 一致]
5. 登记 Sherpa NCNN、Sherpa MNN、streamnative 和 ripgrep 等源码 native 目标。
6. 写入 manifest，并让每个目标路径只能映射到一个消费者。

## 验收

- manifest 覆盖实际归档和所有解包文件
- 每个文件都有真实 hash、许可证、ABI 与唯一消费者
- 无消费者的文件被标记为删除项
- 当前无法取得的制品保持未确认状态，不写入推测内容

## 完成记录

状态：未开始。制品检查需要实际归档，缺少归档时本步骤不能标记完成。
