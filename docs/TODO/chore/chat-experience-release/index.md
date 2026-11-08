---
fork: https://github.com/3316891527/Operit
branch: feat/kong-chat-experience-integration
status: release-prepared
version: 1.12.2+6
versionCode: 62
acceptanceDate: 2026-10-20
---
# 聊天体验统一验收与发行准备

用户已确认本轮全部测试通过，并授权合入 dev、清理本轮分支、同步 main 后发布正式 Release。验收基线为 [1576d70ae](https://github.com/3316891527/Operit/commit/1576d70ae5807e34d3e47a90a3929d2eede6880a)，本次收尾只更新验收文档、发行说明与版本元数据，不再修改业务实现。

## 合并范围

- PR #7：工作区主题、聊天归档、插件市场账号与角色资料入口
- PR #8：删除状态和锁定误报
- PR #9：请求失败提示与重试策略
- PR #10：图片全屏预览、Compose 选择器和内容防重
- PR #11：每日恢复开启的日志写入开关
- PR #12：语音通话移入附件菜单、空白输入禁用发送
- PR #13：不新增用户消息的继续生成
- 统一测试分支保留上述分支的全部提交及已经验收的冲突处理结果，通过汇总 PR 合入 dev

## 验收依据

- 用户验收：完整清单全部通过，结果来源为用户确认；不记为代理本地执行
- [Android Build](https://github.com/3316891527/Operit/actions/runs/39301249323)：验收提交的 assembleDebug 已通过
- [Android Tests](https://github.com/3316891527/Operit/actions/runs/39301255200)：验收提交的 JVM 回归已通过
- [完整 78 项统一清单](../../feat/chat-image-picker-preview/02-统一测试清单.md)：保留全部操作与预期，并记录通过结果
- 本地未运行构建或测试，本轮发布任务会再次执行正式包构建、固定签名与增量包校验

## 发行参数

版本为 1.12.2+6，versionCode 为 62，沿用 Kong 包名和固定签名。main 同步通过维护者发布 PR 完成；发布使用 Android Build 的 `:app:verifyCiPreviewRelease`，`publish_release=true`，基础 Release 指定为 `v1.12.2+5`。

正式 Release 由发布工作流创建，先生成草稿并上传完整 APK、补丁和元数据，再公开。当前记录的是验收与发布准备完成，不将尚未结束的发布任务记为已经发布。具体文案见 [02-发行说明.md](02-发行说明.md)。

[DONE]
