# 主题仓库与发布

## 默认主题

`luojiaping/operit-theme-default` 是 V2 默认主题的唯一源。它实现全部 required daily surface 与通用组件皮肤。APK 仅内置其精确 Release artifact 与 SHA-256 lock。

## 赛博主题

`luojiaping/operit-theme-cyber-grid` 是赛博主题的唯一源。它显式依赖默认主题的精确 V2 坐标，并覆盖 shell、chat、component skins 与资源。它永不进入 APK；用户从 GitHub Release 导入 `.otheme`。

## 发布

两仓库的 package 脚本必须验证 V2 manifest、全部资源摘要、required coverage、确定性 ZIP metadata 与 `Operit Theme Package` comment。只从 release tag 产出 `.otheme` 与 SHA-256 assets。
