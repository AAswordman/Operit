# Capture Commentary Reasoning

## Previous Behavior

The DeepSeek Responses stream renders `message.phase=commentary` as a second visible thinking block beside the `reasoning` item. Later tool continuations also lacked that commentary representation.

## Change

Store the completed commentary message as DeepSeek Responses hidden metadata only. Do not emit it as `<think>`. When rebuilding the next `input`, restore it as a `reasoning` item with `reasoning_text` before the related `function_call` items. Visible thinking stays on the original `reasoning` item.

## Expected Result

Every DeepSeek tool continuation contains all persisted thought representations in original order. Commentary text is absent from the user-visible message, and hidden metadata remains absent from user-visible request content.

[DONE]
