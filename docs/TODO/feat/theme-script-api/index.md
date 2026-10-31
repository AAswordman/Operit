---
fork: https://github.com/3316891527/Operit
branch: feat/oktheme-script-api
base: 3e37884024c244a565893fbc017c214ac33b5a61
status: implemented_pending_ci
---
# 角色主题的 JavaScript 外观接口

现有主题以角色卡或角色组为作用域，使用草稿预览和显式保存，完整主题通过 `.oktheme` 分享。本次在同一套主题中增加 JS 外观和素材，现有主题参数继续持有颜色、字体、透明度和玻璃配置。

参考调研核对了 `luojiaping/Operit@54df846c` 与 `AAswordman/Operit2@31a935c1`。本次采用运行时外观描述和素材打包的思路，保留现有角色作用域和主题文件类型。

实施记录：

1. [包职责与依赖安排](00-package-layout.md)
2. [主题数据与分享](01-theme-data.md)
3. [脚本执行与原生绘制](02-runtime-and-editor.md)
4. [回归范围与验证状态](03-verification.md)
5. [原生主题范围扩展](04-native-theme-expansion.md)
6. [原生组件类型与导入约束](05-native-component-compatibility.md)
7. [原生主题工作室与资源目录](06-theme-studio.md)
8. [工作室图标子集与模式扩展引用](07-studio-ci-references.md)
9. [主题 JSON 与 JavaScript 制作参考](08-authoring-reference.md)
10. [工作室配色、触控选项与聊天预览](09-studio-usability.md)
11. [预览工具栏与统一菜单](10-preview-toolbar-and-menus.md)
12. [实测反馈与真实预览](11-studio-feedback.md)
13. [属性修改崩溃与预览界面清理](12-crash-and-preview-cleanup.md)
14. [部件属性消费、形状描边与源码高亮](13-surface-capabilities-and-source-highlighting.md)

[DONE] 共享模型、素材存储、角色偏好、去品牌的 `.oktheme` 归档、运行限制、原生绘制、可视化编辑与模板、完整配色、原生组件适配、原生主题工作室、目录资源工程、资源字体与像素采样、JS/TS 示例及接口文档已实现。未增加 data 一级子包。基础为 #92–#98 合入后的 dev。

当前状态为实现完成、仓库验证结果待回报；未执行本地构建或测试。Android 原生执行与界面场景未实测，不作为本次提交和工作流派发的前置条件。API 文档位于 `docs/doc-src/feature-protocol/theme_script_api.md`，SDK 位于 `examples/theme-script/`。
