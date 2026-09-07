---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix/thinking-quality-slider
status: in_progress
---

# OpenAI Codex OAuth Provider

## Background

Operit has OpenAI Responses support and a separate GitHub OAuth flow, but it
does not have a ChatGPT/Codex login provider. Codex login uses a first-party
OAuth flow and the ChatGPT Codex Responses backend rather than the public API
key endpoint.

## Intent

Add a built-in Codex provider that keeps OAuth credentials outside model
configuration backups, refreshes access tokens during use, and reuses Operit's
existing Responses streaming, tool-call, reasoning, and usage pipelines.

The model catalog follows the current OpenCode OAuth model policy: explicitly
allowed models remain available, explicitly disallowed models are removed, and
new GPT model versions are accepted only above the current version threshold.

## Scope

- PKCE OAuth login, loopback callback, token refresh, revoke, and encrypted storage
- Codex provider factory, Responses payload/header adaptation, and model catalog
- Settings login state, model selection, onboarding readiness, and logout
- Provider identity, reasoning, pricing, logo, localization, tests, and docs
- Remote Android build through the repository Android Build workflow

## Steps

1. [ ] Add OAuth storage and callback coordination
2. [ ] Add Codex provider and fast model policy
3. [ ] Integrate settings, readiness, resources, and logo
4. [ ] Review static checks and tests
5. [ ] Trigger and record the remote Android build
