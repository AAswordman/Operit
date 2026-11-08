---
For_Agent: Codex OAuth encrypted prefs use Android Keystore and must not be restored without the original device key.
---

# Codex Auth Keystore Restore Crash

## 原状

`CodexAuthPreferences` 使用 `EncryptedSharedPreferences` 保存 OAuth token。Android Auto Backup 和设备迁移会复制 `shared_prefs` XML，但 Android Keystore key 仍然绑定在原设备。恢复后的 Tink keyset 无法通过新设备 key 校验，会在打开模型设置页时抛出 `AEADBadTagException`。

## 作用域

- `app/src/main/java/com/ai/assistance/operit/data/preferences/CodexAuthPreferences.kt`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

## 预期

- Android Auto Backup 与设备迁移不再携带 `codex_oauth_credentials.xml`
- 已经损坏的 Codex OAuth encrypted store 会被限定清理，用户回到未登录状态并可重新登录
- 其他 SharedPreferences、DataStore 和数据库不受影响
