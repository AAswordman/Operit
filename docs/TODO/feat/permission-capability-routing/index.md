---
fork: https://github.com/3316891527/Operit
branch: feat/permission-capability-routing
---

# 三档权限与能力路由

旧实现把无障碍、Shizuku 和设备管理员当作互斥的权限等级。管理员模式即使缺少 Shizuku，也会选择依赖它的文件工具。

本次将用户模式收敛为 STANDARD、ADMIN、ROOT。ADMIN 组合使用独立授权的无障碍与 Shizuku，不使用 Android Device Admin。旧配置中的 ACCESSIBILITY 与 DEBUGGER 在读取时映射到 ADMIN，后续保存使用新名称。Dhizuku 不在本次实现范围内。

- [x] 01：模式、授权快照、后端选择与旧配置兼容
- [x] 02：文件、Shell、UI、监听器、PhoneAgent 和包条件路由
- [x] 03：权限页面、状态展示与回归用例
- [ ] 后续：确认仓库构建与测试结果，填写 Android 实机回归结果

详细行为见 [模式与路由](01-model-and-routing.md)，验证项目见 [回归清单](03-verification.md)。
