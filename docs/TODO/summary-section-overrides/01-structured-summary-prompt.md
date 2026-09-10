# Structured Summary Prompt

## Old Implementation

The proposed implementation finds headers in a mutable prompt string. Removing one section can
remove the header needed as the boundary for a previous section.

## Change

Represent the built-in sections as data and assemble the prompt from the resolved section list.

## Expected Result

Disabling and editing any number of sections cannot remove unrelated enabled sections.

[DONE]
