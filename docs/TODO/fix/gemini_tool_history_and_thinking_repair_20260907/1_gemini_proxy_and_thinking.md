---
title: Preserve Gemini package proxy correlation and thinking boundaries
status: done
---

# Preserve Gemini package proxy correlation and thinking boundaries

## Existing behavior

The provider remembers the Gemini function name as both its response identity
and the name used to match a `tool_result`. For package proxies those are not
the same value. Gemini history therefore appends `User cancelled` after a
successful result.

The provider writes `thought_signature` back into a request Part. Gemini uses
`thoughtSignature`. A reasoning-required model also receives
`includeThoughts: true` for a title request whose caller explicitly disables
thinking.

## Change

- Represent the provider response identity and execution-result matching name
  separately in an open tool call
- Derive package proxy matching names from both OpenAI-style `arguments` and
  Gemini-style `args`
- Emit Gemini thought signatures as `thoughtSignature`
- Suppress returned thought text when the caller has disabled thinking without
  changing the model-required reasoning level

## Verification

- Add source-level tests for Gemini package proxy matching and request thinking
  configuration
- Do not run build or test commands unless requested

## Result

- [DONE] 1_gemini_proxy_and_thinking.md
- Package-proxy results now match the concrete executable tool while preserving
  `package_proxy` as Gemini's function-response name.
- Gemini thought signatures are serialized with `thoughtSignature`.
- Disabled-thinking requests suppress thought text both in the request
  configuration and in response extraction.
- Thinking-mode state is scoped to one response instead of the shared provider.
