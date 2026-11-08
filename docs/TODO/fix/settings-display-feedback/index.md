---
fork: https://github.com/3316891527/Operit
branch: fix/settings-display-feedback
---
# 设置页提醒与协议阅读显示修复

模型配置已经通过页面内横幅展示地区提醒，切换供应商时再次弹出相同的底部消息没有必要。本次仅移除重复通知，保留地区判断、横幅和其他操作反馈。

软件内用户协议入口复用首次启动组件，标题固定在正文滚动区之外，完整协议又通过原生 TextView 绘制，主题颜色只在创建视图时设置。本次将完整协议改为 Compose 富文本，使文字颜色、字体缩放和布局使用同一套主题与排版；软件内阅读模式的标题和正文一起滚动，不再显示首次确认的提示语。首次启动仍保留原有正文滚动区和底部确认按钮。

## 范围

- `ModelApiSettingsSection.kt`：删除地区提醒对应的重复底部通知调用，不删除页面横幅，也不修改供应商和地区判定
- `AgreementScreen.kt`：使用 `AnnotatedString.fromHtml` 显示现有完整协议；阅读模式整页滚动，背景覆盖整个页面
- 不修改协议正文、协议版本、已同意状态、首次启动 5 秒倒计时、权限引导、模型配置引导或其他通知

## 验证

不运行本地 Android 构建或 JVM 测试；提交后通过个人 fork 的 `Android Build / assembleDebug` 和 `Android Tests` 验证。真机检查见 [回归清单](01-regression.md)，尚未取得的结果不标记为通过。
