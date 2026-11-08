---
title: Gemini tool history and thinking repair
status: done
---

# Gemini tool history and thinking repair

## Original state

Gemini records a package-proxy function call under `package_proxy`, while the
execution result identifies the concrete `package:tool`. The history adapter
compares the two different names and writes a fabricated cancellation response.

Gemini thought signatures also use a non-standard request field name. Required
reasoning models expose thought text for title generation even when that
functional call disables thinking.

## Intended result

- Match package tool results by their concrete execution name and answer Gemini
  with the original proxy function name
- Send Gemini thought signatures using the API field name `thoughtSignature`
- Keep required model reasoning active while omitting visible thought text when
  the caller disables thinking

## Scope

- Structured provider tool-call bridge
- Gemini history and thinking request handling
- Focused JVM regression tests

## Steps

- [DONE] 1_gemini_proxy_and_thinking.md
