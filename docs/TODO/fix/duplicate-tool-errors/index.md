---
repo: https://github.com/3316891527/Operit
status: IMPLEMENTED_PENDING_VALIDATION
---

# 工具错误去重与终端失败输出保留

工具结果聚合将最终失败信息同时写进正文和 error 字段，统一格式化后会出现两份错误；同时，终端底层返回的失败正文会被合成错误文本替代，导致具体输出丢失。修复只调整最终错误的聚合表示，保留分段输出、其他步骤错误及原有执行状态。

范围：ToolExecutionManager.aggregateToolResults、既有有序结果回归用例；不改变参数解析、执行顺序或历史已保存正文。

实施与完整回归见 [实施记录](1-implementation.md)。
