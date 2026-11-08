# 4. Provider Capabilities And Token Types

## Previous State

`OpenAIProvider` inspected `ApiProviderType` to decide whether a request should
include `stream_options.include_usage`. This couples the generic compatibility
base to concrete provider identities. The statistics integration also narrowed
the local MNN and llama.cpp token counters from `Long` to `Int`, then converted
the values back only at API boundaries.

## Intended Change

Append the stream usage request field directly while constructing requests for
native OpenAI, DeepSeek, and Kimi. Generic OpenAI-compatible providers do not add
the field. Keep local token counts as `Long` through generation finalization and
statistics normalization.

## Expected State

Adding a provider no longer requires editing a central provider-type condition.
Local usage statistics preserve values larger than `Int.MAX_VALUE`. [DONE]
