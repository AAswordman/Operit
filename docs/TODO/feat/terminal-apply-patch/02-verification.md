# apply_patch 回归清单

以下清单覆盖保留的全部场景及新增安装路径。C/R/I/A/P 项由仓库工作流执行；S 项为可选实机冒烟，不阻塞缺少对应设备条件时按工作流落地。原版调用验证器专用的“验证后替换路径”用例未加入独立 CLI，直接引擎符号链接测试保留。

## 做什么与预期行为

| 编号 | 操作 | 预期行为 |
| --- | --- | --- |
| C01 | 新增文件 | 创建文件并补齐末尾换行 |
| C02 | 同一补丁新增、修改、删除 | 各项按原版执行并输出摘要 |
| C03 | 多段更新 | 各上下文段替换正确 |
| C04 | 移动到新目录 | 创建目标目录并移除源文件 |
| C05 | 空补丁 | 报错且文件不变 |
| C06 | 缺少上下文 | 报错且文件不变 |
| C07 | 删除缺失文件 | 报错并保留其他文件 |
| C08 | 空更新段 | 保留原版解析结果 |
| C09 | 更新缺失文件 | 报错且不创建目标 |
| C10 | 移动覆盖目标 | 目标使用修改后的内容 |
| C11 | 新增覆盖已有文件 | 保留原版覆盖行为 |
| C12 | 删除目录 | 报错且目录内容保留 |
| C13 | 无效操作头 | 报错且文件不变 |
| C14 | 更新缺少末尾换行的文件 | 输出包含末尾换行 |
| C15 | 前项成功、后项失败 | 保留前项已写入结果 |
| C16 | 只有新增行的更新段 | 插入位置与原版一致 |
| C17 | 带空白的操作头 | 保留原版解析结果 |
| C18 | 带空白的补丁标记 | 保留原版解析结果 |
| C19 | Unicode 文本 | 正确修改 UTF-8 内容 |
| C20 | 删除已有文件 | 只删除指定目标 |
| C21 | 标记行带空白 | 保留原版解析结果 |
| C22 | 只删除行的更新段 | 保留剩余文本 |
| C23 | 文件尾标记 | 在指定文件尾上下文更新 |
| C24 | CRLF 文件 | 保留目标 CRLF |
| C25 | 混合换行 | 保留未修改行的换行类型 |
| R01 | 解析器、流式解析器、文本/上下文匹配单元回归 | 原版规则和错误信息一致 |
| R02 | 命令参数、stdin、综合文件操作回归 | 输出、换行、失败结果一致 |
| R03 | 路径工具工作区回归 | 本地路径解析与归一化一致 |
| R04 | 符号链接直接引擎回归 | 默认跟随链接；禁用时拒绝叶子及父路径链接 |
| I01 | 首次安装及 PATH 查找 | 安装为 0755；命令可定位，stdin 修改成功 |
| I02 | 重复安装同版本 | 不替换已校验文件的 inode |
| I03 | 已安装副本损坏 | 原子替换为 APK 对应版本 |
| I04 | 来源校验失败 | 返回失败，已有副本内容不变 |
| I05 | 安装目标是符号链接 | 替换链接，链接原目标不变 |
| I06 | 多个会话并发安装 | 全部完成且不留下暂存文件 |
| I07 | 资源打包及篡改 | 有效资源可核对；篡改二进制立即报错 |
| I08 | ARM64 退出码和绝对路径 | 返回 0/1/2；绝对路径修改正确 |
| I09 | ARM64 执行 C01–C25 全部场景 | 实际文件快照与原版 fixture 一致 |
| A01 | Android 资源安装及重复使用 | 写入校验正确的资源，相同版本不重复提取 |
| A02 | Android 资源升级校验失败 | 原副本保留且清理暂存文件 |
| A03 | Android 资源目标是符号链接 | 只替换链接本身，不修改原目标 |
| P01 | Gradle 资源合并与最终 APK 校验 | 二进制、脚本、清单及许可全部匹配 |
| P02 | CI 改动范围判定 | Rust、锁文件、安装器及复用步骤触发完整检查 |
| S01 | 更新已有 Ubuntu 后新开普通会话 | 无需重装环境，apply_patch 可直接执行 |
| S02 | 隐藏工具执行 | 安装完成后再执行 apply_patch，退出码正确 |
| S03 | 新装或重装 Ubuntu | 首次登录自动安装命令 |
| S04 | 真实 chroot 安装后切换 proot | 命令可执行且后续资源更新权限正常 |

## 实际执行状态

本地已完成 Rust 代码/字面量及 fixture 对照、依赖锁定、Python/TOML/YAML 解析、Bash 语法和 Git 差异检查。本地未运行编译、构建和测试。工作流已接入所列自动回归；实际通过情况以对应提交的工作流报告为准。

## 可填写结果表

| 编号 | 结果 | 证据或备注 |
| --- | --- | --- |
| C01 | 待确认 | |
| C02 | 待确认 | |
| C03 | 待确认 | |
| C04 | 待确认 | |
| C05 | 待确认 | |
| C06 | 待确认 | |
| C07 | 待确认 | |
| C08 | 待确认 | |
| C09 | 待确认 | |
| C10 | 待确认 | |
| C11 | 待确认 | |
| C12 | 待确认 | |
| C13 | 待确认 | |
| C14 | 待确认 | |
| C15 | 待确认 | |
| C16 | 待确认 | |
| C17 | 待确认 | |
| C18 | 待确认 | |
| C19 | 待确认 | |
| C20 | 待确认 | |
| C21 | 待确认 | |
| C22 | 待确认 | |
| C23 | 待确认 | |
| C24 | 待确认 | |
| C25 | 待确认 | |
| R01 | 待确认 | |
| R02 | 待确认 | |
| R03 | 待确认 | |
| R04 | 待确认 | |
| I01 | 待确认 | |
| I02 | 待确认 | |
| I03 | 待确认 | |
| I04 | 待确认 | |
| I05 | 待确认 | |
| I06 | 待确认 | |
| I07 | 待确认 | |
| I08 | 待确认 | |
| I09 | 待确认 | |
| A01 | 待确认 | |
| A02 | 待确认 | |
| A03 | 待确认 | |
| P01 | 待确认 | |
| P02 | 待确认 | |
| S01 | 待确认 | |
| S02 | 待确认 | |
| S03 | 待确认 | |
| S04 | 待确认 | |

## 命令行扩展完整回归

以上原有 C/R/I/A/P/S 清单全部保留。新增项目随同一工作流运行，所有项目在确认结果前保持待定。全部 25 个场景同时比较 `--check`、`--diff` 和实际应用退出状态，检查前后原目录快照必须相同。

| 用例 | 操作与预期 | 结果 |
| --- | --- | --- |
| `version_and_help_do_not_read_input_or_modify_files` | 版本/帮助各别名返回 0，不读 stdin 或创建文件 | 待 CI |
| `help_and_version_support_json_output` | 版本/帮助也返回单个 JSON 对象 | 待 CI |
| `file_input_supports_short_long_equal_and_non_utf8_paths` | 文件输入各写法、特殊字符及非 UTF-8 路径正常 | 待 CI |
| `stdin_and_end_of_options_preserve_original_patch_input` | 保留 stdin 及 -- 后补丁参数的行为 | 待 CI |
| `usage_and_input_failures_are_single_json_objects` | 缺参数、冲突、未知选项与 I/O 错误只输出一个 JSON 对象 | 待 CI |
| `file_name_equal_to_json_option_is_not_an_output_flag` | --json 作为 -f 文件名时不切换输出格式 | 待 CI |
| `check_aliases_never_create_directories_or_files` | 三个检查别名都不创建文件或目录 | 待 CI |
| `check_simulates_sequential_add_update_move_and_delete` | 新增、移动、目标继续更新和删除按顺序检查 | 待 CI |
| `failed_check_leaves_all_files_unchanged_even_after_valid_prefix` | 失败前的模拟写入不改变实际文件、inode 或目录 | 待 CI |
| `json_apply_preserves_partial_failure_and_reports_moves` | JSON 应用失败仍保留已写入结果，准确标记部分变更 | 待 CI |
| `check_follows_leaf_and_directory_links_without_modifying_targets` | 符号链接别名共享模拟内容，实际目标不变 | 待 CI |
| `check_hard_link_aliases_share_changes_but_recreated_names_do_not` | 硬链接共享模拟内容，删除重建后独立 | 待 CI |
| `check_rejects_a_file_used_as_a_parent_without_touching_it` | 文件作为父目录时失败且原文件不变 | 待 CI |
| `successful_json_reports_binary_deletions_even_without_text_snapshots` | 成功删除非 UTF-8 内容仍列出 delete 操作 | 待 CI |
| `all_original_scenarios_have_matching_check_status_and_unchanged_inputs` | 全部 25 个原版场景检查/预览/应用状态一致，只读操作不写入 | 待 CI |
| ARM64 扩展入口 | QEMU 执行版本、帮助、文件输入、检查、预览、禁止覆盖、静默与 JSON；25 个场景检查及预览不写入 | 待 CI |

## 实机反馈

维护者已确认旧命令入口的 `Add File` 在 Android 实机可用，仅记录该操作。

| 操作 | 预期 | 结果 |
| --- | --- | --- |
| 原入口 Add File | 正常新增文件 | 维护者实机反馈通过 |
| --version 与 -h | 显示 0.1.0 和帮助 | 待填写 |
| -c -f change.patch | 检查成功且文件未修改 | 待填写 |
| -f change.patch --json | 按补丁修改并输出单个 JSON 对象 | 待填写 |

## 禁止覆盖与预览新增场景

原有 C/R/I/A/P/S 与命令行扩展用例全部保留。下列新增用例交由同一仓库工作流执行，本地未运行测试。

| 用例 | 操作与预期 |
| --- | --- |
| `no_overwrite_rejects_existing_add_targets_in_all_modes` | 三种模式新增已有文件、目录或链接：拒绝覆盖且目标不变 |
| `no_overwrite_refuses_move_destination_and_preserves_source` | 三种模式移动到已有目标：拒绝且保留源和目标 |
| `no_overwrite_allows_updates_and_explicit_delete_then_add` | 更新及显式删除后重建：允许；只读模式不改变磁盘 |
| `no_overwrite_observes_sequential_creations_and_partial_apply` | 同一补丁重复新增：第二次拒绝；只有实际应用保留前项结果 |
| `concurrent_no_overwrite_creations_have_one_winner` | 并发创建同名文件：只有一个成功，内容保持获胜请求的完整结果 |
| `diff_previews_all_operations_without_writes` | 预览新增、更新、移动和删除：显示对应差异，原文件、inode 和目录不变 |
| `diff_preserves_execution_order_for_new_then_updated_files` | 预览新增后继续更新：按实际执行顺序显示每步差异 |
| `diff_json_combines_check_policy_and_quiet_without_ansi` | 组合预览、检查、禁止覆盖、静默和 JSON：单个无颜色 JSON 对象 |
| `failed_diff_has_no_partial_stdout_and_keeps_original_files` | 预览有效前缀后失败或输入缺失：没有部分差异，文件不变，JSON diff 为 null |
| `diff_reports_binary_empty_files_and_symbolic_link_deletion` | 二进制、空文件和链接删除：显示标记或路径，不误显示为删除链接目标 |
| `diff_quotes_paths_and_disables_color_when_redirected` | 含空格和制表符的路径：头部正确转义，重定向无颜色；检查及实际应用保留文件名，去掉制表符的另一份文件不变 |
| `quiet_hides_summaries_but_keeps_errors_and_requested_output` | 两种静默写法：隐藏摘要，保留错误、差异、JSON、帮助和版本 |
| `help_documents_custom_rules_and_strict_is_not_an_alias` | 帮助显示自定义规则与示例，strict 不是接受的别名 |
| `new_option_names_are_valid_file_arguments` | 新选项名称作为 -f 文件名：按文件名读取，不改变模式 |
| `diff_unchanged_update_outputs_nothing` | 内容未变化的更新：预览成功且不输出虚假差异 |
| `no_overwrite_and_no_follow_keep_exclusive_creation_and_link_rules` | 内部禁止跟随链接模式：独占创建成功、同名拒绝、父路径及叶子链接规则保留 |
| `native_path_joins_preserve_url_ignored_bytes` | 相对/绝对路径、字符串/字节入口保留制表符与换行；百分号和非 UTF-8 字节不被误解码，NUL 仍拒绝 |
| ARM64 新选项组合 | QEMU 预览不写入、应用静默、重复新增拒绝；全部原版场景仍核对快照 |

### 新增用例结果表

| 用例 | 结果 |
| --- | --- |
| `no_overwrite_rejects_existing_add_targets_in_all_modes` | 4510d853 已通过；本次待 CI |
| `no_overwrite_refuses_move_destination_and_preserves_source` | 4510d853 已通过；本次待 CI |
| `no_overwrite_allows_updates_and_explicit_delete_then_add` | 4510d853 已通过；本次待 CI |
| `no_overwrite_observes_sequential_creations_and_partial_apply` | 4510d853 已通过；本次待 CI |
| `concurrent_no_overwrite_creations_have_one_winner` | 4510d853 已通过；本次待 CI |
| `diff_previews_all_operations_without_writes` | 4510d853 已通过；本次待 CI |
| `diff_preserves_execution_order_for_new_then_updated_files` | 4510d853 已通过；本次待 CI |
| `diff_json_combines_check_policy_and_quiet_without_ansi` | 4510d853 已通过；本次待 CI |
| `failed_diff_has_no_partial_stdout_and_keeps_original_files` | 4510d853 已通过；本次待 CI |
| `diff_reports_binary_empty_files_and_symbolic_link_deletion` | 4510d853 已通过；本次待 CI |
| `diff_quotes_paths_and_disables_color_when_redirected` | 4510d853 失败；本次待 CI |
| `quiet_hides_summaries_but_keeps_errors_and_requested_output` | 4510d853 已通过；本次待 CI |
| `help_documents_custom_rules_and_strict_is_not_an_alias` | 4510d853 已通过；本次待 CI |
| `new_option_names_are_valid_file_arguments` | 4510d853 已通过；本次待 CI |
| `diff_unchanged_update_outputs_nothing` | 4510d853 已通过；本次待 CI |
| `no_overwrite_and_no_follow_keep_exclusive_creation_and_link_rules` | 待 CI |
| `native_path_joins_preserve_url_ignored_bytes` | 待 CI |
| ARM64 新选项组合 | 待 CI |

### 新选项实机结果表

| 操作 | 预期 | 结果 |
| --- | --- | --- |
| --no-overwrite 新增同名文件 | 拒绝且原内容不变 | 待填写 |
| --diff -f change.patch | 显示差异且文件不变 | 待填写 |
| --quiet 成功与失败各一次 | 成功无摘要，失败保留错误和退出码 | 待填写 |
| -h | 包含所有自定义规则与组合示例 | 待填写 |
