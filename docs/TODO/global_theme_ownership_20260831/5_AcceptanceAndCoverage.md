# 验收与覆盖门槛

## 静态门槛

1. 每个日常 Operit 自有原生 root 都登记一个 surface ID
2. default package 覆盖全部 required daily surface 和 component skins
3. author package 覆盖全部内容，或精确显式依赖覆盖它的 default base package
4. 未登记 root、未解析 skin 或裸露 Material 视觉入口使测试失败

## 设备矩阵

1. 手机和 tablet：app shell、drawer/rail、每类原生 route、dialogs/sheets/menus/snackbars
2. 聊天：角色栏、消息、streaming、input、model picker、attachments、IME、selection、workspace、Computer overlay
3. 浮窗、浏览器壳、WebChat、应用内权限 overlay
4. 主题导入、启用、卸载、版本基底依赖与参数编辑
5. TalkBack、字体缩放、深浅系统外观、不同密度

不得以 APK 构建成功或单页背景截图作为验收完成结论。

## 进展

[DONE] 静态门槛 1–4：surface 目录登记于 `ThemeSurfaceCatalogV2`（37 项）；链接器强制覆盖测试（缺 surface/缺皮肤/缺投影/基底冲突/参数解析）通过；内置默认包与导入包使用同一 V2 校验器。

[DONE] 容器验证：`:app:compileReleaseKotlin` 与主题相关 91 项 JVM 测试通过。

[TODO] 设备矩阵全部待执行：主壳/抽屉/各路由/弹层、聊天（流式、IME、模型选择、附件）、悬浮窗、浏览器壳、WebChat、导入启用卸载、TalkBack、深浅模式与密度。无实测截图前不报告完成。
