# 语音服务独立档案

## 存储与启动顺序

TTS 和 STT 档案保存在独立的 `speech_service_profiles` Preferences DataStore。每个档案包含稳定 ID、名称、创建和更新时间，以及该服务类型的完整参数；同一存储还保存 TTS、STT 的当前档案 ID 和格式版本。

该 DataStore 通过 `recoverablePreferencesDataStore` 声明，并在 `PreferenceStoreCatalog` 中拥有唯一 owner。应用启动时，storage recovery gate 按以下顺序处理它：

1. 对已注册 Preferences DataStore 执行物理预检；物理文件不可解析时先隔离损坏源，再从双恢复槽中选择序号最大的有效快照。
2. 物理预检完成后执行 `SpeechServiceProfilesPreferences.initializeAndRepair()`。
3. 格式版本尚未建立时，先规范化已发布版本的 `speech_services_preferences`，再把旧 TTS/STT 单配置一次性导入新存储。
4. 对新存储执行逻辑校验和修复，最后为已验证状态更新恢复槽。

档案 Flow、工厂和界面不承担迁移写入。它们只在 recovery gate 建立存储不变量后读取数据。

## 已发布版本迁移

已发布版本的单配置位于 `speech_services_preferences` DataStore。启动 recovery gate 发现新存储格式版本尚未建立时，会先修复旧存储中可识别的无效字段，然后读取一次迁移种子：

- 旧 TTS 配置生成固定 ID 的 TTS 档案，保留服务类型、HTTP/VITS 参数、清理规则、语速和音调。
- 旧 STT 配置生成固定 ID 的 STT 档案，保留服务类型、端点、密钥和模型。
- 没有已发布配置的新安装会生成有效的默认 TTS/STT 档案。
- 档案列表、当前档案 ID 和格式版本在新 DataStore 的同一次原子更新中提交。

格式版本提交后不会再次读取旧配置，也不会把新档案反向写入旧 DataStore。`speech_service_profiles` 是运行时唯一事实源；TTS/STT 工厂、Provider 默认语速与音调、设置页、聊天朗读、悬浮窗和内置软件设置工具都从当前档案取得配置。

原始快照备份已经包含整个应用的 `datastore/` 目录，因此新的档案存储会随现有备份自动导出和恢复。

正常的新安装初始化和一次性迁移属于状态建立，不记录为逻辑损坏，不创建逻辑 quarantine，也不增加 corruption 修复计数。只有实际发现损坏字段时才进入损坏修复路径。

## 自愈规则

物理恢复和逻辑修复分两层执行：

- 物理 protobuf 损坏时，先把原始文件复制到 quarantine，再验证双槽 envelope、序号和 SHA-256，恢复最新有效快照。没有有效物理槽时建立空 schema，由后续逻辑修复重建必需域。
- 逻辑修复在 DataStore actor 内计算完整结果，先对结果再次验证是否收敛，再隔离修复前的原始 Preferences 状态，最后通过一次原子更新提交。
- `tts_profiles` 或 `stt_profiles` 的根值无法解析为 JSON 数组时，只重建对应 TTS 或 STT 域，不清空另一个可用域。
- 根数组合法时逐项处理。能够识别的档案会被保留并规范化；无法救回的单项不会阻止其他合法档案恢复。
- 重复或空 ID、无效服务类型、损坏 HTTP/VITS 配置、无效响应管线、无效清理正则、非有限或越界的语速和音调，以及悬空当前档案 ID 都会被修正到一致状态。
- 未知的 Preferences key、档案字段及嵌套配置字段在修复和正常更新时保留，避免旧版本删除未来版本数据。
- 修复必须幂等；同一状态第二次运行不得继续产生修改或重复 corruption 事件。

## 生命周期约束

- 创建档案时复制当前档案的参数，并分配新的 UUID。
- 更新档案保留 ID 和创建时间，只修改内容和更新时间。
- 当前档案不能删除；其他档案可以删除。
- 当前档案 ID 始终指向同类现存档案；损坏或空域由启动修复建立有效档案后才允许运行时读取。

## 设置页交互

设置页将档案管理与参数表单分离：顶部管理卡片分别列出当前 TTS/STT 档案，使用整行 Surface 选择档案，提供新建和重命名入口，并在档案菜单中删除非当前档案。参数表单使用 `LazyColumn` 承载，保存异常通过 Snackbar 呈现，保持 Provider 专属字段的原有编辑逻辑。
