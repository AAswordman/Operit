# Codex Settings And Usage

## Existing State

`ModelApiSettingsSection` already detects `OPENAI_CODEX` and renders login,
fixed endpoint, generic model controls, and the generic image/audio/video and
ToolCall switches. OAuth state is provided by `CodexAuthManager`.

The official Codex CLI reads `GET https://chatgpt.com/backend-api/wham/usage`.
The response exposes `plan_type` and rate-limit windows with usage percentage,
duration, and reset timestamps. The response slots are not the UI periods: the
period is identified by `limit_window_seconds`, and the five-hour window may be
absent.

## Intended Change

- Keep login/logout and model selection available.
- Show account identity, plan name, and a compact quota capsule.
- Show five-hour quota as an explicit no-data row when the primary window is absent.
- Show the seven-day quota from the secondary window with remaining percentage,
  progress, and reset countdown.
- Refresh only from an explicit user action and never request token history.
- Do not log authorization headers or response bodies.
- Store one latest snapshot per Codex account in a dedicated DataStore so model
  configuration changes do not remove the visible result.

## Completion

[DONE]
