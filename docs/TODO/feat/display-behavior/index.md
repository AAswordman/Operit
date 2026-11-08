---
repo: https://github.com/3316891527/Operit
branch: feat/config-workflow-attachments
---

# 显示与行为定稿落地

按已确认的 HTML 和全部二级窗口截图，实现聊天、界面、自动化三个页签，保留原文案、卡片顺序、选择形态及 26 项设置。页面跟随 App 现有主题。

- [x] [01-实现与核对.md](01-实现与核对.md)：原生页面、窗口、保存与本页重置已实现并完成静态核对。
- [x] [02-完整验收清单.md](02-完整验收清单.md)：68 项完整操作、预期和独立结果表已整理；设备结果均待执行。
- [x] [03-静态核对记录.md](03-静态核对记录.md)：记录检查范围、结论和未执行项。

实现使用独立的 `ui/features/settings/display/` 目录，保留原 `GlobalDisplaySettingsScreen` 路由入口。定稿 HTML 归档为 [design.html](design.html)，其中浏览器浅深色预览、演示标记和模拟权限控件仅用于设计展示，不作为 Android 功能。

中文、英文、日文、韩文、西班牙文、巴西葡萄牙文、印尼文、马来文、罗马尼亚文各补齐 166 项资源，放在各语言 `strings.xml` 中。

按最新安排，远端工作流暂缓。后续恢复时再派发 assembleDebug 与 Android Tests，关闭发布开关，不轮询结果。当前没有本轮编译成功或设备验收通过的结论。主题新增「布局」页签另交 HTML 设计稿，认可后再实施 Android 合并。
