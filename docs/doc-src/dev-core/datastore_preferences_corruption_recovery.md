---
title: 应用一打开就闪退？可能是偏好文件坏了
date: 2026-10-12
last_reviewed: 2026-11-06
related:
  - issue: https://github.com/AAswordman/Operit/issues/494
  - pr: https://github.com/AAswordman/Operit/pull/1162
tags: [DataStore, 崩溃, 恢复, 排障]
---

# 应用一打开就闪退？可能是偏好文件坏了

## 遇到的问题

应用一打开，1 到 2 秒就闪退，直接跳到故障页面。日志里报的是这个错：

```
java.lang.ClassCastException: java.lang.Boolean cannot be cast to java.lang.Integer
    at com.ai.assistance.operit.data.preferences.UserPreferencesManager.readThemePreferenceValues(UserPreferencesManager.kt:959)
```

这段日志说明：应用读取主题偏好时，某个预期为整数的值实际是布尔值，发生了类型不匹配。日志没有指出具体的偏好键，也不能仅凭这段堆栈确定错误数据由哪个版本写入。

覆盖安装不会清掉应用的老数据，因此更新安装包后，同一份偏好数据仍可能触发崩溃。

## 为什么会这样

可以确认的是，存储值的类型与当前读取代码的预期不一致。是否由历史版本迁移、导入数据或其他写入路径造成，需要结合原始偏好文件和代码历史继续确认，不能直接认定某个版本曾把布尔设置改成整数。相关启动崩溃报告见 [Issue #494](https://github.com/AAswordman/Operit/issues/494)。

仓库提供独立的数据恢复界面，可手动检查配置与数据库，参见 [PR #1162](https://github.com/AAswordman/Operit/pull/1162)。以下步骤和限制按当前源码说明，不保证适用于所有历史版本。

## 检查能确认什么

`PreferencesHealthManager` 会读取偏好文件的副本，检查 DataStore 文件能否解析，并检查其中以 `{` 或 `[` 开头的字符串能否作为 JSON 解析。它没有按 `UserPreferencesManager` 的每个键检查预期类型。

因此，一个能正常解析、但把整数键存成布尔值的文件，仍可能显示检查通过。这类 `ClassCastException` 不一定会出现在可自动处理的清单中。修复按钮不可用时，应查看检查报告；不能仅凭按钮灰色就判定是主进程仍在运行。

## 办法一：用官方的数据恢复界面

先决条件：手机开 USB 调试，连上电脑。以下示例包名为 `com.ai.assistance.operit`，其他安装变体应使用对应包名。

在电脑上执行下面任一条命令：

```bash
adb shell am start -n com.ai.assistance.operit/.ui.recovery.DataRecoveryActivity
```

或

```bash
adb shell am start -a com.ai.assistance.operit.action.OPEN_DATA_RECOVERY
```

操作前先导出并另存一份未经修改的原始快照。配置修复会保全待处理的原文件，然后删除报告中列出的整份 `.preferences_pb` 文件，由应用重新生成；它不是只修改类型错误的那一个键。删除 `user_preferences.preferences_pb` 的影响见下一节。

然后按这个顺序操作：

1. 找到“配置和数据库检测”，点“检查配置和数据库”。
2. 展开报告，确认有可处理的配置文件，并核对“将执行的修复操作”列出的影响范围。
3. 点“修复可处理问题”，确认后等待完成。保存界面显示的“修复前配置原件”归档路径。
4. 确认复检结果，再点“启动主应用”。若仍有问题，保留报告和原件继续排查。

按钮仅在当前没有操作运行，且配置或数据库报告至少一项支持修复时启用。配置报告还要求检测到可处理文件、没有阻塞问题，并能确认主进程已经停止；无法确认进程状态时也不会执行配置修复。

若报告涉及主进程仍在运行，可先在手机设置中强行停止 Operit，再通过上面的命令直接进入恢复界面，并重新检查。若报告通过、需要人工处理，或文件检查失败，强行停止并不能保证按钮可用。不要把“检查通过”当作已经排除了所有偏好类型错误。

## 办法二：在快照副本中移除已确认有问题的偏好文件

只有已确认故障来自 `user_preferences.preferences_pb`、并接受重置该文件全部内容时，才使用这个办法。`user_preferences` 不只保存主题，还保存语言、角色或角色组主题覆盖、记忆空间列表及当前选择，以及尚未迁移的旧用户资料记录和索引等。删除整份文件会清除其中所有内容，可能影响旧资料迁移及已有记忆数据的访问，不能保证只是外观重置。

1. 在数据恢复界面导出原始快照 ZIP，另存原件，只对副本操作。
2. 解压副本，检查 `payload/files/datastore/` 和 `payload/datastore/`。当前 `UserPreferencesManager` 使用的标准位置是前者，对应应用的 `files/datastore/`；后者对应应用数据目录下独立的 `datastore/`，快照也会导出和恢复该目录。两处不是同一路径的别名，内容应分别核对。
3. 仅从恢复副本中移除已经确认需要重置的 `user_preferences.preferences_pb`，保留其他偏好文件、数据库和附件。若发现 `user_preferences.backup.pb` 等历史备份，先保留在原始归档中核对来源，不应仅凭文件名认定它也损坏或必须删除。
4. 重新打包 ZIP，确保 `manifest.json` 和 `payload/` 仍直接位于压缩包根目录，没有多套一层文件夹。
5. 在用于恢复的设备上先备份现有数据，再进入数据恢复界面导入这个处理后的快照。原始快照导入会替换对应的本地数据目录，不是把文件增量合并到当前数据中。
6. 导入完成后启动主应用，检查聊天记录、记忆空间、旧资料和设置是否符合预期；保留原始快照供后续核对。

未从快照移除的数据库和文件会按快照内容恢复，但偏好索引或迁移信息缺失仍可能影响应用能否正常访问它们。恢复前应理解这个区别，不能把文件仍在等同于所有资料都能正常使用。

## 几个小提醒

- 保留未经修改的原始快照，以及界面生成的修复前配置归档。
- 重置整份偏好文件会影响其中全部内容；不要按“只重置主题”的预期操作。
- 向社区反馈时附上崩溃堆栈和检查报告，避免公开包含个人资料的原始快照。

## 参考链接

- [Issue #494](https://github.com/AAswordman/Operit/issues/494)：相关启动崩溃报告
- [PR #1162](https://github.com/AAswordman/Operit/pull/1162)：配置与数据库恢复功能
- [DataRecoveryActivity.kt](../../../app/src/main/java/com/ai/assistance/operit/ui/recovery/DataRecoveryActivity.kt)：检查报告、修复按钮条件和原件归档路径
- [PreferencesHealthManager.kt](../../../app/src/main/java/com/ai/assistance/operit/data/recovery/PreferencesHealthManager.kt)：检查范围、主进程状态校验、保全原件后重置偏好文件
- [RawSnapshotBackupManager.kt](../../../app/src/main/java/com/ai/assistance/operit/data/backup/RawSnapshotBackupManager.kt)：ZIP 目录映射与整份快照恢复行为
- [UserPreferencesManager.kt](../../../app/src/main/java/com/ai/assistance/operit/data/preferences/UserPreferencesManager.kt)：`user_preferences` 的存储位置、偏好内容和旧资料索引
