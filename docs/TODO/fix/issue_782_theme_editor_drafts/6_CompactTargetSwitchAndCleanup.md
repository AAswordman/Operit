# Compact Target Switch And Cleanup

## Previous Behavior

The target selector and Basic tab both render the selected role identity. The selector changes only editor-local state, so applying that target's saved theme still requires selecting the same role from the chat dialog. The draft implementation mirrors the persistent preference manager with per-setting flows and a large optional-parameter save method.

## Change

- Replace the selector and Basic-tab identity card with one compact target card above the tabs.
- Make the active prompt the authoritative editor target. An accepted target selection calls `ActivePromptManager.setActivePrompt`, which activates the role and projects its saved theme without invoking chat-history auto-switch behavior.
- Preserve save, discard, and cancel ordering before target activation when the current draft is dirty.
- Replace the manager-shaped draft facade with one editor session containing values, baseline, dirty/reset state, and staged assets.
- Bind controls directly to draft values and synchronous updates.
- Delete the superseded immediate-save, current-projection copy, and declaration-only wrapper APIs.

## Compatibility Boundary

Keep released card and group theme prefixes, persisted theme key names, legacy default-theme migration, legacy vertical repeat values, WebChat response fields, and existing chat binding resolution. This refinement has not shipped, so its internal draft APIs require no compatibility layer.

## Expected Result

The screen uses one compact role selector, role/theme activation is immediate, chat history is unchanged, draft confirmation remains deterministic, and no old persistent-manager-shaped editor path remains.

[DONE]
