---
fork: https://github.com/AAswordman/Operit
---

# App-Owned OAuth Coordination

## Existing State

The unshipped implementation placed generic browser callback registration and
navigation handling in `operit-host-api` and in Flutter's runtime browser
registry. That made an application interaction look like a Core Host
capability.

## Intended Change

- Remove `BrowserCallbackHost` and its transport types from `operit-host-api`.
- Keep the Core broker service responsible for pending credentials, validation,
  one-time claim, and persisted GitHub authentication.
- Let the Flutter market dialog present the authorization page and provide its
  captured completion URL to Core.
- Let the CLI reserve its loopback listener, print the authorization URL, wait
  for the callback, then provide that completion URL to Core.
- Remove the unused Flutter browser-registry callback channel.

## Verification

- Search the Operit2 source tree for the removed Host API names.
- `cargo test --manifest-path apps/cli/Cargo.toml oauth_callback` reaches the
  CLI package, but the package currently fails before tests execute because of
  unrelated Runtime Host Interaction imports, obsolete `setPermissionRequester`
  calls, and non-`Send` TUI tasks. No diagnostic references the OAuth callback
  files changed here.

[DONE]
