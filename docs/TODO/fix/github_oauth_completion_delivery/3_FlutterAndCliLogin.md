---
fork: https://github.com/AAswordman/Operit
---

# Flutter And CLI Login

## Existing State

The broker service already keeps the pending delivery credential and persists
the claimed GitHub authentication. The Flutter market dialog owns its WebView,
and the CLI owns a loopback listener. The CLI help text and Core comments still
describe the earlier login surface imprecisely.

## Intended Change

- Keep Flutter login inside the market dialog: start the broker transaction,
  navigate its WebView, intercept the completion URL, then claim through Core.
- Keep CLI login in `market auth login`: reserve loopback, print the URL for
  the user to open, wait for the callback, then claim through Core.
- Remove the obsolete token-login command from CLI help.
- Keep `market auth login` out of Core command help because Core does not own
  browser interaction.
- Allow users to close the Flutter login dialog before completion.
- Use App-oriented language in the Core and client documentation.

## Verification

- Confirm the Flutter market entry point constructs `GitHubOAuthLoginDialog`.
- Confirm the CLI intercepts only `market auth login` and does not launch a
  browser process.
- Search the source tree for the removed token-login help and callback-host
  language.

[DONE]
