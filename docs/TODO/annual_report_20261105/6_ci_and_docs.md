# 6. CI 与文档

## 旧实现情况

`web-chat` 接入构建的位置：

- 根 `package.json` 中的 `build:webchat`
- `.gitignore` 忽略 `app/src/main/assets/web-chat/`
- `.github/workflows/android-build.yml` 与 `pr-check.yml` 中的依赖安装、类型检查和构建步骤
- `ci/script/pr_check.py` 的 web 范围

## 意图

在相同位置接入 `annual-report`：

- 根 `package.json` 新增 `build:annualreport`，先构建再同步到 assets
- `.gitignore` 忽略 `app/src/main/assets/annual-report/` 和 `annual-report/dist/`
- `android-build.yml` 增加依赖安装与构建步骤，路径过滤加入 `annual-report/**`
- `pr-check.yml` 在 web 范围内增加 `typecheck` 与构建，Android 打包前同样构建报告页面
- `pr_check.py` 把 `annual-report/**` 归入 web 范围，`ci/test/test_pr_check.py` 增加对应用例
- 采集脚本和徽章生成脚本不进 CI，由维护者在发版前手动运行并提交产物

文档：

- 新增 [年度报告开发说明](../../doc-src/dev-core/ANNUAL_REPORT.md)：目录、本地预览、采集快照、生成徽章、数据路径、手写字形和新增年份
- 在 [Android 编译指南](../../doc-src/dev-core/BUILDING.md) 和 `ci/README.md` 中补充报告页面的构建步骤与流水线说明

## 期待的新实现情况

[DONE] CI 产出的 APK 自带报告前端；本地构建 APK 前运行一次 `npm run build:annualreport` 即可。协作者按文档就能预览页面、更新快照和重新生成徽章。
