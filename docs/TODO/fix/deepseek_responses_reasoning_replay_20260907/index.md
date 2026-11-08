---
fork_repository: local-worktree
scope: DeepSeek Responses thinking-mode tool continuations
---

# DeepSeek Responses Reasoning Replay

## Current State

DeepSeek Responses tool continuations can receive HTTP 400 with `The reasoning_text in the thinking mode must be passed back to the API.` The existing DeepSeek adapter preserves `reasoning` output items but does not preserve thought text emitted as an assistant `message` with `phase: commentary`.

## Intended Result

DeepSeek-specific request conversion must retain and replay both supported thought representations before the associated tool calls. OpenAI Responses must keep its encrypted reasoning contract unchanged.

## Scope

- `DeepseekProvider.kt`: DeepSeek Responses metadata capture and replay.
- `OpenAIProvider.kt`: a provider extension hook invoked at the completed Responses message item boundary.
- `DeepseekResponsesPayloadAdapterTest.kt`: regression coverage for commentary thought replay.
- `deepseek_responses_web_search.md`: protocol ownership and replay behavior.

## Steps

- [x] `01_capture_commentary_reasoning.md`: Persist and replay DeepSeek commentary thought output.
- [x] `02_regression_coverage.md`: Cover request ordering and hidden metadata removal.

[DONE]
