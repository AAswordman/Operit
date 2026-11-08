---
title: 欢迎页与用户协议正文接入
status: done
document_type: implementation-record
---
# 欢迎页与用户协议正文接入 [DONE]
本轮将首次使用页调整为真正的启动页，并将用户协议与本文档内的个人信息、隐私边界说明接入应用内阅读页。
## 欢迎页结构
- 使用 `ic_launcher_simple_foreground` 透明前景图标居中显示，不再套用非透明方形容器
- 图标下方居中显示应用名和广告词“让想法，走出对话框。”
- 底部只保留带箭头的“开始”按钮，点击后直接进入主界面
- 欢迎页不再显示协议勾选框、协议链接或第二页流程
- 页面没有配置导入、权限集中授权或额外功能介绍
## 协议正文
- `app/src/main/res/raw/operit_user_agreement_v02.md` 读取完整正文，移除草稿元信息和第 22.5 条
- 阅读页显示居中的“Operit Kong 用户协议与隐私政策”标题，正文使用项目已有 Markdown 渲染器处理 `##` 标题、`**` 粗体和 URL
- 第十五条作为本文档内的个人信息、隐私与授权边界说明；正文不再引用不存在的独立《Operit 隐私政策》
- 补充真实的项目 Issues 反馈入口，修正“首页列明”但界面不再展示联系方式的不一致
## 首次启动状态
- 新记录使用 `onboarding_completed`，按钮点击表示完成首次启动流程，不伪装成明确的协议同意
- 已有 `agreement_accepted` 或 `accepted_agreement_version` 记录只做一次兼容迁移，避免覆盖安装用户重复进入欢迎页
## 验证
- 九个 `values*` 目录的 `strings.xml` 均通过 XML 解析
- 九种语言均保留 `onboarding_tagline` 和 `onboarding_start`，不再保留欢迎页协议勾选文案
- 已检查协议正文不再包含草稿元信息、第 22.5 条或对不存在独立隐私政策的引用
- 未运行本地 Android 构建、单元测试或真机回归；按仓库约定交由远程工作流和后续设备验收
