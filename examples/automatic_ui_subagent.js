/* METADATA
{
    name: "Automatic_ui_subagent"

    display_name: {
      zh: "UI 控制器子代理"
      en: "UI Controller Sub-agent"
    }description: {
        zh: '''
使用单独配置的 UI 控制器模型，提供通用的高层 UI 自动化子代理工具，用于根据自然语言意图自动规划并执行点击/输入/滑动等一系列界面操作。
当用户提出需要帮忙完成某个界面操作任务（例如打开应用、搜索内容、在多个页面之间完成一套步骤）时，可以调用本包由子代理自动规划和执行具体步骤。
''',
        en: '''
Provides a general-purpose UI automation sub-agent using the separately configured UI controller model. It can plan and execute a sequence of UI actions (tap/type/swipe) from natural-language intent.
When the user asks you to complete a UI task (e.g. open an app, search content, or finish a multi-step workflow across pages), you can call this package and let the sub-agent plan and execute the steps.
'''
    }
    category: "Automatic"

    tools: []

    states: [
        {
            id: "virtual_display"
            condition: "ui.virtual_display"
            inheritTools: true
            tools: [
                {
                    name: "usage_advice"
                    description: {
                        zh: '''
 UI子代理使用建议：

 - 屏幕选择规则（重要）：不传 agent_id 或传 'default' => 主屏幕；传入且不为 'default' => 对应虚拟屏会话（虚拟屏必须可用，否则会失败）。

 - 会话复用（重要）：多次调用尽量复用同一个 agent_id（沿用上一次返回的 data.agentId），保持在同一虚拟屏/同一应用上下文内。
 - 启动前置（非常重要）：当你第一次使用某个 agent_id（新建或更换 agent_id）时，intent 开头必须写“启动XXX应用 ...”，让子代理直接执行 Launch，而不是在桌面自己找。
 - 对话无状态（重要）：每次调用对子代理都是全新对话，intent 需要自带上下文，建议固定模板：
   当前任务已经完成: ...
   你需要在此基础上进一步完成: ...
   可能用到的信息: ...
 - 意图必须自包含（重要）：禁止使用“这五个/继续/同上/刚才说的”等指代。
   - 多对象任务必须在“可能用到的信息”里给出清单（按界面顺序），并明确当前要处理哪个（例如第1个酒店）。
   - 若清单未知，本次调用先让子代理从当前页面识别并复述清单，再进行下一步（必要时拆成多次调用）。
 - 对齐推进（重要）：不要一次调用里同时做“收集清单 + 处理清单全部对象”。应拆成：先清单，再按 A→B→C 逐项处理。
 - 并行优先（重要）：互不依赖的子任务（多平台搜索/多入口确认/同一对象的信息提取分工）优先用 run_subagent_parallel_virtual 并行；并行时每个子代理建议不同 agent_id 避免互相干扰.
 - 并行资源约束（重要）：并行分支数必须受“可用独立App数量/可用虚拟屏数量”限制。
   - 同一个App/同一个包名，不能同时存在于两个虚拟屏/两个 agent_id 中并行操作（会导致应用状态错乱/坏掉）。
   - 并行分支数不得超过“可同时存在的独立App数量”（例如只有2个独立App可用，就最多2并行）。
   - 并行调用必须传入 target_app_i=目标应用名（每个 intent_i 对应一个 target_app_i），用于冲突检测；所有启用分支的 target_app_i 必须互不相同.
   - 第2次/第N次并行（重试或第二轮任务）不得因为“想更快”而擅自提高并行度；应保持同样的并行上限，只重试失败分支，或改为串行.
 - 失败与完成（重要）：半成功/误判完成不算完成；应继续纠错推进。仅在连续 2-3 次失败仍无法推进时才停止，并明确失败原因与可选替代方案.
 - 例子（详细：1并行 + 2串行）

   例子1（并行：多平台同时找同一酒店的差评要点）
   目标：同时在 大众点评/美团/携程 搜索“XX酒店”，各自提取“近一年差评Top3要点 + 原文引用 + 日期”，最后主Agent合并.
   调用流程：
   1) 主Agent 一次并行：
      - 调用 run_subagent_parallel_virtual，给每个子代理一个互不干扰的 agent_id（例如 dp_1 / mt_1 / xc_1）。
      - intent_1（大众点评）示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开大众点评，搜索“XX酒店”，进入酒店详情，进入评价/差评/低分区，提取近一年差评Top3要点，并把每条要点附带1句原文引用+日期.
        可能用到的信息: 目标酒店名=XX酒店；输出格式=1)要点 2)引用 3)日期；如果搜到多个同名酒店，必须先确认地址/商圈与目标一致.
      - intent_2（美团）/ intent_3（携程）同理，各自写清 app、路径、输出格式.
   2) 主Agent 汇总：读取并行返回 results，合并三个平台的提取结果.
   3) 只重试失败分支：若 results 里只有美团分支失败，则只对美团再发起一次（不要重跑点评/携程）。
      - 例如再次调用 run_subagent_virtual（或再次 run_subagent_parallel_virtual 但只填 intent_2），并补充纠错信息：
        当前任务已经完成: 上次在美团搜索到酒店列表，但未能进入评价页（可能入口在“点评/评价”Tab）。
        你需要在此基础上进一步完成: 重新打开美团搜索“XX酒店”，进入正确的酒店详情页，找到“评价/点评”入口并进入差评/低分区，按同样格式输出Top3.
        可能用到的信息: 若页面出现“住客点评/全部评价/差评”多入口，优先选择“全部评价”再筛选“差评/低分”。

   例子2（串行：先清单，再按 A→B→C 逐项处理）
   目标：在携程酒店列表页，先列出前5家酒店；然后只处理第1家酒店的差评要点；处理完再处理第2家……
   调用流程：
   1) 清单阶段（一次 run_subagent_virtual）：
      - 主Agent 调用 run_subagent_virtual(intent=..., agent_id="ctrip_1")
      - intent 示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开携程，搜索“杭州 西湖 酒店”，进入列表页；把当前屏幕能看到的酒店按从上到下顺序列出前5个（名称+价格/评分如可见），并停留在列表页不要进入详情.
        可能用到的信息: 输出必须包含清单序号1-5；若需要滚动才能凑满5个可以滚动一次，但仍要保持顺序.
      - 该次返回 data.agentId 记为 A（后续复用）
   2) 单对象阶段：处理“清单第1家”（一次 run_subagent_virtual，复用 agent_id=A）
      - intent 示例：
        当前任务已经完成: 已获得酒店清单（1)酒店A 2)酒店B 3)酒店C 4)酒店D 5)酒店E），当前停留在列表页.
        你需要在此基础上进一步完成: 进入酒店A详情页，找到评价页并筛选差评/低分，提取差评Top3要点（每条含1句原文引用）。完成后返回列表页并确认列表顶部仍是酒店A/B/C顺序.
        可能用到的信息: 目标对象=清单第1家=酒店A；如果进入后发现标题不是酒店A则立刻返回列表并重新点击正确条目.
   3) 继续处理第2家/第3家……（每次都复用 agent_id=A，并在 intent 中显式写“已完成/下一步/关键信息”，以及“当前目标=清单第2家=酒店B”）。

   例子3（串行：发消息，强调“页面确认+会话复用”）
   目标：在微信给“张三”发送“我到楼下了”，并确认发送成功.
   调用流程：
   1) 打开并定位会话（一次 run_subagent_virtual）：
      - 主Agent 调用 run_subagent_virtual(intent=..., agent_id="wechat_1")
      - intent 示例：
        当前任务已经完成: 无
        你需要在此基础上进一步完成: 打开微信，进入聊天列表，搜索联系人“张三”，打开与“张三”的聊天页面；必须确认页面顶部标题=张三.
        可能用到的信息: 如果搜索结果有多个“张三”，需要根据头像/备注/地区等二次确认；不确定时返回并说明.
      - 该次返回 data.agentId 记为 W（后续复用）
   2) 发送并二次确认（一次 run_subagent_virtual，复用 agent_id=W）：
      - intent 示例：
        当前任务已经完成: 已打开与“张三”的聊天页，顶部标题=张三.
        你需要在此基础上进一步完成: 输入并发送消息“我到楼下了”；发送后在消息列表中确认该消息出现在最新一条且无明显发送失败标记（如红色感叹号）.
        可能用到的信息: 若出现权限弹窗/键盘遮挡，先处理弹窗再继续；若发送失败，尝试重发一次并说明原因.
 ''',
        en: '''
 UI sub-agent usage advice:

 - Screen selection rule (important): omitted agent_id or 'default' => main screen; provided and not 'default' => the requested virtual display session (must be available, otherwise the run fails).

 - Session reuse (important): reuse the same agent_id across calls when possible (use the returned data.agentId) to stay in the same virtual display and app context.
 - Launch first (very important): when you first use an agent_id (new or changed), the intent must start with "Launch <app> ..." so the sub-agent performs a Launch directly instead of searching from the home screen.
 - Stateless per call (important): each call is a new conversation; include all necessary context. Recommended template: Completed so far / Next objective / Useful info.
 - Self-contained intent (important): do not use references like "those five / continue / same as above".
   - For multi-item tasks, list items in "Useful info" (in on-screen order) and specify which one to handle now.
   - If the list is unknown, first ask the sub-agent to identify and restate the list from the current screen, then proceed.
 - Step-by-step alignment (important): do not combine "collect list" + "process all items" in one call. Split into: list first, then process A→B→C.
 - Prefer parallelism (important): for independent subtasks (multi-platform search / multi-entry verification / extraction sub-tasks for the same object), use run_subagent_parallel_virtual. Use different agent_id per branch to avoid interference.
 - Parallel resource constraints (important): parallel branches are limited by available independent apps / virtual displays.
   - The same app/package must not be operated in parallel across two virtual displays / agent_id (it can break app state).
   - Branch count must not exceed available independent apps.
   - Parallel calls must provide target_app_i for each enabled intent_i; all target_app_i must be different.
   - Do not increase parallelism in later rounds; keep the same upper bound and only retry failed branches, or switch to serial.
 - Failure vs done (important): partial success is not done; keep correcting and progressing. Only stop after 2-3 consecutive failures with a clear reason and alternatives.
'''
                    }
                    advice: true
                    parameters: []
                }

                {
                    name: "run_subagent_main"
                    description: {
                        zh: "在主屏幕运行 UI 子代理（强制主屏）。",
                        en: "Run the UI sub-agent on the main screen (forced main screen)."
                    }
                    parameters: [
                        {
                            name: "intent"
                            description: {
                                zh: "任务意图描述",
                                en: "Task intent description"
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "target_app"
                            description: {
                                zh: "目标应用名/包名（可选）",
                                en: "Target app name/package (optional)"
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps"
                            description: {
                                zh: "最大执行步数（默认20）",
                                en: "Maximum execution steps (default: 20)"
                            }
                            type: "number"
                            required: false
                        }
                    ]
                }

                {
                    name: "run_subagent_virtual"
                    description: {
                        zh: "在虚拟屏幕会话运行 UI 子代理（强制虚拟屏）。",
                        en: "Run the UI sub-agent on a virtual-display session (forced virtual screen)."
                    }
                    parameters: [
                        {
                            name: "intent"
                            description: {
                                zh: "任务意图描述",
                                en: "Task intent description"
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "target_app"
                            description: {
                                zh: "目标应用名/包名（可选）",
                                en: "Target app name/package (optional)"
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps"
                            description: {
                                zh: "最大执行步数（默认20）",
                                en: "Maximum execution steps (default: 20)"
                            }
                            type: "number"
                            required: false
                        }
                        {
                            name: "agent_id"
                            description: {
                                zh: "虚拟屏会话 agent_id（必须为非 'default'；可传入复用，或留空复用上次返回的 data.agentId）。",
                                en: "Virtual-screen session agent_id (must be non-'default'; pass to reuse, or omit to reuse returned data.agentId)."
                            }
                            type: "string"
                            required: false
                        }
                    ]
                }

                {
                    name: "run_subagent_parallel_virtual"
                    description: {
                        zh: '''
并行运行 1-4 个 UI 子代理（强制虚拟屏）。

 注意：并行调用时，每个子代理对它自身都是全新对话，因此 intent_1..4 需要由主Agent分别写清楚“已完成/下一步/关键信息”。
 建议并行时每个子代理使用不同的 agent_id（必须显式传入，且不能为 'default'），避免操作同一虚拟屏幕造成冲突。
 如果并行任务中仅有部分子代理失败，则只对失败的子代理继续发起后续调用（补充纠错信息、提高约束），不要让已成功的子代理重复执行。
 每个 intent_i 必须自包含；建议不同 agent_id；只重试失败的 intent_i。
 典型场景：多平台并行搜索；同一对象多入口确认/交叉校验；把“同一对象A”的分工并行做完后再进入B.
 '''
                    }
                    parameters: [
                        {
                            name: "intent_1"
                            description: {
                                zh: "第1个子代理意图（推荐使用：当前任务已经完成/你需要进一步完成/可能用到的信息 三段式）",
                                en: "Intent for sub-agent #1 (recommended template: Completed so far / Next objective / Useful info)."
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "target_app_1"
                            description: {
                                zh: "第1个子代理目标应用名（必填，用于并行冲突检测；各分支必须不同）",
                                en: "Target app name for sub-agent #1 (required for conflict detection; must be different across branches)."
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "max_steps_1"
                            description: {
                                zh: "第1个子代理最大步数（默认20）",
                                en: "Max steps for sub-agent #1 (default: 20)."
                            }
                            type: "number"
                            required: false
                        }
                        {
                            name: "agent_id_1"
                            description: {
                                zh: "第1个子代理 agent_id（必填，且不能为 'default'；用于指定虚拟屏会话；并行建议不同）",
                                en: "agent_id for sub-agent #1 (required, must not be 'default'; selects a virtual-display session; use different agent_id per branch)."
                            }
                            type: "string"
                            required: true
                        }

                        {
                            name: "intent_2"
                            description: {
                                zh: "第2个子代理意图（可选）",
                                en: "Intent for sub-agent #2 (optional)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "target_app_2"
                            description: {
                                zh: "第2个子代理目标应用名（当 intent_2 存在时必填；各分支必须不同）",
                                en: "Target app name for sub-agent #2 (required when intent_2 is provided; must be different across branches)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps_2"
                            description: {
                                zh: "第2个子代理最大步数（默认20）",
                                en: "Max steps for sub-agent #2 (default: 20)."
                            }
                            type: "number"
                            required: false
                        }
                        {
                            name: "agent_id_2"
                            description: {
                                zh: "第2个子代理 agent_id（当 intent_2 存在时必填，且不能为 'default'；用于指定虚拟屏会话；并行建议不同）",
                                en: "agent_id for sub-agent #2 (required when intent_2 is provided, must not be 'default'; selects a virtual-display session; use different agent_id per branch)."
                            }
                            type: "string"
                            required: false
                        }

                        {
                            name: "intent_3"
                            description: {
                                zh: "第3个子代理意图（可选）",
                                en: "Intent for sub-agent #3 (optional)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "target_app_3"
                            description: {
                                zh: "第3个子代理目标应用名（当 intent_3 存在时必填；各分支必须不同）",
                                en: "Target app name for sub-agent #3 (required when intent_3 is provided; must be different across branches)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps_3"
                            description: {
                                zh: "第3个子代理最大步数（默认20）",
                                en: "Max steps for sub-agent #3 (default: 20)."
                            }
                            type: "number"
                            required: false
                        }
                        {
                            name: "agent_id_3"
                            description: {
                                zh: "第3个子代理 agent_id（当 intent_3 存在时必填，且不能为 'default'）",
                                en: "agent_id for sub-agent #3 (required when intent_3 is provided; must not be 'default')."
                            }
                            type: "string"
                            required: false
                        }

                        {
                            name: "intent_4"
                            description: {
                                zh: "第4个子代理意图（可选）",
                                en: "Intent for sub-agent #4 (optional)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "target_app_4"
                            description: {
                                zh: "第4个子代理目标应用名（当 intent_4 存在时必填；各分支必须不同）",
                                en: "Target app name for sub-agent #4 (required when intent_4 is provided; must be different across branches)."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps_4"
                            description: {
                                zh: "第4个子代理最大步数（默认20）",
                                en: "Max steps for sub-agent #4 (default: 20)."
                            }
                            type: "number"
                            required: false
                        }
                        {
                            name: "agent_id_4"
                            description: {
                                zh: "第4个子代理 agent_id（当 intent_4 存在时必填，且不能为 'default'）",
                                en: "agent_id for sub-agent #4 (required when intent_4 is provided; must not be 'default')."
                            }
                            type: "string"
                            required: false
                        }
                    ]
                }

                {
                    name: "close_all_virtual_displays"
                    description: {
                        zh: "关闭所有虚拟屏幕。",
                        en: "Close all virtual displays."
                    }
                    parameters: []
                }

                {
                    name: "run_subagent_main"
                    description: {
                        zh: "在主屏幕运行 UI 子代理（强制主屏）。",
                        en: "Run the UI sub-agent on the main screen (forced main screen)."
                    }
                    parameters: [
                        {
                            name: "intent"
                            description: {
                                zh: "任务意图描述",
                                en: "Task intent description"
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "target_app"
                            description: {
                                zh: "目标应用名/包名（可选）",
                                en: "Target app name/package (optional)"
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps"
                            description: {
                                zh: "最大执行步数（默认20）",
                                en: "Maximum execution steps (default: 20)"
                            }
                            type: "number"
                            required: false
                        }
                    ]
                }
            ]
        }

        {
            id: "main_screen"
            condition: "!ui.virtual_display"
            inheritTools: true
            tools: [
                {
                    name: "usage_advice"
                    description: {
                        zh: '''
 UI子代理使用建议（主屏模式）：

 - 屏幕选择规则（重要）：主屏模式始终在主屏幕执行；agent_id 会被忽略/不适用。

 - 启动前置（非常重要）：当你第一次需要操作某个应用时，intent 开头必须写“启动XXX应用 ...”，让子代理直接执行 Launch，而不是在桌面自己找。
 - 对话无状态（重要）：每次调用对子代理都是全新对话，intent 必须自带上下文，建议固定模板：
   当前任务已经完成: ...
   你需要在此基础上进一步完成: ...
   可能用到的信息: ...
 - 意图必须自包含（重要）：禁止使用“这五个/继续/同上/刚才说的”等指代.
 - 严格串行（重要）：主屏模式不支持并行工具；一次只做一个明确子目标，必要时拆成多次调用.
 - 不支持会话复用（重要）：主屏模式不支持 agent_id，会话复用相关策略不适用.
 - 失败与完成（重要）：半成功不算完成；应继续纠错推进.仅在连续 2-3 次失败仍无法推进时才停止，并明确失败原因与可选替代方案.
 ''',
                        en: '''
 UI sub-agent usage advice (main-screen mode):

 - Screen selection rule (important): main-screen mode always operates on the main screen; agent_id is ignored / not applicable.

 - Launch first (very important): when you need to operate an app for the first time, the intent must start with "Launch <app> ..." so the sub-agent performs Launch directly.
 - Stateless per call (important): each call is a new conversation; include all context. Recommended template: Completed so far / Next objective / Useful info.
 - Self-contained intent (important): do not use references like "those five / continue / same as above".
 - Strictly serial (important): main-screen mode does not support parallel tools; do one clear sub-goal per call.
 - No session reuse (important): main-screen mode does not support agent_id; session reuse strategies do not apply.
 - Failure vs done (important): partial success is not done; keep progressing. Only stop after 2-3 consecutive failures with a clear reason and alternatives.
'''
                    }
                    advice: true
                    parameters: []
                }

                {
                    name: "run_subagent_main"
                    description: {
                        zh: '''
 在主屏幕运行 UI 子代理（强制主屏）。

 注意：主屏模式不支持虚拟屏会话与并行工具。
 ''',
                        en: '''
 Run the UI sub-agent on the main screen (forced main screen).

 Note: main-screen mode does not support virtual sessions and does not support parallel tools.
 '''
                    }
                    parameters: [
                        {
                            name: "intent"
                            description: {
                                zh: "任务意图描述，例如：'打开微信并发送一条消息' 或 '在B站搜索某个视频'",
                                en: "Task intent description, e.g. 'Open WeChat and send a message' or 'Search a video on Bilibili'."
                            }
                            type: "string"
                            required: true
                        }
                        {
                            name: "target_app"
                            description: {
                                zh: "目标应用名/包名（建议传入，用于在虚拟屏未创建时先执行一次默认 Launch 预热虚拟屏，避免在主屏误操作；约定：当用户要求分析当前屏幕/页面内容时不要传 target_app，也不要预热虚拟屏）",
                                en: "Target app name/package (recommended). Helps with a default Launch/warm-up and avoids operating on the wrong screen. Convention: when the user asks to analyze the current screen/page, do not pass target_app and do not prewarm the virtual display."
                            }
                            type: "string"
                            required: false
                        }
                        {
                            name: "max_steps"
                            description: {
                                zh: "最大执行步数，默认20，可根据任务复杂度调整。",
                                en: "Maximum execution steps (default: 20). Adjust based on task complexity."
                            }
                            type: "number"
                            required: false
                        }
                    ]
                }
            ]
        }
    ]
 }*/
const UIAutomationSubAgentTools = (function () {
    const CACHE_KEY = '__operit_ui_subagent_cached_agent_id';
    function getCachedAgentId() {
        try {
            return globalThis[CACHE_KEY];
        }
        catch (_e) {
            return undefined;
        }
    }
    function setCachedAgentId(value) {
        try {
            if (value === undefined || value === null || String(value).length === 0) {
                delete globalThis[CACHE_KEY];
            }
            else {
                globalThis[CACHE_KEY] = String(value);
            }
        }
        catch (_e) {
        }
    }
    function getPackageState() {
        try {
            return getState();
        }
        catch (_e) {
            return undefined;
        }
    }
    function errorMessage(e) {
        return e instanceof Error ? e.message : String(e);
    }
    function getStringArrayFromUnknown(v) {
        if (!Array.isArray(v))
            return [];
        return v.map((x) => String(x || '').trim()).filter((s) => s.length > 0);
    }
    function parseInstalledAppEntry(raw) {
        const s = String(raw || '').trim();
        if (!s)
            return { name: '', raw: '' };
        const open = s.lastIndexOf('(');
        const close = s.lastIndexOf(')');
        if (open >= 0 && close === s.length - 1 && open < close) {
            const name = s.slice(0, open).trim();
            const pkg = s.slice(open + 1, close).trim();
            if (name && pkg)
                return { name, pkg, raw: s };
        }
        return { name: s, raw: s };
    }
    async function getInstalledApps() {
        const appList = await Tools.System.listApps(false);
        const rawItems = getStringArrayFromUnknown(appList?.packages);
        const entries = rawItems.map(parseInstalledAppEntry).filter((e) => e.name);
        const nameMap = new Map();
        for (const e of entries)
            nameMap.set(e.name.toLowerCase(), e.name);
        const names = Array.from(nameMap.values()).sort((a, b) => a.localeCompare(b));
        return { entries, names };
    }
    function matchTarget(targetApp, installed) {
        const t = String(targetApp || '').trim();
        if (!t)
            return null;
        const tl = t.toLowerCase();
        const m = installed.find((a) => (a.pkg || '').toLowerCase() === tl) ||
            installed.find((a) => a.name.toLowerCase() === tl) ||
            installed.find((a) => a.raw.toLowerCase() === tl);
        if (!m)
            return null;
        const run = m.pkg || m.name;
        return { run, key: run.toLowerCase() };
    }
    async function usage_advice(_params) {
        const state = getPackageState();
        const isMainScreen = String(state).toLowerCase() === 'main_screen';
        return {
            success: true,
            message: 'UI子代理使用建议',
            data: {
                advice: isMainScreen
                    ? ("主屏模式：不支持 agent_id，会话复用策略不适用；不支持并行工具，一次只做一个明确子目标（必要时拆多次）。\n" +
                        "屏幕选择规则：不传 agent_id 或传 'default' => 主屏幕；传入且不为 'default' => 虚拟屏（主屏模式下会忽略并强制主屏）。\n" +
                        "启动前置（非常重要）：当你第一次需要操作某个应用时，intent 开头必须写“启动XXX应用 ...”，让子代理直接执行 Launch，而不是在桌面自己找。\n" +
                        "对话无状态：每次调用是新对话，intent 写清 已完成/下一步/关键信息。\n" +
                        "自包含：别用‘这五个/继续/同上’；多对象要么列清单+当前目标，要么先让子代理在当前页识别并复述清单。\n" +
                        "失败与完成：半成功不算完成；未达成目标继续推进，连续 2-3 次失败再停并说明原因。")
                    : ("虚拟屏模式：尽量复用 agent_id（沿用 data.agentId）保持同一虚拟屏/同一应用上下文。\n" +
                        "屏幕选择规则：不传 agent_id 或传 'default' => 主屏幕；传入且不为 'default' => 对应虚拟屏会话（虚拟屏必须可用，否则失败）。为避免误操作主屏，虚拟屏模式下首次调用建议显式传入 agent_id。\n" +
                        "启动前置（非常重要）：当你第一次使用某个 agent_id（新建或更换 agent_id）时，intent 开头必须写“启动XXX应用 ...”，让子代理直接执行 Launch，而不是在桌面自己找。\n" +
                        "对话无状态：每次调用是新对话，intent 写清 已完成/下一步/关键信息。\n" +
                        "自包含：别用‘这五个/继续/同上’；多对象要么列清单+当前目标，要么先让子代理在当前页识别并复述清单。\n" +
                        "对齐+并行：先清单后逐项(A→B→C)；独立子任务/同一对象多入口优先并行(run_subagent_parallel_virtual)，只重试失败分支。\n" +
                        "并行资源约束：并行分支数必须受可用独立App/虚拟屏数量限制；同一个App/包名不能同时出现在两个虚拟屏/两个agent_id 中并行操作（会坏）。并行调用必须传 target_app_i=目标应用名，且各分支 target_app_i 不能重复；第2次/第N次并行不得擅自提高并行度，保持上限，只重试失败分支或改串行。\n" +
                        "失败与完成：半成功不算完成；未达成目标继续推进，连续 2-3 次失败再停并说明原因。"),
            },
        };
    }
    async function run_subagent_internal(params) {
        const { intent, max_steps, agent_id, target_app } = params;
        const state = getPackageState();
        const isMainScreen = String(state).toLowerCase() === 'main_screen';
        const explicitAgentId = (agent_id === undefined || agent_id === null) ? '' : String(agent_id).trim();
        const cachedAgentId = getCachedAgentId();
        let agentIdToUse;
        if (isMainScreen) {
            agentIdToUse = 'default';
        }
        else {
            agentIdToUse = explicitAgentId.length > 0 ? explicitAgentId : cachedAgentId;
            if (agentIdToUse === undefined || agentIdToUse === null || String(agentIdToUse).trim().length === 0) {
                return {
                    success: false,
                    message: "虚拟屏模式下未指定 agent_id：为避免误操作主屏幕，请显式传入 agent_id（且不为 'default'）来使用虚拟屏会话；或先完成一次成功调用并复用返回的 data.agentId。",
                };
            }
        }
        let targetAppForRun = target_app;
        if (target_app && String(target_app).trim().length > 0) {
            const installed = await getInstalledApps();
            const matched = matchTarget(target_app, installed.entries);
            if (!matched) {
                return {
                    success: false,
                    message: `目标应用不存在：当前给定的 target_app=“${String(target_app).trim()}” 未在已安装应用中找到。已返回已安装应用名列表。`,
                    data: {
                        target_app: String(target_app).trim(),
                        installed_apps: installed.names,
                    },
                };
            }
            targetAppForRun = matched.run;
        }
        const result = await Tools.UI.runSubAgent(intent, max_steps, agentIdToUse, targetAppForRun);
        const agentId = result?.agentId;
        if (agentId && String(agentId).trim().length > 0 && String(agentId).trim().toLowerCase() !== 'default') {
            setCachedAgentId(agentId);
        }
        return {
            success: true,
            message: 'UI子代理执行完成',
            data: result,
        };
    }
    async function run_subagent_main(params) {
        const { intent, max_steps, target_app } = params;
        return run_subagent_internal({ intent, max_steps, target_app, agent_id: 'default' });
    }
    async function run_subagent_virtual(params) {
        const { intent, max_steps, agent_id, target_app } = params;
        const explicitAgentId = (agent_id === undefined || agent_id === null) ? '' : String(agent_id).trim();
        const cachedAgentId = getCachedAgentId();
        const agentIdToUse = explicitAgentId.length > 0 ? explicitAgentId : cachedAgentId;
        if (agentIdToUse === undefined || agentIdToUse === null || String(agentIdToUse).trim().length === 0 || String(agentIdToUse).trim().toLowerCase() === 'default') {
            return {
                success: false,
                message: "虚拟屏模式必须使用非 'default' 的 agent_id：请显式传入 agent_id（非 'default'），或先完成一次成功调用并复用返回的 data.agentId。",
            };
        }
        return run_subagent_internal({ intent, max_steps, target_app, agent_id: String(agentIdToUse) });
    }
    async function run_subagent_parallel_internal(params) {
        const state = getPackageState();
        const isMainScreen = String(state).toLowerCase() === 'main_screen';
        if (isMainScreen) {
            return {
                success: false,
                message: "主屏模式不支持 run_subagent_parallel_virtual 并行调用。请改用 run_subagent_main 串行执行。",
            };
        }
        const slots = [1, 2, 3, 4];
        const activeSlots = slots
            .map((i) => {
            const intent = params[`intent_${i}`];
            if (!intent || intent.trim().length === 0)
                return null;
            const targetApp = params[`target_app_${i}`];
            return { index: i, targetApp };
        })
            .filter((x) => Boolean(x));
        const missingTargets = activeSlots
            .filter((s) => s.targetApp === undefined || s.targetApp === null || String(s.targetApp).trim().length === 0)
            .map((s) => s.index);
        if (missingTargets.length > 0) {
            return {
                success: false,
                message: `并行参数错误：intent_${missingTargets.join(', intent_')} 缺少 target_app_${missingTargets.join(', target_app_')}（目标应用名）。并行时必须为每个启用分支传入目标应用名，用于检测“同一应用不能出现在两个虚拟屏/agent_id”的冲突。`,
            };
        }
        const missingAgentIds = activeSlots
            .filter((s) => {
            const v = params[`agent_id_${s.index}`];
            const id = v === undefined || v === null ? '' : String(v).trim();
            return id.length === 0 || id.toLowerCase() === 'default';
        })
            .map((s) => s.index);
        if (missingAgentIds.length > 0) {
            return {
                success: false,
                message: `并行参数错误：虚拟屏并行模式下，每个启用分支必须显式传入非 'default' 的 agent_id。缺少/非法 agent_id 的分支：${missingAgentIds.map((i) => `#${i}`).join('，')}。`,
            };
        }
        const installed = await getInstalledApps();
        const resolvedBySlot = new Map();
        const missingApps = [];
        for (const s of activeSlots) {
            const t = String(s.targetApp || '').trim();
            const m = matchTarget(t, installed.entries);
            if (!m)
                missingApps.push(t);
            else
                resolvedBySlot.set(s.index, m);
        }
        if (missingApps.length) {
            return {
                success: false,
                message: `目标应用不存在：当前给定的 target_app 列表中包含未安装/不存在的应用：${missingApps.map((s) => `“${String(s).trim()}”`).join('，')}。已返回已安装应用名列表。`,
                data: {
                    missing_apps: missingApps,
                    installed_apps: installed.names,
                },
            };
        }
        const used = {};
        for (const s of activeSlots) {
            const key = resolvedBySlot.get(s.index)?.key || String(s.targetApp).trim().toLowerCase();
            const prev = used[key];
            if (prev !== undefined) {
                return {
                    success: false,
                    message: `并行参数错误：target_app_${prev} 与 target_app_${s.index} 重复（同一目标应用=“${String(s.targetApp).trim()}”）。同一应用不能同时在两个虚拟屏/agent_id 中并行操作。`,
                };
            }
            used[key] = s.index;
        }
        const tasks = slots
            .map((i) => {
            const intent = params[`intent_${i}`];
            if (!intent || intent.trim().length === 0)
                return null;
            const maxSteps = params[`max_steps_${i}`];
            const agentId = params[`agent_id_${i}`];
            const targetApp = resolvedBySlot.get(i)?.run ?? params[`target_app_${i}`];
            return (async () => {
                try {
                    const result = await Tools.UI.runSubAgent(String(intent), maxSteps === undefined ? undefined : Number(maxSteps), agentId === undefined || agentId === null || String(agentId).trim().length === 0 ? undefined : String(agentId).trim(), targetApp);
                    return { index: i, success: true, result };
                }
                catch (e) {
                    return { index: i, success: false, error: errorMessage(e) };
                }
            })();
        })
            .filter((x) => x !== null);
        const results = await Promise.all(tasks);
        const okCount = results.filter((r) => r.success).length;
        return {
            success: true,
            message: `并行UI子代理执行完成：成功 ${okCount} 个 / 共 ${results.length} 个`,
            data: {
                results,
            },
        };
    }
    async function run_subagent_parallel_virtual(params) {
        return run_subagent_parallel_internal(params);
    }
    async function close_all_virtual_displays(_params) {
        const result = await toolCall('close_all_virtual_displays', {});
        const ok = result?.success !== false;
        const error = result?.error;
        return {
            success: ok,
            message: ok
                ? '已关闭所有虚拟屏幕。'
                : `关闭虚拟屏幕失败：${error ? String(error) : 'unknown error'}`,
            data: result,
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${errorMessage(error)}`,
            });
        }
    }
    return {
        usage_advice: (params) => wrapToolExecution(usage_advice, params),
        run_subagent_main: (params) => wrapToolExecution(run_subagent_main, params),
        run_subagent_virtual: (params) => wrapToolExecution(run_subagent_virtual, params),
        close_all_virtual_displays: (params) => wrapToolExecution(close_all_virtual_displays, params),
        run_subagent_parallel_virtual: (params) => wrapToolExecution(run_subagent_parallel_virtual, params),
    };
})();
exports.usage_advice = UIAutomationSubAgentTools.usage_advice;
exports.run_subagent_main = UIAutomationSubAgentTools.run_subagent_main;
exports.run_subagent_virtual = UIAutomationSubAgentTools.run_subagent_virtual;
exports.close_all_virtual_displays = UIAutomationSubAgentTools.close_all_virtual_displays;
exports.run_subagent_parallel_virtual = UIAutomationSubAgentTools.run_subagent_parallel_virtual;
