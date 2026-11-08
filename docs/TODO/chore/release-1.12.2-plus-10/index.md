---
fork: https://github.com/3316891527/Operit
status: 代码已完成，准备同步分支与正式发布
---

# 1.12.2+10 发布同步

维护者已授权最后两处 UX 调整完成后，将数据与备份功能分支合入 dev，再同步 main，并从 main 派发正式发布。本次为维护者发布同步，main 是发布 PR 的目标分支。

最后调整包括发言设置使用现有自定义滑块、保留毫秒范围和步进，以及工具权限保存反馈改为 OKToast、规则区独立撤销入口。相关操作仍由 [完整 136 项验收清单](../../feat/data-and-backup/verification.md) 记录，既有检查编号与结果保留。

已发布的 1.12.2+9 不可覆盖。本次版本为 1.12.2+10，versionCode 为 66，使用现有独立发行签名和增量包发布脚本。

发布输入：main、android-build.yml、gradle_task=:app:verifyCiPreviewRelease、publish_release=true、previous_tag=v1.12.2+9，并提供中文发行说明。按本次维护者要求，不额外派发 Debug 和 Android Tests；正式构建仍执行发布工作流原有的打包和签名核对。

滑块参数、导入、资源及最终差异核对完成 [DONE]。分支合并、派发受理与正式构建结果另记提交记录，不将受理等同于发布完成。
