---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix-some-bug
---

# Token Statistics Legacy Identity Runtime Repair

## Background

The v20-to-v21 token history import preserved display-oriented provider values
such as `DEEPSEEK/old-configuration` and intentionally left `configId` empty.
The token statistics page used those values directly for logo lookup and always
opened a configuration-scoped price editor.

## Intent

Normalize legacy provider identities in memory. Use provider/model pricing for
records without a real configuration ID, while keeping configuration pricing
unchanged for newly recorded usage.

## Scope

- Do not change the Room schema or add a database migration.
- Keep raw legacy provider values available for historical row separation.
- Normalize only known built-in provider prefixes for logo and pricing keys.
- Keep custom provider identities unchanged.

## Steps

1. [DONE] Add runtime provider and provider/model identity normalization.
2. [DONE] Make legacy rows use provider/model price scope.
3. [DONE] Add pure logic regression tests.
4. Review the final diff and release build result.
