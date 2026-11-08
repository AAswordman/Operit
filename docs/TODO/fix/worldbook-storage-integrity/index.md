---
fork: https://github.com/3316891527/Operit
branch: fix/worldbook-storage-integrity
status: implemented; remote verification in progress
---
# 世界书存储完整性修复

读取失败曾被转换为空数组或空变量库，后续整库保存可覆盖旧数据。独立 UI 与主运行时也可能同时读改写，存储管理还把插件配置目录标成可删除源码。

PR: https://github.com/3316891527/Operit/pull/160

本次保留 entries、groups、variables 文件格式及现有插件工具接口，修正读取、保存、并发、迁移与清理路径。

- [实现和边界](01-storage-integrity.md)
- [故障回归和交付记录](02-validation.md)
