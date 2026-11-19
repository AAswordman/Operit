# 06 全部分段关闭时的自由格式提示词

## 旧实现情况

applySummarySectionOverrides 逐段渲染，enabled 为 false 的分段整体跳过，最后无条件拼接模板原文的 suffix。用户把四个分段全部关闭后，渲染结果只剩三段残骸：孤立的分隔线标题行、一段要求"必须使用上述固定格式"的格式要求清单、以及一段内容要求清单。清单与上方已被清空的正文自相矛盾，模型只能自由发挥，按最常见形态输出多段结构，用户看到"关闭了分段却仍然是多板块总结"。与此同时，buildSummarySystemPrompt 把自定义总结规则裸拼在提示词末尾，没有指令标识，模型把它当作待总结的对话内容，把规则文本写进了摘要正文。

## 意图修正

分段覆盖的目的是让用户决定总结的结构。全部分段关闭不是"关闭总结"，而是"用自由格式总结"，必须给出可直接执行的指令，而不是留一份要求遵循已不存在格式的矛盾空壳。部分关闭同理，模板原文的格式要求写死了全部分段，会诱导模型把关掉的分段补回。自定义规则是指令而不是内容，需要有明确的指令标识。

## 期待的新实现情况

applySummarySectionOverrides 先统计启用与关闭的分段数量：

- 没有任何启用分段时，返回模板前缀加自由格式总结指令，不再拼接模板原文的格式要求与内容要求清单，开头说明与标题标记保留
- 存在启用分段且有分段被关闭时，在保留的分段之后、模板原文格式要求之前追加分段锁定约束，声明只允许输出列出的分段
- 没有分段被关闭时不追加分段锁定约束，未编辑用户的提示词与默认模板逐字一致

buildSummarySystemPrompt 的自定义规则改为带指令头的段落，中英文各一份，规则文本紧跟指令头，仍位于提示词末尾。

## 细化作用域

- 修改 core/config/FunctionalPrompts.kt 的 applySummarySectionOverrides 与 buildSummarySystemPrompt，新增自由格式指令、分段锁定约束、规则指令头三组中英文常量
- 扩充 test/core/config/FunctionalPromptsSummaryTest.kt：全关时不含模板格式要求清单特征句且含自由格式指令、全关叠加自定义规则时规则带头部出现在末尾、部分关闭时含分段锁定约束、无关闭时不追加锁定约束
- 保持 03 步既有的保留行为：未做任何编辑的用户提示词与默认模板逐字一致

## 调试注释要求

在新增常量与渲染分支处注释历史上全关后只输出矛盾空壳、以及规则裸拼被当作内容这两个问题的成因，说明现在为何改成自由格式指令与带头规则。

## 实际落地记录

- applySummarySectionOverrides 增加 hasEnabledSection 与 hasDisabledSection 预统计，判定口径与逐段渲染一致，enabled 为 null 视为启用
- 全关时输出模板前缀加 SUMMARY_ALL_SECTIONS_DISABLED_CN/EN，明确不要分段标题、不要分隔线、不要输出格式要求清单，并给出自由格式应覆盖的内容范围
- 部分关闭时在保留分段之后追加 SUMMARY_SECTION_LOCK_HINT_CN/EN，禁止模型新增或补回未列出的分段标题；无分段被关闭时不追加，默认提示词逐字不变
- 自定义规则改为 SUMMARY_GLOBAL_RULES_HEADER_CN/EN 加规则文本的段落，仍位于提示词末尾
- 单测扩充到 16 个用例，全关场景的两个用例最初误把自由格式指令中"不要输出格式要求"的字样判为模板清单残留，改为断言模板清单原文特征句"必须使用上述固定格式，包括分隔线"与"结尾使用等号分隔线"
- GetDiagnostics 对 FunctionalPrompts 与测试文件零告警，:app:compileDebugKotlin 与 FunctionalPromptsSummaryTest 16 用例通过

[DONE]
