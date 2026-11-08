# Room 健康检查

## 旧实现

数据救援界面只能由用户手动输入 SQL。数据库无法正常打开时，用户缺少结构化诊断结果。

## 修改意图

- 检查 `app_database` 是否为普通文件
- 使用不会主动删除损坏源的 SQLite corruption handler
- 执行 `PRAGMA quick_check`、`PRAGMA user_version` 和 `PRAGMA foreign_key_check`
- 检查 WAL、SHM 和 journal 的路径类型与大小
- 主进程停止且 SQLite 基础检查通过后，在隔离副本上验证完整 Room schema 和 migration chain
- 将结果建模为明确的健康等级和检查项
- 在 SQL 执行器下方展示报告

## 期待结果

用户可以在不执行自定义 SQL 的情况下确认数据库当前状态，并知道问题是否存在确定的处理方式。

[DONE]
