# 存储自愈与独占访问

本文记录 Operit 已发布持久化接口的所有权、启动校验和恢复约束。新增持久化实现或修改数据救援功能时，必须同时维护本文与 `ci/script/check_storage_ownership.py`。

## 覆盖范围

自动自愈覆盖以下数据库与数据库式配置存储：

- `PreferenceStoreCatalog` 登记的 24 个 Preferences DataStore owner，其中 23 个配置存储执行自动恢复
- Room 数据库 `app_database`
- 默认 ObjectBox 数据库 `files/objectbox/`
- 每个记忆空间独立的 ObjectBox 数据库 `files/objectbox_<profileId>/`
- API、模型、功能模型映射、记忆空间、角色卡、角色群组、TTS 和 STT 的逻辑状态

SharedPreferences、缓存、插件包、聊天导出文件和其他用户可编辑文件不执行自动文件替换。SharedPreferences 与结构化私有文件仍由原有读写方维护，并包含在原始快照功能中；它们不是本机制所称的数据库。没有明确 schema 和单一所有者的文件不得接入自动恢复，否则一次解析失败可能覆盖用户主动编辑的内容。

## 稳定接口

该机制在已发布版本之后加入，以下接口不得改名或迁移：

- 24 个 DataStore 文件名及其 Preferences key
- Room 名称 `app_database`
- ObjectBox 默认目录与 `objectbox_<profileId>` 目录
- 修复进程名 `:repair` 和崩溃进程名 `:crash`
- DocumentsProvider authority `${applicationId}.documents.data`
- DocumentsProvider 根 ID、文档 ID、路径和已发布的增删改接口
- Room ZIP 与原始快照的既有入口和 payload 路径

恢复元数据是附加状态，只写入 no-backup storage，不会改变已发布数据文件的位置。快照、事件和隔离数据位于 credential-protected `noBackupFilesDir/storage-recovery/`；跨进程 lease 单独位于 device-protected no-backup storage，避免已发布的 `Operit Data` Provider 删除锁文件 inode 后绕过互斥。

## 目录布局

```text
device-protected-no-backup/storage-recovery/
└── storage.lock

credential-protected-no-backup/storage-recovery/
├── events.json
├── preferences/
│   ├── <store>.0.json
│   └── <store>.1.json
├── room/
│   ├── app_database.0.db
│   ├── app_database.0.json
│   ├── app_database.1.db
│   └── app_database.1.json
├── objectbox/
│   └── <safe-profile-id>_<hash>/
│       ├── data.0.mdb
│       ├── data.0.json
│       ├── data.1.mdb
│       └── data.1.json
└── quarantine/
    ├── <time>_<store>_physical_<uuid>.preferences_pb
    ├── <time>_<store>_physical_path_<uuid>/
    ├── <time>_<store>_logical_<uuid>.json
    ├── room_<time>_<uuid>/
    ├── objectbox_<profile>_<time>_<uuid>/
    └── recovery_epoch_<time>_<uuid>/
```

隔离区不自动清理。它与应用数据保持相同生命周期，清除应用数据会一并删除。自动删除旧损坏源会让一次错误修复成为不可逆数据丢失，因此只有明确的用户操作或专门的数据救援工具可以清理该目录。目录型损坏源必须逐层显式枚举；任一子目录无法读取或复制时中止替换，不能把跳过部分子树的副本视为已完成保全。

## Preferences DataStore

恢复槽位必须同时通过 envelope 校验与 typed value 完整解码。按序号从新到旧检查，某个槽位无法解码时继续检查另一槽位；只有可以构造完整 `Preferences` 的快照才允许替换 live 文件。

所有 DataStore 必须通过统一注册器声明。23 个配置存储使用 `recoverablePreferencesDataStore`，注册器为其配置 `ReplaceFileCorruptionHandler` 和恢复槽；`token_stats_preferences` 使用 `managedPreferencesDataStore`，只复用 actor 独占、关闭和重绑能力。

调用方拿到的是进程内稳定的 DataStore 代理，而不是当前 actor 本身。原始快照替换会取消并等待旧 actor 退出；替换完成后的首次读取或写入由代理绑定新 actor，避免单例 manager 永久保存已经关闭的 DataStore。已经开始收集的长期 Flow 属于旧存储 epoch，原始快照恢复成功后必须按既有界面流程重启进程，不允许旧 collection 跨 epoch 继续消费。

每个快照是 typed JSON envelope。payload 保存格式版本、DataStore 名称、单调序号、创建时间和按 key 排序的类型化值；envelope 保存 payload 的 SHA-256。两个 slot 按序号交替写入，并通过 `AtomicFile` 发布。String Set 写入前排序，保证校验内容稳定。

更新提交与快照写入使用同一个 per-store mutex。这个约束不能移除，否则两个并发提交可能按相反顺序发布快照，使旧状态获得更大的恢复序号。关闭 DataStore 以执行原始快照或文件替换时，必须调用 `closeAllAndAwait`，等待 actor 退出后才能复制文件。

物理 protobuf 无法解析时，corruption handler 先复制原文件到隔离区，再读取两个 slot 中序号最大的有效快照。损坏源复制失败时中止自动替换，不能在未保全原文件的情况下发布恢复结果。已存在恢复 slot 但 live Preferences 文件缺失时，首次打开通过 DataStore migration 恢复快照，不在 actor 外直接生成 protobuf。live 文件路径被目录占位时，先把目录树递归复制到 `physical_path` 隔离目录，保全成功后删除占位目录，再通过同一 migration 恢复快照或创建空 schema；恢复事件只能在 migration 提交后记录一次。两个 slot 都无法验证时，DataStore 创建空 schema 状态；原始坏文件或目录仍保留在隔离区，后续逻辑修复负责恢复必需默认项。

Room 与 ObjectBox 的物理预检必须先于记忆空间、角色及其他逻辑索引修复。暂时不可读但可以从 slot 恢复的 ObjectBox profile 不能被逻辑层误判为已删除，否则数据库恢复后引用已经丢失。

逻辑修复通过 `repairPreferenceState` 在 DataStore actor 内提交。修复函数必须幂等，在提交前对计算出的结果再次运行验证；仍有问题时拒绝提交。原始逻辑状态先写入隔离区，事件只记录修复 key 数量，不记录 key 名和值。

逻辑配置采用可识别字段校验。有效 JSON 不因空白、字段顺序或当前版本不认识的附加字段而重新编码；只有解析失败或已知字段的语义规范化确实改变状态时才写回，避免旧版本启动时删除新版本字段。已知字段确实需要修正时，修复器按原始 JSON 与当前版本可识别字段做差量合并；对象中的未知字段以及未改变数组元素中的未知字段仍保留。TTS HTTP 配置、模型与自定义参数、记忆空间、角色工具权限、角色群组和 SAF 书签遵守该规则。SAF 书签按条目验证，单条坏记录不会清空其他可解码书签。功能模型映射按条目解析，未知的未来功能条目保留原始 JSON；旧版本正常保存、重置已知功能映射时也必须带回这些未知条目。

`speech_service_profiles` 在 Preferences 物理预检完成后、任何语音业务 owner 读取配置前执行启动初始化与逻辑修复。已发布的 `speech_services_preferences` 仅在 `speech_profiles_migration_version` marker 未完成时作为一次性迁移源；marker 提交后，`speech_service_profiles` 是 TTS/STT profile 的唯一运行时数据源，正常初始化不再读取旧 store。首次迁移、创建缺失的 profile 域与无需改动的正常初始化都不上报 corruption 事件。可解码的 Preferences 中出现 profile 逻辑缺陷时不上报 protobuf corruption：profile JSON 根无法解析或不是数组时重建对应 TTS/STT 域；根数组合法时逐项解析并修正已知字段，不因单个 profile 损坏而删除其他条目。需要写回的条目按原始 JSON 差量合并，保留 profile、HTTP/VITS 子对象与 pipeline step 中未知的未来字段。

`token_stats_preferences` 保存统计页标量配置，但不属于自动自愈范围。它不参加启动预检、逻辑修复、物理 corruption replacement 或双槽 checkpoint。上游已经明确统计账本从版本 21 开始建立，不导入或解释 `api_settings` 中的历史 token、价格和汇率键。它仍由统一注册器管理，以便原始快照替换前关闭 actor，并在替换后重新绑定。

模型配置、TTS/STT、GitHub OAuth、外部 HTTP 和权限设置按字段修复。一个非敏感字段类型错误不得清空同一记录中仍有效的 API key、key pool、请求头、endpoint、access token、refresh token、OAuth delivery credential、bearer token 或自定义 `su` 命令。只有无法解析为 JSON 对象的整条原文才重建最小记录，重建前必须保留逻辑隔离副本。记忆空间删除只接受值为 Boolean `true` 的持久化标记；`false` 和类型错误的标记会被隔离并移除，不能转换为删除授权。

模型、角色、角色群组和记忆空间的稳定记录 ID 优先保留。单条结构化记录无法解码时，损坏原文保留在隔离区，live 状态以同一 ID 重建最小可用记录；索引独有但记录缺失的角色和群组也按同一规则重建，避免一次局部损坏把整个配置及其引用删除。

## Room

手动 Room 恢复从关闭 singleton 到完成文件替换始终持有 `AppDatabase` 的 singleton 监视器，后台 repository 无法在关闭与替换之间重新打开数据库。

Room 在 singleton 构建前执行以下校验：

1. 在 Room 尚未打开时执行 `PRAGMA wal_checkpoint(FULL)`。
2. 执行 `PRAGMA quick_check`，验证 SQLite 页和索引结构。
3. 读取 `PRAGMA user_version`。版本大于当前应用时保留 live 数据并进入数据救援，不执行替换。
4. 将关闭状态的数据库复制到隔离文件名，删除隔离副本的 `room_master_table`，再通过真实 Room builder 强制完成迁移和实际表、列、外键、索引 schema 校验，不能只信 identity hash。
5. 只有全部校验通过的关闭状态数据库才能写入双 slot 快照。

直接打开 live 和恢复槽执行 SQLite 校验时，必须显式安装只记录损坏信号、绝不删除文件的 `DatabaseErrorHandler`。Android 默认损坏处理器会在 `openDatabase` 返回前删除数据库，使损坏源无法隔离，并可能把最终异常改变为文件不存在。只要本次打开收到损坏信号，即使顶层异常类型随后发生变化，也必须按确定损坏处理；没有损坏信号的权限、空间、路径和 I/O 异常仍中止自动替换。

恢复前会复制 live DB、WAL、SHM 和 journal 到隔离区。快照的文件长度、SHA-256、SQLite 完整性和 Room schema 都必须通过验证。live 主数据库缺失但存在有效 slot 时也会恢复；只有 sidecar、没有主数据库和有效 slot 时保留 sidecar 并进入数据救援。主数据库路径被目录占位时，目录树和空子目录必须递归保全；存在有效 slot 时才删除占位路径并恢复，没有有效 slot 时保留原路径并进入数据救援。主数据库仍为普通文件、只有 sidecar 被目录占位时，先递归隔离并移除无效 sidecar，再对原主库执行完整性和 schema 校验。损坏或残缺状态没有有效快照时不创建空 Room 数据库，也不覆盖 live 文件。

首次创建 Room 数据库时，singleton 尚未交给调用方。创建路径会强制打开并关闭该实例，发布第一份已验证快照后再重建 singleton，避免首装进程被系统结束时长期没有恢复槽位。

手动 Room 备份先取得进程内 operation permit，checkpoint live WAL，再把 DB、WAL 和 SHM 复制到隔离工作目录。工作副本通过完整性和 schema 校验后才发布 ZIP。恢复 ZIP 先解压到精确文件名并验证，验证成功后才隔离并替换 live 数据库。

## ObjectBox

记忆空间元数据修复会读取仍含 `data.mdb` 的 ObjectBox 目录。ObjectBox 预检还会从有效恢复槽位元数据发现 live 目录已经完全缺失的 profile，先恢复其数据库，再把 profile ID 交给逻辑修复。即使 DataStore 索引或记录同时丢失，这些 profile ID 也会重新进入记忆空间索引并生成最小元数据。关闭 profile 后以及 live store 打开或全页校验失败时都会清除进程内 preflight 标记，下一次打开必须重新校验。

ObjectBox 按记忆空间分别校验和恢复。打开 live store 之前先复制 `data.mdb` 到缓存目录，通过 `BoxStore.validate(0L, true)` 校验全部页。异常本身或 cause 链中的 `FileCorruptException` 表示数据库页损坏；ObjectBox 5.3.0 以普通 `DbException` 暴露的 `MDBX_PAGE_NOTFOUND`、`MDBX_CORRUPTED` 和 `MDBX_INVALID` 也属于确定的存储内容损坏。分类必须使用结构化错误码，禁止匹配异常文字。版本不兼容、锁冲突、权限、空间不足、I/O 和未知错误会保留 live 文件并终止自动替换。

两个 `data.mdb` slot 都带格式版本、profile ID、序号、文件长度和 SHA-256。每个 slot 在发布后还会通过临时 BoxStore 再验证一次。恢复只影响损坏的 profile；`data.mdb` 缺失、被目录占位或 ObjectBox 数据库目录本身被文件占位时，只有存在有效 slot 才在递归隔离原路径后恢复。有效 `data.mdb` 旁的 `lock.mdb` 被目录占位时，先递归隔离整个 profile，再移除无效 lock 路径并继续全页校验。只有 `lock.mdb` 或其他残片且没有有效 slot 时，保全目录并进入数据救援，不创建空 ObjectBox 数据库。用户主动删除记忆空间时，恢复 slot 先原子移入 deletion quarantine 并退出自动发现，再删除 live 目录；任一步失败都向调用方报错。即使 live 删除失败，旧 slot 也只保留作人工救援，不能在下次启动复活用户已选择删除的记忆空间。

记忆空间删除在 Preferences 中先提交持久化删除标记。每次启动都在 ObjectBox 恢复槽发现之前重放这些标记，依次清除角色绑定、用户文档、live ObjectBox、恢复槽和元数据，最后才移除标记。中途进程退出时下一次启动继续同一删除操作。默认 profile、空 ID、路径分隔符、NUL、超长 ID 及其他不能解析为 `filesDir` 直属目录的 profile ID 不参与自动打开；对应 live 目录先严格隔离，再从自动发现范围移除。

ObjectBox 恢复快照、文件替换和删除只能在 store 关闭后执行，并必须通过 `ObjectBoxManager` 管理 store 生命周期。`:repair` 原始快照路径同样先关闭 store。已发布的主进程原始快照导出入口保持可用：它持有主进程 storage lease，先 checkpoint Preferences 与 Room，并保留已有 repository 持有的 DAO 和 BoxStore，避免一次导出使当前会话中的数据库对象永久失效。

正常运行中的 ObjectBox 不直接复制活动 `data.mdb`。变更订阅在最后一次写入后延迟 15 秒，通过 store 事务生成稳定 staging copy；staging copy 完成全页验证后才发布双 slot。checkpoint 失败会按指数间隔重试，最长 15 分钟，不阻塞已经成功的业务事务。关闭 store 时仍执行一次关闭态 checkpoint，确保进程可控退出和恢复替换前拥有最新的已验证副本。

原始快照格式 2 的 manifest 按路径排序登记每个 regular file 的相对 ZIP 路径、长度和 SHA-256，并显式登记五个必需 payload 目录。恢复在覆盖 live 目录前拒绝缺失或未登记文件、重复 entry、路径类型错误、长度或哈希不一致，以及无效 Room/ObjectBox 数据库。已发布的格式 1 继续可读，但只具备 ZIP central directory、CRC、长度、固定 includes 和 Room/ObjectBox 校验；它没有逐文件加密清单，导入前的完整 live 隔离是其兼容性边界。ObjectBox 验证生成的 `lock.mdb` 不进入导入目录；缺少 `data.mdb` 的 ObjectBox 目录、被普通文件占位的 ObjectBox 目录、被目录占位的 Room 主库或 sidecar，以及缺少主数据库的 Room sidecar 都会拒绝导入。ZIP、全部数据库和替换前置条件完成验证之前，不关闭主进程已有 owner。

owner 关闭后、第一次删除 live entry 前，恢复流程严格枚举并复制 `files`、external files、`shared_prefs`、`datastore` 和 `databases` 的完整旧状态到 no-backup 隔离区，再校验目录拓扑、文件长度和 SHA-256。保全失败时不修改 live 数据。随后先持久化 `PREPARED` 事务标记并归档原恢复槽，在第一次 live 删除前提交 `MUTATION_STARTED`；导入状态完成恢复和 owner 关闭后才提交 `COMMITTED` 并移除标记。启动恢复在打开任何 Preferences、Room 或 ObjectBox owner 前处理残留标记，将五个 live 类别和原恢复槽恢复到同一旧 epoch。回滚失败会与原始异常一并报告，事务标记和隔离副本不会自动删除，供数据救援继续处理。

进程内 recovery gate 同时覆盖启动恢复和原始快照替换。它原子阻止普通调用方重开 DataStore、Room 和 ObjectBox，授权恢复协程通过显式上下文权限跨 IO 与 Main dispatcher 工作。原始快照真正进入替换阶段后，已经开始的 Preferences actor 会被取消并等待退出，ObjectBox owner 必须严格关闭并完成关闭态 checkpoint；任一 owner 无法关闭时不覆盖 live 文件。角色卡与角色群组的逻辑修复每次重新取得当前 DataStore actor，不能复用关闭前的引用。目录替换完成后，导入前一代的 Preferences、Room 和 ObjectBox 恢复 slot 会整体移入隔离区，再为导入状态建立新 slot，禁止旧快照覆盖主动恢复的数据。原始快照的 gate 无论替换成功或失败都保持到 owner 最终清理完成后才释放。

## 进程所有权

主进程在 `Application.attachBaseContext` 取得 device-protected `storage.lock` 并激活 recovery gate，早于 Android 安装并发布 ContentProvider，也早于任何 Preferences、Room 或 ObjectBox consumer。`Application.onCreate` 复用该 lease 和 gate 执行同步恢复，并在成功进入 `READY` 后才放行普通 owner；恢复失败时 gate 保持关闭状态，直到主进程退出。Memory DocumentsProvider 仍按原 authority 发布，只有不读取存储的根信息可在启动窗口返回；任何读取用户偏好或 ObjectBox 的调用都要求 `READY` 且主进程仍持有 storage lease。进程退出时内核释放 lock；`onTerminate` 只承担可控测试环境中的显式关闭。

`:repair` 进程不初始化主进程 preference manager。SQL、Room 备份与恢复、原始快照和 DocumentsProvider 写操作必须获取同一 file lock。跨进程由 OS file lock 排他；同一进程已经持有主 lease 时，另由可跨协程线程释放的 operation permit 串行化这些操作。原始快照管理器只接受持有全局 lease 的主进程或 `:repair` 进程，并自行持有 operation lease，调用方不能用外部约定代替。写描述符的 lease 一直保留到 `ParcelFileDescriptor` 的 close listener 执行。

DocumentsProvider 的 `r` 模式保持已发布行为，可以在主进程运行时读取。其他 descriptor 模式以及 create、delete、rename、move 在主进程持锁时返回 storage busy。Provider 仍发布原有写 flags，调用方无需适配新的文档 ID 或路径。

## 启动结果

主进程同步恢复结束后只会进入以下状态之一：

- `READY`：物理校验、逻辑修复、Room schema 校验和 ObjectBox preflight 全部完成
- `BUSY`：另一个进程持有 storage lock
- `RECOVERY_REQUIRED`：数据保留，但没有可验证的自动恢复结果
- `NON_MAIN_PROCESS`：`:repair` 或 `:crash`，不运行主应用初始化

`MainActivity` 只在 `READY` 时初始化业务模块。其他状态启动现有 `DataRecoveryActivity`，随后终止主进程，为 `:repair` 提供独占窗口。前台聊天服务、悬浮窗服务、Room 备份 Worker 和工作流 Worker 同样只在 `READY` 时进入业务；它们不能在恢复失败后重新创建持久化 owner。持久化语言在 storage ready 之前不会读取，基础 Context 暂时使用系统语言。

启动恢复失败时会取消并等待已经创建的 Preferences actor，释放主进程 storage lease，并保留关闭的 recovery gate 直到进程退出。这样 `DataRecoveryActivity` 所在的 `:repair` 进程可以取得独占 lease，同时失败的主进程不能在路由恢复界面期间重新打开数据库。

## 性能与空间代价

启动会读取 23 个可恢复 Preferences 文件、校验 Room 页与真实 Room schema，并对每个已发现 ObjectBox profile 执行全页验证。`token_stats_preferences` 不参加该预检。数据库越大，首次启动校验越久；该成本是阻止损坏数据进入业务 owner 的必要边界，不能通过跳过页或只检查 metadata 缩短。

每个可恢复 Preferences、Room 和 ObjectBox 存储保留两个恢复 slot。`token_stats_preferences` 没有 slot。稳定状态的额外空间大约是两份可恢复 payload，加上不自动清理的损坏源隔离副本。ObjectBox 活库 checkpoint 在 cache 中短暂再占用一份 `data.mdb`；设备空间不足时 checkpoint 失败会保留 live 数据并记录日志，不会把未完成副本发布为恢复 slot。

逻辑修复只在检测到已知字段损坏时写入，并在同一 actor 内验证幂等性。物理快照会产生额外顺序 I/O；调用方不得为了减少 I/O 绕过 catalog、直接复制活库，或删除隔离区中的唯一损坏源。

## 事件与诊断

`events.json` 最多保留 100 条事件。事件字段只包含 UUID、时间、storage、kind、action 和异常类名，不写 API key、token、用户文本、Preferences key 名或数据库内容。

排查顺序如下：

1. 查看 `StorageRecovery`、`PreferenceRecovery`、`RoomRecovery`、`ObjectBoxRecovery` 和 `StorageProcessLock` 日志。
2. 查看 `events.json` 的最新 action，确认是恢复、保留还是版本拒绝。
3. 检查对应 slot 的 metadata、长度和 SHA-256，不要直接编辑 slot。
4. 从 `quarantine/` 复制损坏源进行离线分析，不要在 live 目录上运行修复工具。
5. 若事件为 `newer_version_preserved`，使用能识别该 schema 的新版本应用，禁止用旧 slot 覆盖。

识别出物理损坏但没有有效槽位时，必须同时看到 `preserved_without_snapshot` 事件和对应隔离目录。缺少其中任一项都表示恢复流程在保全边界之前异常退出，不能把它解释为正常的数据救援结果。

### 全存储冷启动破坏清单

这些用例只允许在可丢弃的测试账号或已完整备份的设备上执行。先正常启动并修改一次对应数据，确认两个 slot 都存在且可以独立通过长度、SHA-256 和数据库内容校验。每个用例开始前强制停止应用，注入期间不得让主进程或 `:repair` 进程存活。启动后同时保存 logcat、`events.json`、live 与 slot 的文件列表及 SHA-256、隔离目录和前台 Activity；不要只根据界面是否打开判定结果。

1. **D01 Preferences live 损坏**：用固定 ASCII 内容覆盖任一非空 live `files/datastore/<store>.preferences_pb`，保留两个有效 slot。冷启动应隔离原字节，选择序号最大的有效 slot，记录 `physical_corruption/restored_snapshot`，并进入主界面。对 `api_settings`、角色、角色群组、用户偏好和 `speech_service_profiles` 至少各执行一次；`token_stats_preferences` 不适用。
2. **R01 Room live 损坏**：用固定 ASCII 内容覆盖 `databases/app_database`，保留双 slot。冷启动应先产生与注入内容字节一致的 Room quarantine，再从有效 slot 写回 live，记录 `room_corruption/restored_snapshot`，最终进入主界面。日志中不得出现对缺失 live 路径反复执行 `checkpointAndValidate`。
3. **R02 Room live 缺失**：删除测试副本中的 `app_database`、WAL、SHM 和 journal，保留双 slot。冷启动应记录 `room_missing/restored_snapshot`，恢复数据库并进入主界面。
4. **F03 Room 最新 slot 损坏**：按 metadata 的 `sequence` 找到最新 slot，改坏其数据库文件并同时改坏 live，保留另一个有效 slot。冷启动应拒绝最新 slot，选择旧的有效 slot，保留损坏 slot 供诊断，隔离损坏 live，并进入主界面。
5. **O01 ObjectBox live 损坏**：用固定 ASCII 内容覆盖 `files/objectbox/data.mdb`，保留双 slot。冷启动应把 MDBX `-30793` 识别为内容损坏，隔离原字节，从有效 slot 恢复 marker 数据，记录 `objectbox_corruption/restored_snapshot`，并进入主界面。
6. **O02 ObjectBox 最新 slot 损坏**：按 metadata 的 `sequence` 找到最新 `data.<slot>.mdb`，改坏该文件与 live `data.mdb`，保留另一个有效 slot。冷启动应拒绝最新 slot，选择旧的有效 slot，恢复 marker，保留损坏 slot，并隔离损坏 live。
7. **M01 混合损坏**：在同一次强制停止窗口内分别破坏一个 Preferences live、Room live 和默认 ObjectBox live，三个存储均保留有效 slot。冷启动应分别完成三个存储的隔离与恢复，三个事件都存在，任何单个 validator 都不得提前终止其他存储的可恢复路径，最终进入主界面。
8. **F01 无有效槽的数据救援**：先把 Room 或 ObjectBox 的两个 slot 及 metadata 移到测试 hold 目录，再破坏 live。冷启动应保留原 live，生成字节一致的 quarantine，记录 `preserved_without_snapshot`，进入 `DataRecoveryActivity`，且不得创建空数据库。测试完成后只把 hold 中的文件恢复到原位置，不把已损坏 live 当成新基线。

以上物理用例完成后，再正常修改一次对应业务数据并等待快照发布，确认两个 slot 的 sequence 继续递增。这样可以验证恢复后的 live 重新进入正常快照轮换，而不是只完成一次性启动。

### `speech_service_profiles` 手工破坏检查

以已完成一次正常启动、两个恢复槽已生成的测试账号为基线。每次注入前强制停止应用，注入后启动主进程，检查 live Preferences、两个 slot、隔离副本和 `events.json`：

1. **protobuf 物理损坏**：把 live `speech_service_profiles.preferences_pb` 改成无法解码的字节。启动应在逻辑修复前触发 corruption handler，隔离原文件，用序号最大的有效 slot 恢复 live，随后 profile 初始化正常完成。
2. **profile 根 JSON 损坏**：保持 protobuf 可解码，把 `tts_profiles` 或 `stt_profiles` 改为非法 JSON 或非数组根。启动应记录 logical corruption，重建仅对应的 profile 域并持久化有效当前 ID，不记录 protobuf corruption。
3. **单字段损坏**：在合法 profile 数组中把一个已知枚举、数值或 pipeline step 字段改成非法值，同时添加未知字段。启动应只修正损坏条目的已知字段，其他 profile 不变，所有未知字段仍保留；再次启动不应产生新的逻辑修复。
4. **悬空当前 ID**：把 `current_tts_profile_id` 或 `current_stt_profile_id` 改为数组中不存在的 ID。启动应在同一次原子修复中将当前 ID 指向实际存在的 profile，且业务 owner 不得观察到悬空状态。
5. **双 slot 损坏**：同时改坏 `speech_service_profiles` 的两个 envelope 或 payload 校验值，再破坏 live protobuf。启动应隔离 live 损坏源、创建空 schema，然后由 profile 逻辑修复建立最小可用域；不得把无法验证的 slot 复制回 live。
6. **新版本 marker 保留**：在可解码 live 中把 `speech_profiles_migration_version` 设为高于当前实现的整数，并保留一个未知 profile 字段。启动应在任何写入前报告格式不兼容并进入数据救援，保留 live、marker 和 profile JSON 原值，也不执行旧 store 迁移。

## 协作检查

PR 快速检查会执行：

```text
python3 ci/script/check_storage_ownership.py
```

该脚本拒绝直接或别名导入的 `preferencesDataStore`、注册器外 `PreferenceDataStoreFactory.create`、未登记文件名、缺失 owner、重复 owner，以及把 token 之外的 store 设为 managed-only。脚本自身测试位于 `ci/test/test_storage_ownership.py`。

Android 故障夹具与幂等修复断言位于 `app/src/androidTest/java/com/ai/assistance/operit/data/persistence/`。其中覆盖 23 个 recoverable Preferences、managed-only token actor、Room、ObjectBox、TTS、模型、角色、记忆空间和跨进程 Provider 写锁。按照仓库执行准则，只有在用户明确授权后才运行 Gradle 编译或测试。
