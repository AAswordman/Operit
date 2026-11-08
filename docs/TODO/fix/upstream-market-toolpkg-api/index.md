---
repo: https://github.com/3316891527/Operit
branch: fix/upstream-market-toolpkg-api
status: implemented-pending-validation
upstream_pr: https://github.com/AAswordman/Operit/pull/1474
upstream_commit: 14d746c84aef6e37eb4eba49a0bc737fac25a43f
---

# 同步市场 ToolPkg API 兼容性判断

按维护者要求移植上游 PR #1474 的单个提交。市场以加载器的 ToolPkg API 支持集合决定安装资格，移除客户端版本范围门禁和历史版本范围展示；保留服务器字段及旧记录的既有 API 基线语义。

本地已将相关代码迁入 extensions、runtime 与 metadata 分区。本次保留本地目录和模块组织，移植实际功能、九种语言提示、兼容性文档及七项回归源码，不引入上游其他提交。

- [x] 对照上游提交迁移本地路径和导入
- [x] 核对列表、详情、下载前及删除旧插件前的判断一致性
- [ ] 完成提交级静态检查与独立 PR
- [ ] 记录远程 JVM 和构建状态

详见[移植记录](01-port.md)。终端交互排查与这次市场同步分别记录；本同步不修改终端或 TerminalCore。
