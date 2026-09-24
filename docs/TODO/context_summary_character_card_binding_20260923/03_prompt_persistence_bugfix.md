# 03 分段提示词持久化修复

## 旧实现情况

分段配置在设置界面以 List<SummarySectionConfig> 编辑，保存时经 buildSummarySectionOverrides 转成 List<SummarySectionOverride>，只持久化与默认不同的字段，enabled 与默认一致时不落盘。生成时 buildSummarySystemPrompt 仅在 sectionOverrides 非空时才调用 applySummarySectionOverrides。

默认指令文本由 summaryPromptTemplate 的 block.substringAfter('\n').trimEnd() 得到。界面回填与保存比较都基于这套文本。用户改过内容后如果规范化结果与默认文本一致，或者用户把全部段落保持默认，overrides 为空，覆盖逻辑整体跳过，于是关闭开关与修改文本都可能表现为不生效。此外 applySummarySectionOverrides 已能正确处理单段关闭，问题集中在持久化层的丢弃与非空门槛。

## 意图修正

改为规范化后的全量持久化：每个分段的 id、enabled、title、instruction 全部写入，界面文本与默认文本比较前做同一套规范化，不再做默认差异过滤。全量持久化后 overrides 恒非空，非空门槛自然失效，同时关闭单个或全部分段都能正确表达。

## 期待的新实现情况

buildSummarySectionOverrides 改为：

- 遍历 legacyPromptSections 的四个默认分段
- 对每个分段从界面 state 找到同 id 项，取 enabled、规范化后的 title、规范化后的 instruction
- 全部输出为 SummarySectionOverride，不再有 null 过滤分支

规范化规则统一为：

- title 与 instruction 去首尾空白
- instruction 为空字符串时视为未填写，生成时不输出该段内容只输出标题
- 默认指令文本的提取方式保持 trimEnd，与保存侧一致，避免同一字符串在不同函数下规范化结果不同

resolveSummarySections 相应改为按 id 直接取 override 的三个字段，不再回退默认值合并逻辑，overrides 为空时直接返回默认分段列表，保证首次进入界面时显示默认。

buildSummarySystemPrompt 与 applySummarySectionOverrides：

- applySummarySectionOverrides 保持现有渲染逻辑，即 enabled 为 false 跳过、title 与 instruction 缺失时用默认段块
- buildSummarySystemPrompt 中 sectionOverrides 非空的判断保留，全量持久化后非空恒成立，语义等价

保留行为：

- 未做过任何编辑的用户，全量持久化首次落盘后生成的提示词与迁移前的默认模板逐字一致，这一点需要单测守住

## 细化作用域

- 修改 core/config/FunctionalPrompts.kt 的 buildSummarySectionOverrides 与 resolveSummarySections，必要时抽出共享的规范化函数
- 扩充 test/core/config/FunctionalPromptsSummaryTest.kt，覆盖仅关闭不改文、仅改标题、仅改指令、全默认往返、空白差异不被丢弃、关闭全部分段六种用例

## 调试注释要求

在 buildSummarySectionOverrides 处写注释，说明历史上因差异持久化与非空门槛导致用户修改被丢弃，现在改为全量持久化，以及规范化规则为何必须两侧一致。

## 实际落地记录

- buildSummarySectionOverrides 改为对四个默认分段全量输出：enabled 恒写入界面值；title 去首尾空白后，非空且与默认不同才写入，否则写 null；instruction 同样规范化，与默认一致写 null，用户清空得到的空串原样持久化
- resolveSummarySections 的 instruction 由"空白回退默认"改为"null 才回退默认"，空串在界面回显中保持为空，用户清空指令后重开设置页不会看到默认指令又被填回
- applySummarySectionOverrides 的渲染语义随之明确：title 空表示跟随默认标题；instruction 为 null 跟随默认指令，为空串时只输出标题不输出指令正文；title 与 instruction 均为 null 的分段直接拼接模板原文
- 未编辑分段的 title 与 instruction 全为 null，走模板原文分支，因此全量落盘后默认提示词与模板逐字一致，有单测守住
- buildSummarySystemPrompt 的非空判断保留，全量持久化后配置过的用户恒非空，无配置用户仍走默认模板
- FunctionalPromptsSummaryTest 扩充到 12 个用例：全量持久化结构、关闭后不被后续编辑带回、清空指令被保留且渲染只剩标题、纯空白编辑归一、默认覆盖逐字一致、关闭全部分段、序列化解析再序列化幂等、空指令界面回显、全局规则拼接顺序
- 已知遗留：ContextSummarySectionsAutoSaveEffect 所在的旧设置页仍按字段分片写模型配置，存在读改写竞态；04 步把界面整体改写到角色卡或全局默认的一次性写入后，该竞态随旧写入路径一起消失
- GetDiagnostics 对 FunctionalPrompts 与测试文件零告警

[DONE]
