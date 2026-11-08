# Material 图标子集

源码固定为 AndroidX Material Icons 1.7.8，许可证 Apache-2.0。保留源码直接引用的扩展图标，实现和原库一致；核心图标仍通过 `material-icons-core` 提供。

`tools/icons/sync_material_icons.py` 从 Google Maven 的固定 sources JAR 生成子集，并校验 SHA-256。新增应用或终端图标引用后运行该脚本；先初始化固定版本的 `terminal` 子模块，再执行 `--check` 检查双方引用是否完整。使用仓库外的固定版本终端源码时通过 `--terminal-source <目录>` 指定。云端在子模块初始化后检查引用，继续由真实编译和 DEX 合并验证产物。

终端独立编译仍使用其完整扩展图标依赖，但应用的 `:terminal` 依赖边界排除 `material-icons-extended` 及 Android 制品，最终 APK 统一使用本模块和核心图标库，避免同名类被打包两次。

插件和页面配置原有的 Filled 名称查询由 `MaterialFilledIconCatalog` 读取相同版本的 2,132 个矢量数据，保留所有名称、路径和填充规则。动态路径只在请求对应图标时构建 ImageVector，不再通过反射保留整个扩展图标库。

保留 AOSP 原始版权与文件头。自动生成数据的来源和静态图标清单记录在 `tools/icons/material-icons-manifest.json`。APK 字节变化以云端 APK 报告为准。
