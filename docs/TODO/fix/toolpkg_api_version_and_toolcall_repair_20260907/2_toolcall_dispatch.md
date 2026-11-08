# 2. toolCall dispatch

## Previous behavior

`toolCall` required `__operitCurrentCallId` and dispatched through call-specific native methods. The native dispatch implementation did not use the propagated ToolPkg API version, so the global requirement only rejected valid registrations and host-owned invocations.

## Change

Restore direct asynchronous and streaming native dispatch for `toolCall`. Remove the unused call-ID-specific native methods and their unused API-version parameters. Keep ToolPkg API version checks in the JavaScript facade that selects versioned APIs.

## Expected result

`toolCall` remains Promise-based and works from every supported JavaScript entry point without relying on a mutable global execution ID.

## Completion

- [DONE] Generic native dispatch restored and obsolete call-ID dispatch removed
