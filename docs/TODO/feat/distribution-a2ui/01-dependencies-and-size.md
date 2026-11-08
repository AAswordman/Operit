# 依赖、来源与包体输入

原先没有 A2UI，Compose 依赖为 BOM 2026.02.01，构建使用 SDK 36 / AGP 8.13。参考发行版使用 AndroidX A2UI alpha01，其协议、引擎和 Compose AAR 均声明 SDK 37 / AGP 9.1，完整 Material A2UI 又引入新 Compose 与重复图标。直接添加这些二进制依赖会改变现有构建栈。

`a2ui-compat/` 保留官方 alpha01 的 105 个协议、引擎、Compose 运行时与通用 UI 源文件，按本项目的 SDK 与 Compose 编译；未更改这 105 个源文件的协议实现。来源逐文件 SHA-256 记录在模块 `sources.json`，原始 Apache-2.0 文件头和许可保留。宿主自行提供 CardSurface、Divider 和原有主题的局部对比度工具，不引入完整 Material A2UI 组件及其内置图标。

Dynamic UI 宿主、Express 编译器、工具包和相关夹具来自 CATMIAOZHI/Operit 固定提交 `9902726bec086d2950e6472c14f6acb9cca85413`。来源声明、上游 LICENSE 原文、AndroidX Apache-2.0 与 Lucide 许可位于 `app/src/main/assets/licenses/`。主题、品牌、包名和无关改造不随功能迁入。

原有 `material-icons-extended` 提供 11,105 个扩展图标 Kotlin 实现，应用与固定版本终端源码直接引用其中 304 个。本轮用 `material-icons-subset/` 保留这些原始实现，核心图标继续由同版本 `material-icons-core:1.7.8` 提供。原来的动态 Filled 名称查询覆盖 2,132 个名称；改用从相同源文件提取的紧凑路径数据，按需构建 ImageVector，保留填充规则和镜像属性。未知名称的原有返回行为不变。

`tools/icons/sync_material_icons.py` 校验固定 sources JAR 的 SHA-256 后生成子集及数据，提交前用 `--check` 校验新增源码引用是否已被包含；已有手动工作流继续执行真实 Kotlin 编译与 JVM 用例。源码下载缓存不提交，图标子集和运行时数据是应用必要输入。

APK 报告沿用离线资源打包校验，增加 DEX、原生库、assets、resources、其他 ZIP 条目的压缩和展开尺寸及最大的 20 个条目，保存到 `app/build/reports/apk-size/` 并随云端报告上传。

此处的 304/11,105 是图标实现数量，不能当作 APK 体积减少比例。A2UI 引擎、宿主和 Lucide 同时新增内容；净变化必须比较同一构建变体的前后 APK。当前没有本轮 APK 实测结果。

[DONE] 源码与报告路径已接入；兼容性和尺寸待云端验证。
