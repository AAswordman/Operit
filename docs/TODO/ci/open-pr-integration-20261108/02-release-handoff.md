# 联合测试结果与发行移交

联合代码固定为 c5c62fd7cbc13e0fe34661b1480720690fbf91b8，覆盖 #147–#161 的全部 15 个来源 HEAD，排除 #105。新增来源代码已完成三条远端验证：

- [Build，第 5 次尝试](https://github.com/3316891527/Operit/actions/runs/42465317894/attempts/5)：success。
- [PR Check，第 5 次尝试](https://github.com/3316891527/Operit/actions/runs/42465314656/attempts/5)：success。
- [Android Tests，第 2 次尝试](https://github.com/3316891527/Operit/actions/runs/42465320321/attempts/2)：success。

用户反馈其他功能基本通过。OpenAI 绘图源码及参数声明已经合入，但手机加载的独立脚本与 2026-10-27 最新公开 ZIP 内容相同，仅含 7 个参数；当前源码已有包含 GIF 在内的 13 个参数。Debug 构建使用已发布资源目录，不公开新资源。该问题按现有正式发行流程同步当前扩展资源，避免重复改写已经完整的绘图代码。

[1.12.2+23 发行计划](../../chore/release-1.12.2-plus-23/index.md)记录同一提交准备资源和 APK、先资源后应用公开的顺序。清理已通过的临时专项检查，原有通用检查与回归源码保留。实际资源更新与 GIF 调用效果仍待正式发行后的设备验收。

[DONE] 联合代码验证与资源版本问题定位；正式发行由后续 Build 工作流执行。
