# Summary Callers And Tests

## Old Implementation

Automatic and manual summary generation only pass global custom rules.

## Change

Pass the selected model configuration's overrides through both generation paths and add focused
unit tests for the resolved prompt structure.

## Expected Result

Manual and automatic summaries use the same configuration, and regressions in combined section
changes are detected.

[DONE]
