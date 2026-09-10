# Regression Coverage

## Previous Behavior

The existing DeepSeek regression only verifies a standalone `reasoning` item. It does not cover the `message.phase=commentary` representation observed in the failing request flow.

## Change

Add tests that derive metadata from a DeepSeek commentary message and assert that the next request places a `reasoning` item with `reasoning_text` before the corresponding function call and output.

## Expected Result

The test fails if commentary thought is rendered as visible thinking, if it is not persisted as hidden metadata, or if replay order separates a function call from its output.

[DONE]
