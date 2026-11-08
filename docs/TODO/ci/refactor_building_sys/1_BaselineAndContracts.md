---
title: 步骤 1：基线与构建契约
status: draft
document_type: implementation-step
For_Agent: 未经用户明确授权，不执行编译、构建、测试或发布命令
step: 1
depends_on: []
fork_repository: "git@github.com:Nyashiiro/Operit-follow-up.git"
last_reviewed: 2026-07-14
---

# 步骤 1：基线与构建契约

[返回总计划](./index.md) · [下一步：pnpm workspace 与锁文件](./2_JsWorkspaceAndLockfile.md)

## 旧实现情况

- `versionCode` 和 `versionName` 固定在 `app/build.gradle.kts`
- Node.js、pnpm、JDK、Android SDK、NDK 和 Gradle 没有统一的版本清单
- JS assets、外部制品、流水线阶段和最终 APK 没有共同的机器可读清单格式
- 当前 APK 的 applicationId、签名、ABI、assets 和 native 库缺少可比较的基线记录
- 用户可见能力与内部构建入口之间没有书面不变量

## 预期的新实现情况

- `config/build-system/` 保存工具链、版本元数据、阶段契约和清单 schema
- 基线记录能够描述 APK 身份、内容和构建输入
- 用户可见能力、数据格式和对外接口形成明确的不变量清单
- 后续步骤只使用本步骤确定的字段和版本来源

## 修改作用域

计划修改：

- `config/build-system/`
- `ci/script/build_inventory/`
- `docs/TODO/ci/refactor_building_sys/`

本步骤不修改：

- Android 功能实现和模块结构
- JavaScript 依赖树
- Gradle 变体与签名配置

## 计划

1. 静态盘点当前 Gradle、package manifest、assets 生产者、外部制品目录和补丁脚本。
2. 定义 toolchain、version、pipeline stage、JS assets、外部制品、最终构建清单的字段。
3. 记录工具链精确版本及其来源，禁止使用“最新版本”等动态描述。
4. 建立用户接口不变量，覆盖功能入口、持久化数据、applicationId 和更新行为。
5. 为已有可发布 APK 定义基线采集入口，使其能够从指定 APK 或流水线产物生成实包记录。
6. 在清单 schema 和文档之间建立相对链接。

## 验收

- `config/build-system/` 中的每类配置只有一个事实来源
- 清单字段能够表达后续十三个步骤需要的输入与产物
- 未知值被标记为待确认，不写入推测值
- 静态基线与当前仓库配置一致

## 完成记录

状态：未开始。完成代码、文档和授权验证后，在一级标题末尾添加 `[DONE]`。
