---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix/provider-logos-token-statistics-main
status: completed
---

# Codex Settings, Quota, And PDF Input

## Background

The Codex OAuth provider, dynamic model catalog, and Responses transport are
already available. The settings page still exposes the generic provider form,
and Codex attachments do not yet include PDF files as Responses input files.

## Intent

Add a compact Codex-specific settings surface with account information and a
manual subscription quota view. Enable image/PDF direct input and ToolCall for
Codex without changing the existing XML tool execution bridge.

## Scope

- Codex-only compact settings block and localized labels
- Read-only `GET /backend-api/wham/usage` quota client
- Five-hour and seven-day quota presentation, including an absent five-hour row
- Codex PDF attachment conversion to Responses `input_file`
- Targeted parser/request tests and protocol documentation
- Remote Release build through the local builder service

## Non-Goals

- No XML parser or ToolCall execution architecture changes
- No token activity history or `/wham/profiles/me` request
- No audio/video controls for Codex
- No API key or endpoint editing for Codex

## Steps

1. [DONE] Add Codex usage models, client, and settings state
2. [DONE] Replace the Codex generic settings rows with the compact surface
3. [DONE] Add Codex PDF `input_file` request construction
4. [DONE] Add tests, docs, and completion records
5. [DONE] Inspect the diff and trigger the remote Release build
6. [DONE] Correct window classification and persist the latest usage snapshot
