package com.ai.assistance.operit.data.collects

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object ModelThinkingConfigDefaults {
        val DEFAULT_JSON: String =
                """
                [
                  {
                    "id": "openai-chat-reasoning-effort",
                    "providers": ["OPENAI", "OPENAI_GENERIC"],
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
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "max", "label": "max", "path": "reasoning_effort", "value": "max"}
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
                    "id": "xai-grok-reasoning-effort",
                    "providers": ["XAI"],
                    "match": {"modelContains": ["grok"]},
                    "control": "levels",
                    "parameterLabel": "reasoning_effort",
                    "required": true,
                    "options": [
                      {"id": "low", "label": "low", "path": "reasoning_effort", "value": "low"},
                      {"id": "medium", "label": "medium", "path": "reasoning_effort", "value": "medium"},
                      {"id": "high", "label": "high", "path": "reasoning_effort", "value": "high"},
                      {"id": "xhigh", "label": "xhigh", "path": "reasoning_effort", "value": "xhigh"}
                    ]
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
                  }
                ]
                """.trimIndent()

        fun forProvider(providerTypeId: String): String {
                val provider = providerTypeId.trim().uppercase(Locale.US)
                if (provider.isEmpty()) {
                        return "[]"
                }

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
                                target.put(configRule)
                        }
                }
                return target.toString()
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
