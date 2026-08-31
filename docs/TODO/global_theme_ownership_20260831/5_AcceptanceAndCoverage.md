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
