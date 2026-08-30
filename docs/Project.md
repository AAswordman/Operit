# NonO Assistant (OperitX)

> Mobile AI Agent — forked from AAswordman/Operit, extended toward full multi-AI orchestration capabilities

## Overview

NonO Assistant (OperitX) is a mobile AI agent platform for Android with web remote client support for iPad/desktop. It extends the Operit base with multi-AI orchestration, global memory, context budget management, and a provider-agnostic abstraction layer.

## Tech Stack

| Layer | Technology |
|---|---|
| Platform | Android (Kotlin, Jetpack Compose) |
| Native | C++ via CMake, Rust (native_ripgrep) |
| JS Engine | QuickJS |
| Terminal | Termux integration (fork) |
| LLM Runtime | llama.cpp, MNN |
| Web Client | TypeScript (web-chat/) |
| Build | Gradle (Kotlin DSL), AGP 8.13.2, Kotlin 2.2.21 |
| CI | GitHub Actions |

## Project Identity

| Field | Value |
|---|---|
| Package name | `com.ai.nonoassistance` |
| Display name | NonO Assistant |
| Root project | `OperitX` |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| ABI | arm64-v8a only |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer (4 modes)                    │
│   Chat UI │ IDE UI │ CLI UI │ Image-gen UI                │
├─────────────────────────────────────────────────────────┤
│                  Mode Switcher / Session State             │
├─────────────────────────────────────────────────────────┤
│  Orchestration Engine (Leader/Worker Multi-AI)            │
├───────────────┬───────────────┬────────────────┬──────────┤
│ Provider       │ Tool Execution │ Skill/MCP       │ Memory & │
│ Abstraction    │ Engine         │ Manager         │ Context  │
│ Layer          │ (→ Terminal)   │                 │ Engine   │
├───────────────┴───────────────┴────────────────┴──────────┤
│         Terminal Module (Termux-style + root exec)          │
├─────────────────────────────────────────────────────────┤
│   Local Storage: Room DB / Encrypted File Store / .opk     │
└─────────────────────────────────────────────────────────┘
```

## Gradle Modules

| Module | Purpose |
|---|---|
| `:app` | Main Android application |
| `:provider` | AI Provider Abstraction Layer (NEW) |
| `:orchestration` | Multi-AI Leader/Worker Engine (NEW) |
| `:memory` | Global Memory Store + Context Budget Manager (NEW) |
| `:terminal` | Termux integration + shell execution |
| `:llama` | llama.cpp JNI bindings |
| `:mnn` | MNN inference engine |
| `:quickjs` | QuickJS JavaScript engine |
| `:showerclient` | Shower client module |
| `:dragonbones` | DragonBones animation |
| `:mmd` | MMD animation |
| `:fbx` | FBX model support |

## Key Configuration

| Setting | Default |
|---|---|
| Context budget per session | 256K tokens |
| Max worker agents per session | 3 |
| Max retry per subtask | 3 |
| Auto-summarize threshold | 80% of budget |

## Providers (Phase 1 ✅)

| Provider | Status |
|---|---|
| DeepSeek | ✅ Pricing configured |
| Google Gemini | ✅ Pricing configured |
| OpenAI | ✅ Pricing configured (bonus) |

**Phase 1 deliverables:**
- AIProvider interface + ProviderRegistry (pluggable)
- OperitProviderAdapter (bridges to existing AIService system)
- ModelPricingService + PricingFetcher (remote JSON, 24h cache)
- Model Selection UI (Jetpack Compose, Material3)
- model-pricing.json (DeepSeek, Gemini, OpenAI pricing data)

---

## Decision Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-08-29 | Package: `com.ai.nonoassistance` | Avoid conflict with original Operit package |
| 2026-08-29 | App name: NonO Assistant | User preference |
| 2026-08-29 | Fork in place (no new repo) | Work from current repo, rename in code |
| 2026-08-29 | 3 workers max per session | Cost control for user |
| 2026-08-29 | 256K token default budget | Supports large context window models |
| 2026-08-29 | Skeleton modules in Phase 0 | User preference for full module structure upfront |
| 2026-08-29 | Providers: DeepSeek + Gemini first | User selection — pluggable, add more later |
| 2026-08-29 | Dual provider system (old + new) | User choice — wrap existing AIService with new AIProvider abstraction |
| 2026-08-29 | Pricing via remote JSON | Fetchable from GitHub, 24h cache, graceful fallback |
| 2026-08-29 | model-pricing.json includes OpenAI | Bonus — already pluggable, useful for comparison |
