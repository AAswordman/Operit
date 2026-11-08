# 实施与验证

## 固定来源

基线：`e9bb154a631300ec6fd7d9a073b50d58792aca38`。

- PR #147：`fix/upstream-service-cache-ble-write`，`ca126f99c4dbaada730e34c4ca4996e339b3be82`
- PR #148：`feat/phone-agent-local-execution`，`080fead02ab709cf7a78ba77c68b02b100ab5fc5`
- PR #149：`fix/offline-resource-import-actions`，`67de0cb5450addea0d7c1a43ccd83f357dc05b64`
- PR #150：`fix/theme-transfer-custom-fields`，`ad642967091b01ed1a2d4041955541a227306992`
- PR #151：`fix/upstream-a2a-tts`，`8643aa96b734dd43fddfc5dc8a43900cc311e224`
- PR #152：`fix/grep-line-numbers-sse-close`，`b56bea3a6418e02574d302167787f89d4dd8a378`
- PR #153：`fix/a2ui-catalog-expressions`，`88cf4f27d5551326c96ac3dd517039aba3316b54`
- PR #154：`feat/extension-detail-screens`，`070b71a213a331dd13e90da4c3a7dffec6a1a0ee`
- PR #155：`fix/app-launch-preserve-task`，`988f9c6df7c2f36521750785cbb718520fb6dd49`
- PR #156：`fix/upstream-market-toolpkg-api`，`bae1ed5b0617335bb7bb2781695fc1a8ce82fa75`
- PR #157：`fix/terminal-device-status-reports`，`a576507ed3cde27e51b887e5423fa4125421f21e`
- PR #158：`feat/model-vision-image26`，`0a4507bb026ced48e96c2f8ab32f7a961f98649a`
- PR #159：`feat/extension-updates-market`，`bd9ddba7a67a7512bef0a89ecd81401c6933acb7`
- PR #160：`fix/worldbook-storage-integrity`，`662d23bc30e6a80e7e10dad2df501c32571409f9`
- PR #161：`fix/stream-display-anr`，`f9e1ffbd73f50b24d0c14857138a6afef3a52211`

## 合并与验证状态

15 个来源 HEAD 已合入并通过祖先覆盖核对，其中 #155 已由 #148 包含。#154 和 #160 的资源冲突保留双方不同键值，九语言 XML 与资源名唯一性均已检查；#159 安装入口先执行 #156 的 API 兼容性检查，再进入更新锁及持有名称传递流程。#161 使用截图修复后的 `f9e1ffbd`。

GitHub 比较接口确认 #105 与 dev 的共同祖先为 `e829418743de17a23e5cc9efe1caf8ef19735f0f`，dev 领先 96 个提交、另有 #105 独有的 1 个提交；该独有 HEAD 不在联合分支相对 dev 的历史中。

已从本联合测试分支移除世界书临时专项 job、candidate 依赖、结果变量及必过门槛，保留实现、回归文件和通用检查。依据是 [24/24 回归通过记录](https://github.com/3316891527/Operit/actions/runs/42457174369/job/128658664317) 与 [最新世界书提交专项通过记录](https://github.com/3316891527/Operit/actions/runs/42457484340/job/128659556555)。原 PR 分支未改写；#160 原分支的合并前清理事项仍需在其单独合并前处理。

仓库卫生、本地化、链接和工作流结构静态检查将在推送前执行，结果及远端派发记录保存在仓库外的本轮交付目录。现有 Build 使用 `assembleDebug`，现有 Android Tests 执行其原定任务；Release 发布、仓库迁移和合并分支清理均关闭。通过草稿联合测试 PR 触发现有 PR Check。最终 CI 结果由用户查看，不将派发成功记为测试通过。

[DONE] 固定来源、完成合并及冲突处理；静态检查和远端派发待记录。
