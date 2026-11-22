# 01 默认模型与定价行同步官方现状

## 旧实现

- `ApiProviderConfigCollect.kt` DEEPSEEK 供应商 `defaultModelName = "deepseek-v4-flash"`。
- `ApiPreferences.kt` `DEFAULT_MODEL_NAME = "deepseek-v4-flash"`。
- `ScrapedModelPricingRowsCollect.kt` DEEPSEEK 块含 `deepseek-chat`、`deepseek-reasoner`（已退役）、`deepseek-v4-flash`（旧名）与 `deepseek-v4-pro`，币种 CNY，同步标注停留在 2026-05-07。

## 意图修正

官方文档 2026-11 起模型 ID 收敛为 `deepseek-flash` 与 `deepseek-v4-pro`，定价仅公布 USD（peak 值），`deepseek-v4-flash` 旧名仍按 Flash 价格路由。配置默认值与内置定价应反映官方现状，避免新用户拿到旧名与失真价格。

## 新实现

- 两处默认模型名改为 `deepseek-flash`。
- 定价块更新为：`deepseek-flash|TOKEN|0.3|1.2|0.006|USD`、`deepseek-v4-pro|TOKEN|0.66|1.98|0.044|USD`，并保留仍可路由的旧名 `deepseek-v4-flash`（按 Flash 价格）；移除已退役的 `deepseek-chat`、`deepseek-reasoner`。同步注释更新为 2026-11-22。

[DONE]
