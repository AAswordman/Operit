# 设置和 UI

模型配置 DataStore 保存 `thinking_option_id`，Web API 使用同名字符串字段。滑块直接以
当前配置规则解析出的选项数量设置 stops 和 labels，切换模型后由当前映射计算可用位置。

模型配置页新增“思考配置”块，和模型参数同级。外层默认收起；展开后只展示每条配置的模型匹配、请求路径和控件摘要。点击单条预览进入对应配置的紧凑弹窗，写入动作和滑块档位在弹窗内继续折叠。

规则跟随模型配置保存。新建配置或切换供应商时，从
`app/src/main/java/com/ai/assistance/operit/data/collects/ModelThinkingConfigDefaultsCollect.kt`
写入对应内置规则。请求阶段只读取当前模型配置，不再读取收集目录。思考档位也只从当前模型配置读取，
不再保存在 `ApiPreferences` 的全局状态中。

规则数组顺序就是匹配优先级：界面会明确提示按从上到下判断，首条命中的启用规则立即生效；
每条规则预览项提供上移和下移操作，调整后的顺序随规则配置自动保存。

完成请求参数迁移、Android/WebChat 类型迁移和静态检查后，在此文档末尾添加
`[DONE]`。

[DONE]
