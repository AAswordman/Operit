---
title: 应用一打开就闪退？可能是偏好文件坏了
date: 2026-10-12
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

看不懂也没关系，简单说就是：**应用读"主题设置"的时候，发现数据长歪了，直接懵了然后崩溃。**

更坑的是，重新覆盖安装最新版也没用。因为覆盖安装不会清掉应用的老数据，坏掉的文件还在那里，装完照样崩。

## 为什么会这样

以前某个版本把"主题设置"存成了一种格式（对/错），后来版本改成另一种格式（数字）。老数据没跟着变，新版本一读就类型对不上，直接闪退。这个问题官方已经知道了（问题 #494）。

官方在 V1.12.2 版本加了个"数据恢复"功能，但它**不会自动启动**，需要你手动去恢复界面操作（对应合并请求 #1162）。

## 我是怎么查出来的

1. 先看崩溃日志，定位到是主题设置那行代码出的问题
2. 去 GitHub 问题区一搜，果然有人报过同样的问题（#494）
3. 又翻了翻合并请求 #1162，确认新版本有手动修复功能
4. 兴冲冲进去点"修复"，结果按钮是灰的点不了
5. 翻了源码才发现：修复按钮要满足条件才能点亮，而且**主进程还活着的时候它就不让修**

## 办法一：用官方的数据恢复界面（推荐）

先决条件：手机开 USB 调试，连上电脑。

在电脑上执行下面任一条命令：

```bash
adb shell am start -n com.ai.assistance.operit/.ui.recovery.DataRecoveryActivity
```

或

```bash
adb shell am start -a com.ai.assistance.operit.action.OPEN_DATA_RECOVERY
```

然后按这个顺序操作：

1. 找到"数据库健康"这一块，点"检查"
2. 等报告出来，确认显示"需要修复"、并且能看到坏掉的 `.preferences_pb` 文件
3. 点"修复"
4. 修完点"启动主应用"

> 小提示：如果"修复"按钮是灰色的，先去手机设置里把 Operit 强行停止，或者重启一下手机，再进恢复界面，按钮一般就能亮了。

## 办法二：把快照里的"坏文件"挑出来扔掉（我最后用的这个）

如果修复按钮死活点不了，可以用这个土办法

1. 在数据恢复界面，先把当前数据导出一个快照（一个 zip 包）
2. 把 zip 解压，删掉里面这两个坏文件：
   - `payload/files/datastore/user_preferences.preferences_pb`
   - `payload/files/datastore/user_preferences.backup.pb`
3. 重新打包成 zip，传到另一台设备上
4. 在另一台设备的数据恢复界面里点"导入快照"，选这个清理过的 zip
5. 恢复完点"启动主应用"

这样搞完，**聊天记录、文件这些都在，只是主题外观设置会变回默认**（这也是没办法，坏的就是这个文件）。

## 几个小提醒

- 动手前先把原始快照备份好，免得手滑
- 主题外观设置会重置回默认，重新调一下就行
- 搞不定可以去社区问：QQ 群、Discord、GitHub 问题区都行

## 参考链接

- 问题 #494：应用启动崩溃（数据损坏导致）
- 合并请求 #1162：新增手动配置与数据库修复
- 数据恢复界面源码：`app/src/main/java/com/ai/assistance/operit/ui/recovery/DataRecoveryActivity.kt`
- 修复逻辑源码：`app/src/main/java/com/ai/assistance/operit/data/recovery/PreferencesHealthManager.kt`