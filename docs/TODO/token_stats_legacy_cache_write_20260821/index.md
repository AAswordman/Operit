---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: fix-some-bug
---

# Token Statistics Legacy Cache Write Semantics

## Background

The v20-to-v21 history import did not populate `cacheWriteTokens`. For known
providers whose cache-write cost is included in input pricing, the current
calculator therefore reports a partial amount and marks every legacy request as
unknown.

## Intent

Interpret missing cache-write usage as a confirmed zero only for legacy rows and
known providers without independent cache-write billing. Preserve unknown values
for Anthropic, custom providers, and current configuration-bound records.

## Scope

- Repair the aggregate row in memory before price calculation.
- Do not change the Room schema, migration version, or stored records.
- Keep current provider usage recording unchanged.
- Add regression coverage for legacy, independent-billing, and current rows.

## Steps

1. [DONE] Add provider cache-write billing classification.
2. [DONE] Normalize eligible legacy aggregate rows in memory.
3. [DONE] Add cost calculation regression tests.
4. Review the final diff and build result.
