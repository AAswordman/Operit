package com.ai.assistance.operit.data.collects
import com.ai.assistance.operit.api.chat.llmprovider.ThinkingQualityOptionOrdering
import java.util.Locale
import org.json.JSONArray

import org.json.JSONObject

object ModelThinkingConfigDefaults {
        private val providerRulesCache = mutableMapOf<String, String>()
        private val providerRulesCacheLock = Any()

        val DEFAULT_JSON: String =
                """
                [
                  {
                    "id": "openai-gpt5-chat-reasoning-effort",
                    "providers": ["OPENAI", "OPENAI_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)(?:gpt-5(?:[.-]|$)|o[134](?:[.-]|$))"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "minimal", "label": "minimal", "path": "reasoning_effort", "value": "minimal"}
                    ]
                  },
                  {
                    "id": "openai-gpt5-responses-reasoning-effort",
                    "providers": ["OPENAI_RESPONSES", "OPENAI_RESPONSES_GENERIC", "OPENAI_CODEX"],
                    "match": {"modelRegex": ["(?:^|/)(?:gpt-5(?:[.-]|$)|o[134](?:[.-]|$))"]},
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "enable": [
                      {"path": "reasoning.summary", "value": "auto"},
                      {"path": "include", "value": ["reasoning.encrypted_content"]}
                    ],
                    "disable": [
                      {"path": "reasoning.effort", "value": "none"}
                    ],
                    "options": [
                      {"id": "medium", "label": "medium", "path": "reasoning.effort", "value": "medium"},
                      {"id": "low", "label": "low", "path": "reasoning.effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning.effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning.effort", "value": "max"},
                      {"id": "minimal", "label": "minimal", "path": "reasoning.effort", "value": "minimal"},
                      {"id": "none", "label": "none", "path": "reasoning.effort", "value": "none"}
                    ]
                  },
                  {
                    "id": "gemini-pre25-thinking-unsupported",
                    "providers": ["GOOGLE", "GEMINI_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)gemini-(?:1(?:[.-]|$)|2(?:[.-](?:0|1|2|3|4)(?:[.-]|$)|$))"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "gemini-3-thinking-level",
                    "providers": ["GOOGLE", "GEMINI_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)gemini-3(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "thinkingLevel",
                    "required": true,
                    "enable": [
                      {"path": "generationConfig.thinkingConfig.includeThoughts", "value": true}
                    ],
                    "options": [
                      {"id": "MINIMAL", "label": "MINIMAL", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MINIMAL"},
                      {"id": "LOW", "label": "LOW", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "LOW"},
                      {"id": "MEDIUM", "label": "MEDIUM", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MEDIUM"},
                      {"id": "HIGH", "label": "HIGH", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "HIGH"}
                    ]
                  },
                  {
                    "id": "anthropic-manual-extended-thinking",
                    "providers": ["ANTHROPIC", "ANTHROPIC_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)claude-(?:[^/]+-)?(?:3(?:[-.]|$)|4[-.]?[0-5](?:[-.]|$))"]},
                    "control": "levels",
                    "parameterLabel": "thinking.budget_tokens",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "1024", "label": "1024", "path": "thinking.budget_tokens", "value": 1024},
                      {"id": "4096", "label": "4096", "path": "thinking.budget_tokens", "value": 4096},
                      {"id": "8192", "label": "8192", "path": "thinking.budget_tokens", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "thinking.budget_tokens", "value": 16384}
                    ]
                  },
                  {
                    "id": "anthropic-adaptive-thinking",
                    "providers": ["ANTHROPIC", "ANTHROPIC_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)claude-(?:[^/]+-)?4[-.]?[6-9](?:[-.]|$)"]},
                    "control": "levels",
                    "parameterLabel": "output_config.effort",
                    "enable": [
                      {"path": "thinking.type", "value": "adaptive"},
                      {"path": "thinking.display", "value": "summarized"}
                    ],
                    "options": [
                      {"id": "low", "label": "low", "path": "output_config.effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "output_config.effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "output_config.effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "moonshot-kimi-k3-reasoning-effort",
                    "providers": ["MOONSHOT"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k3(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "moonshot-kimi-k27-forced-thinking",
                    "providers": ["MOONSHOT"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-]7(?:[.-]|$)"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "moonshot-kimi-k2-thinking-toggle",
                    "providers": ["MOONSHOT"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-][56](?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "openai-chat-reasoning-effort",
                    "providers": ["OPENAI", "OPENAI_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)(?:o[1-9]|gpt-[5-9]|gpt-oss|codex)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "minimal", "label": "minimal", "path": "reasoning_effort", "value": "minimal"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "openai-chat-non-reasoning-models",
                    "providers": ["OPENAI_GENERIC"],
                    "match": {"modelRegex": ["(?:^|/)(?:chatgpt-|gpt-3|gpt-4)"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "openai-compatible-chat-reasoning-effort",
                    "providers": ["OPENAI_GENERIC"],
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "openai-responses-reasoning-effort",
                    "providers": ["OPENAI_RESPONSES", "OPENAI_RESPONSES_GENERIC", "OPENAI_CODEX"],
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "enable": [
                      {"path": "reasoning.summary", "value": "auto"},
                      {"path": "include", "value": ["reasoning.encrypted_content"]}
                    ],
                    "disable": [
                      {"path": "reasoning.effort", "value": "none"}
                    ],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning.effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning.effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning.effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning.effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "gemini-25-thinking-budget",
                    "providers": ["GOOGLE", "GEMINI_GENERIC"],
                    "match": {"modelPrefix": ["gemini-2.5"]},
                    "control": "levels",
                    "parameterLabel": "thinkingBudget",
                    "enable": [
                      {"path": "generationConfig.thinkingConfig.includeThoughts", "value": true}
                    ],
                    "disable": [
                      {"path": "generationConfig.thinkingConfig.includeThoughts", "value": false},
                      {"path": "generationConfig.thinkingConfig.thinkingBudget", "value": 0}
                    ],
                    "options": [
                      {"id": "1024", "label": "1024", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 1024},
                      {"id": "4096", "label": "4096", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 4096},
                      {"id": "8192", "label": "8192", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 16384},
                      {"id": "32768", "label": "32768", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 32768}
                    ]
                  },
                  {
                    "id": "gemini-thinking-level",
                    "providers": ["GOOGLE", "GEMINI_GENERIC"],
                    "control": "levels",
                    "parameterLabel": "thinkingLevel",
                    "required": true,
                    "enable": [
                      {"path": "generationConfig.thinkingConfig.includeThoughts", "value": true}
                    ],
                    "options": [
                      {"id": "MINIMAL", "label": "MINIMAL", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MINIMAL"},
                      {"id": "LOW", "label": "LOW", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "LOW"},
                      {"id": "MEDIUM", "label": "MEDIUM", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MEDIUM"},
                      {"id": "HIGH", "label": "HIGH", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "HIGH"}
                    ]
                  },
                  {
                    "id": "deepseek-responses-reasoning-effort",
                    "providers": ["DEEPSEEK"],
                    "match": {"endpointSuffix": ["/responses"]},
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "required": true,
                    "options": [
                      {"id": "off", "label": "Off", "path": "reasoning.effort", "value": "none"},
                      {"id": "low", "label": "Low", "path": "reasoning.effort", "value": "low"},
                      {"id": "high", "label": "High", "path": "reasoning.effort", "value": "high"},
                      {"id": "max", "label": "Max", "path": "reasoning.effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "deepseek-reasoning-effort",
                    "providers": ["DEEPSEEK"],
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [
                      {"path": "thinking.type", "value": "enabled"}
                    ],
                    "disable": [
                      {"path": "thinking.type", "value": "disabled"}
                    ],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "moonshot-kimi-thinking-toggle",
                    "providers": ["MOONSHOT"],
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "mimo-thinking-toggle",
                    "providers": ["MIMO"],
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "doubao-thinking-toggle",
                    "providers": ["DOUBAO"],
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "siliconflow-deepseek-v4-effort",
                    "providers": ["SILICONFLOW"],
                    "match": {"firstSegment": ["deepseek-ai"], "lastSegmentPrefix": ["deepseek-v4"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "siliconflow-toggle-families",
                    "providers": ["SILICONFLOW"],
                    "match": {"firstSegment": ["zai-org", "tencent"], "lastSegmentPrefix": ["glm-", "hunyuan-"]},
                    "control": "toggle_only",
                    "parameterLabel": "enable_thinking",
                    "enable": [{"path": "enable_thinking", "value": true}],
                    "disable": [{"path": "enable_thinking", "value": false}]
                  },
                  {
                    "id": "siliconflow-thinking-budget",
                    "providers": ["SILICONFLOW"],
                    "control": "levels",
                    "parameterLabel": "thinking_budget",
                    "enable": [{"path": "enable_thinking", "value": true}],
                    "disable": [{"path": "enable_thinking", "value": false}],
                    "options": [
                      {"id": "128", "label": "128", "path": "thinking_budget", "value": 128},
                      {"id": "4096", "label": "4096", "path": "thinking_budget", "value": 4096},
                      {"id": "8192", "label": "8192", "path": "thinking_budget", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "thinking_budget", "value": 16384},
                      {"id": "32768", "label": "32768", "path": "thinking_budget", "value": 32768}
                    ]
                  },
                  {
                    "id": "zhipu-glm-53-required-effort",
                    "providers": ["ZHIPU"],
                    "match": {"modelContains": ["glm-5.3", "glm-5-3"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "zhipu-glm-52-effort",
                    "providers": ["ZHIPU"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:5[.-][2-9]|[6-9])"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "zhipu-glm-thinking-toggle",
                    "providers": ["ZHIPU"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:4[.-][5-9]|[5-9])"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "anthropic-extended-budget",
                    "providers": ["ANTHROPIC", "ANTHROPIC_GENERIC"],
                    "match": {"modelPrefix": ["claude-3"]},
                    "control": "levels",
                    "parameterLabel": "thinking.budget_tokens",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "1024", "label": "1024", "path": "thinking.budget_tokens", "value": 1024},
                      {"id": "4096", "label": "4096", "path": "thinking.budget_tokens", "value": 4096},
                      {"id": "8192", "label": "8192", "path": "thinking.budget_tokens", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "thinking.budget_tokens", "value": 16384}
                    ]
                  },
                  {
                    "id": "anthropic-adaptive-effort",
                    "providers": ["ANTHROPIC", "ANTHROPIC_GENERIC"],
                    "control": "levels",
                    "parameterLabel": "output_config.effort",
                    "enable": [
                      {"path": "thinking.type", "value": "adaptive"},
                      {"path": "thinking.display", "value": "summarized"}
                    ],
                    "options": [
                      {"id": "low", "label": "low", "path": "output_config.effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "output_config.effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "output_config.effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "openrouter-reasoning-budget",
                    "providers": ["OPENROUTER", "NOUS_PORTAL"],
                    "control": "levels",
                    "parameterLabel": "reasoning.max_tokens",
                    "disable": [{"path": "reasoning.enabled", "value": false}],
                    "options": [
                      {"id": "1024", "label": "1024", "path": "reasoning.max_tokens", "value": 1024},
                      {"id": "8192", "label": "8192", "path": "reasoning.max_tokens", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "reasoning.max_tokens", "value": 16384},
                      {"id": "32768", "label": "32768", "path": "reasoning.max_tokens", "value": 32768},
                      {"id": "65536", "label": "65536", "path": "reasoning.max_tokens", "value": 65536}
                    ]
                  },
                  {
                    "id": "xai-grok-46-reasoning-effort",
                    "providers": ["XAI"],
                    "match": {"modelRegex": ["(?:^|/)grok-4[.-]6(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"}
                    ]
                  },
                  {
                    "id": "xai-grok-45-reasoning-effort",
                    "providers": ["XAI"],
                    "match": {"modelRegex": ["(?:^|/)grok-4[.-]5(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"}
                    ]
                  },
                  {
                    "id": "xai-grok-reasoning-effort",
                    "providers": ["XAI"],
                    "match": {"modelContains": ["grok"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "nvidia-reasoning-effort",
                    "providers": ["NVIDIA"],
                    "match": {"modelContains": ["gpt-oss", "nemotron"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "disable": [{"path": "reasoning_effort", "value": "none"}],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "nvidia-template-thinking-toggle",
                    "providers": ["NVIDIA"],
                    "control": "toggle_only",
                    "parameterLabel": "chat_template_kwargs.enable_thinking",
                    "enable": [{"path": "chat_template_kwargs.enable_thinking", "value": true}],
                    "disable": [{"path": "chat_template_kwargs.enable_thinking", "value": false}]
                  },
                  {
                    "id": "mnn-llama-template-thinking-toggle",
                    "providers": ["MNN", "LLAMA_CPP"],
                    "control": "toggle_only",
                    "parameterLabel": "enable_thinking",
                    "enable": [{"path": "enable_thinking", "value": true}],
                    "disable": [{"path": "enable_thinking", "value": false}]
                  },
                  {
                    "id": "opencode-gemini-thinking-level",
                    "providers": ["OPENCODE"],
                    "match": {"firstSegment": ["google"], "lastSegmentPrefix": ["gemini-"]},
                    "control": "levels",
                    "parameterLabel": "thinkingLevel",
                    "enable": [{"path": "thinkingConfig.includeThoughts", "value": true}],
                    "options": [
                      {"id": "LOW", "label": "LOW", "path": "thinkingConfig.thinkingLevel", "value": "LOW"},
                      {"id": "MEDIUM", "label": "MEDIUM", "path": "thinkingConfig.thinkingLevel", "value": "MEDIUM"},
                      {"id": "HIGH", "label": "HIGH", "path": "thinkingConfig.thinkingLevel", "value": "HIGH"}
                    ]
                  },
                  {
                    "id": "opencode-anthropic-effort",
                    "providers": ["OPENCODE"],
                    "match": {"firstSegment": ["anthropic", "minimax"], "lastSegmentPrefix": ["claude-", "minimax-"]},
                    "control": "levels",
                    "parameterLabel": "output_config.effort",
                    "enable": [
                      {"path": "thinking.type", "value": "adaptive"},
                      {"path": "thinking.display", "value": "summarized"}
                    ],
                    "options": [
                      {"id": "low", "label": "low", "path": "output_config.effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "output_config.effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "output_config.effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "opencode-zhipu-glm-effort",
                    "providers": ["OPENCODE"],
                    "match": {"firstSegment": ["zhipu", "zai-org", "thudm"], "lastSegmentContains": ["glm"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "opencode-responses-effort",
                    "providers": ["OPENCODE"],
                    "match": {"firstSegment": ["openai", "azure", "xai"], "modelContains": ["gpt-", "grok-", "codex"]},
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "enable": [
                      {"path": "reasoning.summary", "value": "auto"},
                      {"path": "include", "value": ["reasoning.encrypted_content"]}
                    ],
                    "disable": [{"path": "reasoning.effort", "value": "none"}],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning.effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning.effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning.effort", "value": "xhigh"}
                    ]
                  },
                  {
                    "id": "opencode-chat-effort",
                    "providers": ["OPENCODE"],
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "aliyun-qwen-thinking-toggle",
                    "providers": ["ALIYUN"],
                    "match": {"modelContains": ["qwen"]},
                    "control": "toggle_only",
                    "parameterLabel": "enable_thinking",
                    "enable": [{"path": "enable_thinking", "value": true}],
                    "disable": [{"path": "enable_thinking", "value": false}]
                  },
                  {
                    "id": "xunfei-spark-x-thinking-toggle",
                    "providers": ["XUNFEI"],
                    "match": {"modelRegex": ["(?:^|/)spark-x(?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "mistral-reasoning-effort",
                    "providers": ["MISTRAL"],
                    "match": {"modelPrefix": ["mistral-small", "mistral-medium-3-5"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "off", "label": "none", "path": "reasoning_effort", "value": "none"}
                    ]
                  },
                  {
                    "id": "minimax-m3-thinking-toggle",
                    "providers": ["MINIMAX"],
                    "match": {"modelRegex": ["(?:^|/)minimax-m3(?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "adaptive"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "alipay-bailing-ring-reasoning-effort",
                    "providers": ["ALIPAY_BAILING"],
                    "match": {"modelRegex": ["(?:^|/)ring-2[.-]6-1t(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning.effort", "value": "xhigh"}
                    ]
                  },
                  {
                    "id": "alipay-bailing-ling-thinking-toggle",
                    "providers": ["ALIPAY_BAILING"],
                    "match": {"modelRegex": ["(?:^|/)ling-3[.-]0-flash(?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "infiniai-glm-53-reasoning-effort",
                    "providers": ["INFINIAI"],
                    "match": {"modelContains": ["glm-5.3", "glm-5-3"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "infiniai-glm-reasoning-effort",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:5[.-][2-9]|[6-9])"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "infiniai-glm-thinking-toggle",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:4[.-][5-9]|[5-9])"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "infiniai-kimi-k3-reasoning-effort",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k3(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "infiniai-kimi-k27-forced-thinking",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-]7(?:[.-]|$)"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "infiniai-kimi-k2-thinking-toggle",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-][56](?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "infiniai-minimax-m3-thinking-toggle",
                    "providers": ["INFINIAI"],
                    "match": {"modelRegex": ["(?:^|/)minimax-m3(?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "adaptive"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "infiniai-deepseek-v4-reasoning-effort",
                    "providers": ["INFINIAI"],
                    "match": {"modelContains": ["deepseek-v4"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "infiniai-qwen-thinking-toggle",
                    "providers": ["INFINIAI"],
                    "match": {"modelContains": ["qwen"]},
                    "control": "toggle_only",
                    "parameterLabel": "enable_thinking",
                    "enable": [{"path": "enable_thinking", "value": true}],
                    "disable": [{"path": "enable_thinking", "value": false}]
                  },
                  {
                    "id": "infiniai-mimo-thinking-toggle",
                    "providers": ["INFINIAI"],
                    "match": {"modelContains": ["mimo"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "other-deepseek-responses-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {
                      "modelContains": ["deepseek"],
                      "endpointSuffix": ["/responses"]
                    },
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning.effort", "value": "low"},
                      {"id": "max", "label": "max", "path": "reasoning.effort", "value": "max"},
                      {"id": "off", "label": "off", "path": "reasoning.effort", "value": "none"}
                    ]
                  },
                  {
                    "id": "other-openai-responses-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {
                      "modelRegex": ["(?:^|/)(?:gpt-5(?:[.-]|$)|o[134](?:[.-]|$))"],
                      "endpointSuffix": ["/responses"]
                    },
                    "control": "levels",
                    "parameterLabel": "reasoning.effort",
                    "enable": [
                      {"path": "reasoning.summary", "value": "auto"},
                      {"path": "include", "value": ["reasoning.encrypted_content"]}
                    ],
                    "disable": [{"path": "reasoning.effort", "value": "none"}],
                    "options": [
                      {"id": "medium", "label": "medium", "path": "reasoning.effort", "value": "medium"},
                      {"id": "low", "label": "low", "path": "reasoning.effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning.effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning.effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning.effort", "value": "max"},
                      {"id": "minimal", "label": "minimal", "path": "reasoning.effort", "value": "minimal"},
                      {"id": "none", "label": "none", "path": "reasoning.effort", "value": "none"}
                    ]
                  },
                  {
                    "id": "other-deepseek-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelContains": ["deepseek"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
                    ]
                  },
                  {
                    "id": "other-glm-53-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelContains": ["glm-5.3", "glm-5-3"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "other-glm-current-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:5[.-][2-9]|[6-9])"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}],
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "other-glm-thinking-toggle",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)glm-(?:4[.-][5-9]|[5-9])"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "other-kimi-k3-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k3(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"}
                    ]
                  },
                  {
                    "id": "other-kimi-k27-forced-thinking",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-]7(?:[.-]|$)"]},
                    "control": "unsupported"
                  },
                  {
                    "id": "other-kimi-k2-thinking-toggle",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)kimi-k2[.-][56](?:[.-]|$)"]},
                    "control": "toggle_only",
                    "parameterLabel": "thinking.type",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "disable": [{"path": "thinking.type", "value": "disabled"}]
                  },
                  {
                    "id": "other-grok-46-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)grok-4[.-]6(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"}
                    ]
                  },
                  {
                    "id": "other-grok-45-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)grok-4[.-]5(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"}
                    ]
                  },
                  {
                    "id": "other-claude-adaptive-thinking",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)claude-(?:[^/]+-)?4[-.]?[6-9](?:[-.]|$)"]},
                    "control": "levels",
                    "parameterLabel": "output_config.effort",
                    "enable": [
                      {"path": "thinking.type", "value": "adaptive"},
                      {"path": "thinking.display", "value": "summarized"}
                    ],
                    "options": [
                      {"id": "medium", "label": "medium", "path": "output_config.effort", "value": "medium"},
                      {"id": "low", "label": "low", "path": "output_config.effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "output_config.effort", "value": "high"}
                    ]
                  },
                  {
                    "id": "other-claude-extended-thinking",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)claude-(?:[^/]+-)?(?:3(?:[-.]|$)|4[-.]?[0-5](?:[-.]|$))"]},
                    "control": "levels",
                    "parameterLabel": "thinking.budget_tokens",
                    "enable": [{"path": "thinking.type", "value": "enabled"}],
                    "options": [
                      {"id": "4096", "label": "4096", "path": "thinking.budget_tokens", "value": 4096},
                      {"id": "1024", "label": "1024", "path": "thinking.budget_tokens", "value": 1024},
                      {"id": "8192", "label": "8192", "path": "thinking.budget_tokens", "value": 8192},
                      {"id": "16384", "label": "16384", "path": "thinking.budget_tokens", "value": 16384}
                    ]
                  },
                  {
                    "id": "other-gemini-3-thinking-level",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)gemini-3(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "thinkingLevel",
                    "required": true,
                    "enable": [{"path": "generationConfig.thinkingConfig.includeThoughts", "value": true}],
                    "options": [
                      {"id": "MEDIUM", "label": "MEDIUM", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MEDIUM"},
                      {"id": "LOW", "label": "LOW", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "LOW"},
                      {"id": "HIGH", "label": "HIGH", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "HIGH"},
                      {"id": "MINIMAL", "label": "MINIMAL", "path": "generationConfig.thinkingConfig.thinkingLevel", "value": "MINIMAL"}
                    ]
                  },
                  {
                    "id": "other-gemini-25-thinking-budget",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)gemini-2\\.5(?:[.-]|$)"]},
                    "control": "levels",
                    "parameterLabel": "thinkingBudget",
                    "enable": [{"path": "generationConfig.thinkingConfig.includeThoughts", "value": true}],
                    "disable": [
                      {"path": "generationConfig.thinkingConfig.includeThoughts", "value": false},
                      {"path": "generationConfig.thinkingConfig.thinkingBudget", "value": 0}
                    ],
                    "options": [
                      {"id": "8192", "label": "8192", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 8192},
                      {"id": "1024", "label": "1024", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 1024},
                      {"id": "4096", "label": "4096", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 4096},
                      {"id": "16384", "label": "16384", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 16384},
                      {"id": "32768", "label": "32768", "path": "generationConfig.thinkingConfig.thinkingBudget", "value": 32768}
                    ]
                  },
                  {
                    "id": "other-openai-chat-reasoning-effort",
                    "providers": ["OTHER"],
                    "match": {"modelRegex": ["(?:^|/)(?:gpt-5(?:[.-]|$)|o[134](?:[.-]|$))"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "options": [
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"},
                      {"id": "minimal", "label": "minimal", "path": "reasoning_effort", "value": "minimal"},
                      {"id": "none", "label": "none", "path": "reasoning_effort", "value": "none"}
                    ]
                  }
                ]
                """.trimIndent()

        fun forProvider(providerTypeId: String): String {
                val provider = providerTypeId.trim().uppercase(Locale.US)
                if (provider.isEmpty()) {
                        return "[]"
                }
                synchronized(providerRulesCacheLock) {
                        providerRulesCache[provider]?.let { return it }

                        val source = JSONArray(DEFAULT_JSON)
                        val target = JSONArray()
                        for (index in 0 until source.length()) {
                                val rule = source.optJSONObject(index) ?: continue
                                val providers = rule.optJSONArray("providers")
                                val providerTypeIds = rule.optJSONArray("providerTypeIds")
                                if (providers.containsProvider(provider) || providerTypeIds.containsProvider(provider)) {
                                        val configRule = JSONObject(rule.toString())
                                        configRule.remove("providers")
                                        configRule.remove("providerTypeIds")
                                        configRule.optJSONArray("options")?.let { options ->
                                                configRule.put("options", ThinkingQualityOptionOrdering.sortJsonOptions(options))
                                        }
                                        target.put(configRule)

                                }
                        }
                        return target.toString().also { providerRulesCache[provider] = it }
                }
        }

        fun missingPresetIdsForProvider(
                providerTypeId: String,
                currentConfigurations: String
        ): List<String> {
                val currentRules = rulesArray(currentConfigurations) ?: return emptyList()
                val existingIds = currentRules.ruleIds()
                return rulesArray(forProvider(providerTypeId))
                        ?.ruleIds()
                        ?.filterNot(existingIds::contains)
                        .orEmpty()
        }

        fun mergeSelectedPresetsForProvider(
                providerTypeId: String,
                currentConfigurations: String,
                selectedPresetIds: Set<String>
        ): String {
                val currentRules = rulesArray(currentConfigurations) ?: return currentConfigurations
                val selectedIds = selectedPresetIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                if (selectedIds.isEmpty()) {
                        return currentConfigurations
                }

                val defaultRules = rulesArray(forProvider(providerTypeId))?.ruleObjects().orEmpty()
                val defaultOrder = defaultRules.mapIndexedNotNull { index, rule ->
                        rule.optString("id", "").trim().takeIf { it.isNotEmpty() }?.let { it to index }
                }.toMap()
                if (defaultOrder.isEmpty()) {
                        return currentConfigurations
                }

                val mergedRules = currentRules.ruleObjects().toMutableList()
                val existingIds = mergedRules.mapTo(mutableSetOf()) { it.optString("id", "").trim() }
                defaultRules.forEach { preset ->
                        val presetId = preset.optString("id", "").trim()
                        if (presetId !in selectedIds || presetId.isEmpty() || presetId in existingIds) {
                                return@forEach
                        }

                        val presetOrder = defaultOrder.getValue(presetId)
                        val nextPresetIndex = mergedRules.indexOfFirst { rule ->
                                defaultOrder[rule.optString("id", "").trim()]?.let { it > presetOrder } == true
                        }
                        val insertAt = if (nextPresetIndex >= 0) {
                                nextPresetIndex
                        } else {
                                val previousPresetIndex = mergedRules.indexOfLast { rule ->
                                        defaultOrder[rule.optString("id", "").trim()]?.let { it < presetOrder } == true
                                }
                                if (previousPresetIndex >= 0) previousPresetIndex + 1 else mergedRules.size
                        }
                        mergedRules.add(insertAt, JSONObject(preset.toString()))
                        existingIds += presetId
                }

                return JSONArray().apply { mergedRules.forEach { put(it) } }.toString()
        }

        private fun rulesArray(raw: String): JSONArray? {
                val text = raw.trim().ifEmpty { "[]" }
                return runCatching {
                        when {
                                text.startsWith("[") -> JSONArray(text)
                                text.startsWith("{") -> {
                                        val container = JSONObject(text)
                                        container.optJSONArray("rules") ?: JSONArray().put(container)
                                }
                                else -> JSONArray(text)
                        }
                }.getOrNull()
        }

        private fun JSONArray.ruleObjects(): List<JSONObject> =
                buildList {
                        for (index in 0 until length()) {
                                optJSONObject(index)?.let { add(it) }
                        }
                }

        private fun JSONArray.ruleIds(): Set<String> =
                buildSet {
                        for (index in 0 until length()) {
                                optJSONObject(index)
                                        ?.optString("id", "")
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let(::add)
                        }
                }

}

private fun JSONArray?.containsProvider(provider: String): Boolean {
        if (this == null) {
                return false
        }
        for (index in 0 until length()) {
                if (optString(index, "").trim().uppercase(Locale.US) == provider) {
                        return true
                }
        }
        return false
}
