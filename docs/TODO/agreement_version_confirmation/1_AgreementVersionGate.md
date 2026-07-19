---
title: 步骤 1：协议版本门禁
status: completed
document_type: implementation-step
step: 1
depends_on: []
last_reviewed: 2026-07-15
---

# 步骤 1：协议版本门禁 [DONE]

[返回总计划](./index.md)

## 旧实现情况

- `agreement_preferences` 仅保存 `agreement_accepted` 布尔值
- 启动流程只检查该布尔值
- 修改协议资源文本后，已同意旧版本的用户不会再次确认

## 预期的新实现情况

- 应用维护当前协议版本常量
- 偏好设置保存用户已确认的协议版本
- 只有已确认当前版本的用户可进入主应用
- 旧版无版本确认记录不满足当前版本的确认要求
- 协议界面展示当前版本

## 修改作用域

- `app/src/main/java/com/ai/assistance/operit/data/preferences/AgreementPreferences.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/main/MainActivity.kt`
- `app/src/main/java/com/ai/assistance/operit/ui/features/agreement/screens/AgreementScreen.kt`
- `app/src/main/res/values*/strings.xml`

## 验收

- 新安装用户确认协议后保存当前版本
- 已安装旧版本的用户升级后重新看到协议页
- 提高当前协议版本时，已确认旧版本的用户重新看到协议页
- 应用内协议查看入口不改变用户的已确认版本

## 完成记录

状态：完成。已完成源码引用、版本资源和差异静态核对；未执行编译、构建或测试命令。
