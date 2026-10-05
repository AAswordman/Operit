# 模式与路由

用户模式只保留 STANDARD、ADMIN、ROOT。无障碍、Shizuku、Root 是运行时授权来源，不再各占一个模式。新的 ADMIN 与 Android Device Admin 无关，设备管理员 Shell 和监听器实现已移除。内部的 Debugger 工具仍作为 Shizuku 后端复用，不再暴露为权限等级。

## 配置兼容

DataStore 的 preferred_permission_level 键不变。首次读取时将 ACCESSIBILITY、DEBUGGER 迁移为 ADMIN，保留其他偏好与自定义 su 命令。字符串解析也兼容这两个旧值，使用固定 Locale，避免设备语言改变结果。STANDARD、ADMIN、ROOT 和未设置状态不迁移。

偏好只保存模式，不保存或复制系统授权。选中 ADMIN 不会自动获得无障碍或 Shizuku 授权，需要分别启用。

## 后端选择

- STANDARD：即使系统已授权其他来源，也只使用标准实现
- ADMIN 无增强授权：文件与 Shell 使用标准实现，UI 仍受 Android 现有能力限制
- ADMIN 仅无障碍：主屏幕节点、手势、按键使用无障碍，文件与 Shell 使用标准实现
- ADMIN 仅 Shizuku：文件与 Shell 优先使用 Shizuku，UI 使用 Shell 实现
- ADMIN 两者都有：主屏幕 UI 优先无障碍，文件与 Shell 优先 Shizuku
- ROOT 已授权：优先使用 Root；Root 不可用时按已授权的管理员能力选择后端

显式 display 参数不交给主屏幕无障碍后端。主屏幕全局按键使用无障碍；其他按键在 Shell 已授权时使用 Shell，避免无障碍已开启时屏蔽 Enter 等按键。ShellIdentity.APP 使用标准应用身份，显式 ROOT 身份要求 ROOT 模式和 Root 授权，显式 SHELL 身份要求已授权的 Shell 后端，两者都不改为应用身份执行。

实例按 PermissionBackend 缓存。一次 sendMessage 创建独立的权限会话，首次需要能力路由时建立 Shell/UI 快照，同一轮的串行和并行工具调用复用该快照；未显式绑定会话的直接工具入口使用惰性 fallback 会话。权限模式、Root 执行模式或自定义 su 命令改变时让下一次未绑定工具调用重新建立快照，侧栏状态查询使用独立读取，不污染工具会话。无障碍是否可用以 Provider 绑定和服务状态为准。Shell 工厂不再把 ADMIN 路由到 Device Admin，也不缓存用户模式。会话中途授权撤销不重新探测或重放命令，由当前实际命令返回错误，下一轮再按最新授权路由。

## 文件操作

管理员文件工具复用标准与 Shell 文件实现，统一覆盖目录、读取、分段读取、存在检查、查找、信息、写入、删除、移动、复制、建目录、压缩、解压、打开、分享和下载。

无可用增强授权时，在提交前直接选择标准文件实现。只读操作失败后可尝试标准实现；如果两者都失败，保留首次失败结果。写入、删除、移动、复制等有副作用的操作提交后不自动重试，避免服务中断时重复修改数据。Shell 文件写入不再在 base64 写入失败后再次使用 printf 写入；分块写入失败时停止并返回错误。Shell 命令同样只在提交前选择后端，返回失败后不重放任意命令。

二进制文件和标准实现已有的 Linux、SAF、仓库环境处理继续复用原有代码，本次不新增这些后端的功能。

## 消费能力

PhoneAgent 的虚拟屏幕判断与包状态的 ui.virtual_display 均检查当前真实 Shell 能力和实验开关；只开无障碍不再被当成 ADB 授权。主屏幕 Shower 输入也重新检查授权与实验开关；虚拟屏幕即使已经创建，撤权后仍停止后续动作，不把虚拟屏幕的启动操作发送到主屏幕。

包条件中的 android.permission_level 返回三档模式名称。需要判断 Shizuku 能力时使用 android.shizuku_available，虚拟屏幕使用 ui.virtual_display；只比较 ADMIN 不代表已经授权 Shizuku。使用旧模式名称的外部条件表达式需要更新。

权限页、初始引导与侧栏使用三档模式。管理员页分别显示无障碍和 Shizuku 的状态与授权向导，功能展示随实际授权变化；Root 模式缺少 Root 授权时仍显示未授权状态。

Dhizuku、稳定节点动作协议、终端 apply_patch 命令均不在本次范围内。

[DONE]
