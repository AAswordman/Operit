---
title: Resource Translation
status: completed
---

# Resource Translation

## Baseline

`app/src/main/res/values/strings.xml` at the recorded upstream base contains 7,195 named string resources. It is the only source of resource names, values, XML escaping, and placeholder requirements.

## Implementation

Create `values-ro/strings.xml` with a Romanian translation for every source resource. Preserve each resource name and resource type. Preserve every printf-style placeholder and brace token exactly, including tokens inside JSON and escaped XML markup.

The completed file must not carry forward malformed or untranslated candidate values from pull request #796.

## Acceptance

- The Romanian resource key set equals the Chinese source key set
- No duplicate or extra resource names exist
- XML parses successfully
- Placeholder multisets match the source for every resource
- Romanian values do not contain accidental Han characters or source-language residue

[DONE]
