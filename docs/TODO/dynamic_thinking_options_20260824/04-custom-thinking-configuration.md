---
fork: https://github.com/AAswordman/Operit
---

# 自定义思考配置

## 作用范围

思考配置属于单个模型配置。规则保存在该模型配置的 `thinkingConfigurations` JSON 中，当前选中的档位保存在 `thinkingOptionId` 中，不再使用全局 `ApiPreferences` 保存思考档位。

这套配置同时决定当前 Provider、模型和 endpoint 可显示的思考滑块，以及请求阶段实际写入的思考参数。

## 规则优先级

`thinkingConfigurations` 是有顺序的 JSON 数组。运行时会按照数组顺序查找第一条启用且同时匹配 Provider、模型和 endpoint 的规则，首条命中规则立即生效。因此规则顺序具有实际语义，不只是显示顺序。设置页中的上移和下移操作会改变规则匹配优先级。

规则可以通过 `providers`、`providerTypeIds`、`modelPrefix`、`modelContains`、`modelSuffix`、`modelRegex`、`firstSegment`、`lastSegmentPrefix`、`lastSegmentContains`、`lastSegmentRegex` 等字段匹配模型；需要区分接口时可以使用 `endpointSuffix`。

## 规则结构

一个档位型规则通常具有以下结构：

```json
{
  "id": "example-reasoning-effort",
  "enabled": true,
  "providers": ["OPENAI"],
  "match": {
    "modelPrefix": ["example-model"],
    "endpointSuffix": ["/responses"]
  },
  "control": "levels",
  "parameterLabel": "reasoning.effort",
  "options": [
    {
      "id": "off",
      "label": "关闭",
      "path": "reasoning.effort",
      "value": "none"
    },
    {
      "id": "low",
      "label": "低",
      "path": "reasoning.effort",
      "value": "low"
    },
    {
      "id": "high",
      "label": "高",
      "path": "reasoning.effort",
      "value": "high"
    }
  ]
}
```

`control` 可以是 `levels` 或 `toggle_only`。规则还可以定义 `enable`、`disable`、`enabledActions`、`disabledActions`，以及档位自身的 `actions`。每个 action 包含 JSON `path`、`value`，并可以通过 `overwrite` 指定是否覆盖请求中已有的值。

## 档位排序和默认值

界面和运行时会对已知档位统一规范化排序：

1. `off` 和 `none` 排在最前。
2. 数值预算按数值从小到大排列。
3. 文本 effort 按 `minimal`、`low`、`medium`、`high`、`xhigh`、`max` 排列。
4. 无法识别的自定义值保持原有相对顺序。

该排序只调整同一规则内部的 `options`，不会改变规则数组本身的匹配优先级。

如果历史配置中的 `thinkingOptionId` 已经不存在，应用会回退到第一个启用档位，避免因为旧 ID 而静默省略思考参数。

## Provider 预设和自定义规则

新建模型配置时，会从 `ModelThinkingConfigDefaultsCollect.kt` 导入对应 Provider 的内置预设。设置页可以在保留现有自定义规则的同时添加支持的预设。自定义规则应尽量使用具体的模型匹配条件，并在需要时增加 endpoint 匹配，避免通用规则抢先覆盖更具体的规则。

## DeepSeek Responses

DeepSeek Responses 使用 endpoint 专用规则，并将思考强度写入：

```json
{
  "reasoning": {
    "effort": "none | low | high | max"
  }
}
```

Responses 适配器会清理不兼容的 Chat 格式 `thinking` 字段和顶层 `reasoning_effort` 字段，避免两套协议混用。DeepSeek Responses 规则会保持在通用 DeepSeek Chat 规则之前，从而确保 `/responses` 使用 `reasoning.effort`。

## 实现和验证

主要实现位于：

- `ThinkingQualityMapping.kt`：规则解析、档位映射和请求 action 应用。
- `ThinkingQualityOptionOrdering.kt`：档位排序。
- `ModelThinkingConfigDefaultsCollect.kt`：Provider 内置预设。
- `ModelConfigManager.kt`：模型配置持久化、默认档位和历史配置迁移。
- `ModelConfigScreen.kt`：设置页编辑器。

相关测试覆盖 Provider 预设排序、无效历史 ID 回退、DeepSeek Responses 请求规范化，以及 `low`、`high`、`max` 请求值。
