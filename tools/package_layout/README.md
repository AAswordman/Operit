# 包路径迁移静态核对

检查器依赖 Python 3.11 以上与 Git，无第三方 Python 依赖；不构建应用、不执行应用单元或设备测试。每份检查对应固定迁移阶段，不作为所有未来业务提交的永久门禁。

## 当前旧界面与虚拟形象清理核对

在本轮清理快照的仓库根目录运行：

```sh
python tools/package_layout/verify_legacy_removal.py
python ci/distribution/resources/verify_hub_catalog.py
```

清理检查固定比较 29972a8a 基线与 removal-migration.json，核对实际删除/修改摘要、未改应用源码、保留的 Waifu/头像/执行器/插件/语音/Android 入口、九套资源和原样迁移的共用实现。Kotlin 检查只覆盖词法结构，应用编译和 JVM 用例仍交给仓库工作流；它不是后续业务修改的永久检查。

## 当前资源目录核对

资源目录同步后的工作树运行：

```sh
python ci/distribution/resources/verify_hub_catalog.py
```

它核对固定目录与 Git 索引中的扩展输入快照。需先暂存实际扩展源码，构建重生成 JS/dist 不改变该输入快照。固定资源变更必须同步新归档、组版本、全局标签和公开下载地址。

## 命名与组件收尾快照

在 `f2d9a9caf1f17d5139cc1e2e33c0d92417369e48` 快照的仓库根目录运行：

```sh
python tools/package_layout/verify_layout_completion.py
```

浅克隆需先取回基线 `5efa22eceb33738ca57dcca528eafb2b7c118797`。规则与目标摘要位于 docs/TODO/chore/package-layout/completion-migration.json；检查全部源码集合、受控名称替换、资源删除、范围外摘要、九份组件包声明、Manifest/Widget 入口、桥接/SDK/内置插件、11 处明确兼容块、两处 Compose DSL 默认值及原生协议文件。completion_text.py 只实施清单中的显式变换，没有运行时作用。

命名收尾时第二轮 677 个文件的路径快照位于 docs/TODO/chore/package-layout/ui-utilities-migration-final.csv。后续删除与语音组件迁移由 removal-migration.json 单独登记；历史 CSV/JSON 继续保留原快照值，便于复现。

## 历史阶段复现

第一轮 API 快照为 b419768cd9fa166b56031bcb38bf5fe957d69e98，比较基线 342694313de3a129e320f326d861903c662930f5；在该快照运行 verify_api_migration.py。

第二轮快照为 5efa22eceb33738ca57dcca528eafb2b7c118797，比较工具箱清理基线 8566d30589199a01570eb7cb2901f533a9cf5074；在该快照运行 verify_layout_migration.py。第二轮当时的九份保留包声明已由当前收尾处理，旧检查器不适用于收尾后的工作树。

例如复现第二轮：

```sh
git worktree add --detach ../operit-layout-round2 5efa22eceb33738ca57dcca528eafb2b7c118797
cd ../operit-layout-round2
python tools/package_layout/verify_layout_migration.py
```

浅克隆需取回对应阶段的基线与实现提交。应用编译、内置插件打包和 JVM 测试交给既有 Android Build / Android Tests 工作流；派发受理与最终通过分别记录。
