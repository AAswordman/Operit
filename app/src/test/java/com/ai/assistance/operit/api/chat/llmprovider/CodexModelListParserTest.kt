package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexModelListParserTest {
    @Test
    fun parsesOpenCodeCatalogAndExpandsAllowedModes() {
        val models = CodexModelListFetcher.parseModels(
            """
            {
              "openai": {
                "models": {
                  "gpt-5.6-luna": {
                    "id": "gpt-5.6-luna",
                    "name": "GPT-5.6 Luna",
                    "experimental": {
                      "modes": {
                        "fast": {"provider": {"body": {"service_tier": "priority"}}},
                        "pro": {"provider": {"body": {"reasoning": {"mode": "pro"}}}}
                      }
                    }
                  },
                  "gpt-5.4": {
                    "id": "gpt-5.4",
                    "name": "GPT-5.4",
                    "experimental": {
                      "modes": {
                        "fast": {"provider": {"body": {"service_tier": "priority"}}}
                      }
                    }
                  },
                  "gpt-5.5-pro": {
                    "id": "gpt-5.5-pro",
                    "name": "GPT-5.5 Pro"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            setOf("gpt-5.6-luna", "gpt-5.6-luna-fast", "gpt-5.4", "gpt-5.4-fast"),
            models.map { it.id }.toSet(),
        )
        assertEquals("GPT-5.6 Luna Fast", models.first { it.id == "gpt-5.6-luna-fast" }.name)
    }
}
