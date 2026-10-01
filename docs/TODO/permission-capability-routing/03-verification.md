# 回归清单

## 自动化用例

已添加 PermissionCapabilitiesTest、PermissionOperationRouterTest、PermissionCapabilitySessionTest、AndroidPermissionModeMigrationTest，共 17 个测试方法。能力矩阵遍历三档模式与四个授权状态位的全部 48 种组合，并覆盖 DEFAULT、APP、ROOT、SHELL 四种身份的后端选择；会话测试覆盖同一会话复用、模式切换清理、UI/Shell 快照关联和消息会话隔离。

仓库 Android Tests 执行 :app:testDebugUnitTest，Android Build 执行 assembleDebug。本地不启动 Gradle，提交后由仓库工作流验证。工作流结果与实机结果分别记录，不把提交工作流当成测试通过。

## Android 实机

1. 旧 ACCESSIBILITY 配置升级：显示 ADMIN，其他偏好不变
2. 旧 DEBUGGER 配置升级：显示 ADMIN，Shizuku 授权不变
3. STANDARD、ADMIN、ROOT 和未设置配置：保留原状态
4. 首次引导和权限页：只有标准、管理员、Root 三档
5. ADMIN 两者未授权：普通可访问路径的读写、建目录、复制、移动、删除成功；受限路径返回实际错误
6. ADMIN 仅无障碍：节点读取、点击、长按、滑动、输入、返回键可用；文件与 Shell 使用标准能力
7. ADMIN 仅 Shizuku：目录、读取、分段读取、文件信息与系统操作使用 Shizuku；UI 使用 Shell
8. ADMIN 两者都有：主屏幕 UI 使用无障碍，文件与 Shell 使用 Shizuku
9. ADMIN 授权中撤销 Shizuku 或停止服务：当前消息会话不重新探测、不重放命令，实际命令返回错误；下一轮重新建立快照后按最新授权使用标准实现或 Shizuku
10. 只读操作的增强后端返回失败：标准实现成功时返回读取结果；都失败时保留首次错误
11. 写入、追加、删除、移动、复制执行中断：不自动重复提交；分块写入中途失败时不重写已经提交的数据
12. 关闭无障碍：下一轮 UI 操作重新建立快照并改用可用 Shell 后端；同一轮已选无障碍路径不重新探测，两者都不可用时返回能力错误
13. 显式 display：不把操作发送到主屏幕无障碍后端
14. STANDARD 且增强来源都已授权：不使用增强权限
15. ROOT 已授权：Root 文件和 UI 路径正常，APP 身份命令保持应用身份
16. ROOT 未授权：普通文件操作使用可用管理员或标准实现；显式 ROOT 身份返回未授权。SHELL 身份缺少增强授权时返回失败，不使用应用身份
17. 授权更换、页面返回与刷新：状态、功能展示、侧栏同步；无障碍和 Shizuku 可以分别开启
18. 仅无障碍时启动虚拟屏幕：不声明具备 ADB 能力；Shizuku 或 Root 授权并打开实验开关后可进入原有虚拟屏幕流程
19. 主屏幕 Shower 已准备后撤销增强授权或关闭实验开关：输入不继续使用过期的 Shower 能力状态。虚拟屏幕已创建后撤权：后续动作返回权限错误，不在主屏幕启动应用
20. Linux、SAF、仓库环境和二进制文件：原有标准实现行为不变
21. 中英与其他本地化页面：管理员文案不再表示 Android Device Admin，长文案不遮挡状态或按钮
22. ADMIN 两者都有：返回、主页等全局按键使用无障碍；Enter、删除等其他按键使用 Shell。仅无障碍时其他按键返回不支持
23. 同一条消息中的串行和并行工具只建立一次权限快照；两个并行消息各自使用独立快照；直接工具入口首次惰性建立快照，后续调用复用，权限配置变更后重新建立

## 结果填写

| 项目 | 设备与 Android 版本 | 结果 | 证据 |
| --- | --- | --- | --- |
| Android Build / assembleDebug | GitHub Actions | 待填写 | |
| Android Tests / testDebugUnitTest | GitHub Actions | 待填写 | |
| 实机 1–4：迁移与三档入口 | | 待填写 | |
| 实机 5–8：授权组合 | | 待填写 | |
| 实机 9–14：失效与降级 | | 待填写 | |
| 实机 15–16：Root 与身份 | | 待填写 | |
| 实机 17–19：状态与虚拟屏幕 | | 待填写 | |
| 实机 20–21：原后端与本地化 | | 待填写 | |
| 实机 22：全局与非全局按键 | | 待填写 | |

[DONE]
