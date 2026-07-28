# WebChat Theme Binding

## Previous Behavior

`GET /chats/{id}/theme` confirms that the chat exists but resolves its theme from the active prompt. Several WebChat fields then read shared Android flows directly, so those fields can come from a different target than the returned snapshot.

## Change

Resolve the target from the requested chat metadata: group ID takes precedence, otherwise the stored character-card binding identifies the card. Build the full theme snapshot from that target and map all WebChat fields from the same snapshot. Structured rendering for a requested chat uses the same resolved snapshot.

The existing JSON fields remain available. New target metadata is additive only where needed by the Web client.

Existing chat records identify character cards by name. Duplicate card names remain ambiguous until the chat schema stores a stable card ID.

## Expected Result

Two WebChat tabs requesting themes for different character or group chats each receive their own resolved visual settings, including bubble glass and font enablement.

[DONE]
