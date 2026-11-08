---
topic: Thinking quality slider UI
status: implemented-user-verified
---

# Thinking quality slider UI

本次补录：用户确认目前 Bug 均已修复，本页待填写的验收说明已按用户确认同步。验证来源及现有 CI 记录见[验证结果补录](../../chore/distribution_integration/06-验证结果补录.md)。

## 原本状况

Android Classic 和 Agent 使用了两套重复的思考程度控件。控件只显示全局数字档位，Classic 还允许编辑数字；provider 的真实映射分散在各自的请求构建代码中。

## 修正意图

使用 Material 3 风格的离散滑块，显示当前 provider、协议和模型的真实映射文本。内部使用 thinking_option_id 字符串契约，不再保留全局固定档位数字。

轨道 active 区域使用主题主色和低速流光动效，拖动时加速，系统减少动态效果时关闭动画。

## 作用域

- 统一 provider 映射描述和请求参数来源
- 重构 Android Classic、Agent 两套思考程度控件
- 同步 Web 模型选择状态和两套 Web 输入样式
- 每个 provider 注册自己的选项数量；仅在协议确实支持别名档位且语义不同的情况下保留重复 wire 值
- 为映射契约补充测试和实现记录

## 细化步骤

1. 映射契约
2. Android 滑块
3. Web 同步
4. 验证记录

## 完成记录

- [DONE] provider 映射、Android/Web 滑块和主题主色流光已实现
- [DONE] 远程 Release 构建已通过
- [DONE] 视觉修订已通过远程 Release 构建
- [REJECTED] 早期 thumb/track 比例和渐变方案未通过视觉验收
- [REJECTED] 早期黑色 thumb 和满宽渐变方案未通过视觉验收
- [DONE] 透明外框、仅填充 active 区域和内嵌高亮 thumb 已通过 Release 构建；视觉验收通过（用户确认）
- [DONE] Android/Web 档位标签响应式锚点布局已通过 Release 构建；视觉验收通过（用户确认）
