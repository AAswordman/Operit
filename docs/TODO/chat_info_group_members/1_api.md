---
title: 群成员读取接口
status: completed
---

# 群成员读取接口

1. 在 ChatInfo 中增加可选的 memberCardIds，不修改旧字段。
2. 为列表页内会话一次读取角色组快照；按 orderIndex 生成成员 ID，同时用于 list_chats 和 find_chat。
3. 存在的空群返回空数组；普通会话和已删除角色组不返回成员字段。名单表示当前配置，不是历史参与者。
4. 同步 TypeScript 声明、聊天模块文档和文本结果。增加结构化列表与查找、空群及字段缺省的序列化用例。

经用户要求，使用临时 JVM 工程，以项目相同的 Kotlin 2.2.21、kotlinx.serialization 1.9.0 和 JUnit 4.13.2，直接编译仓库中的原始数据类及 ChatInfoMemberCardIdsTest。3 个序列化用例全部通过，无失败或跳过。临时工程位于已忽略的 build 目录，不随提交发布。

Android 单测入口触发 NDK 安装后已中止，未完成 Android 构建。静态检查覆盖调用链、声明、文档及 diff；设备上仍需验证成员重排、修改群成员后重新查询，以及列表与查找结果一致。

[DONE]
