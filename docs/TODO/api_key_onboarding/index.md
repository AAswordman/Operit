---
title: API Key 首次引导修复
fork: https://github.com/luojiaping/Operit
branch: fix/api-key-onboarding
status: complete
---

# API Key 首次引导修复

## 当前状况

首次进入对话页时，只要当前配置仍是空密钥的 DeepSeek 配置，界面就会覆盖聊天内容。快速入口只判断输入是否非空，自定义模型配置页返回时也不会把当前选中的配置绑定到对话功能。

## 修改意图

让快速配置使用明确的本地密钥格式校验，并让首次引导打开的自定义配置页在返回前完成保存、校验和对话配置绑定。普通模型设置入口继续维持已发布版本的行为。

## 预期结果

- 中文、空白和不可见字符不能作为 DeepSeek API Key 保存
- “配置其他模型”是清晰的次级按钮，不再是三个字的文字链接
- 从首次引导进入自定义配置后，返回操作会使用当前选中的有效配置
- 顶部返回、系统返回和返回手势遵循同一保存与绑定流程
- 保存或配置检查失败时留在设置页并显示错误
- 普通模型设置入口不会自动修改对话功能绑定

## 步骤

1. [快速配置校验与界面](./1_QuickConfiguration.md) [DONE]
2. [自定义配置返回事务](./2_CustomConfigurationReturn.md) [DONE]
3. [静态审查与交付](./3_StaticReviewAndDelivery.md) [DONE]

## 执行约束

本任务不在本地运行编译、构建或测试命令。验证限于源码检查、调用点检查和 Git diff 审查。
