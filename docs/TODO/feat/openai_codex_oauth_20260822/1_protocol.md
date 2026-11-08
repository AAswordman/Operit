# OAuth And Codex Protocol

## Sources

- https://developers.openai.com/codex/auth.md
- https://developers.openai.com/codex/models.md
- https://github.com/openai/codex/blob/main/codex-rs/login/src/server.rs
- https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/plugin/openai/codex.ts

## Contract

- Issuer: `https://auth.openai.com`
- Authorization endpoint: `/oauth/authorize`
- Token endpoint: `/oauth/token`
- Revoke endpoint: `/oauth/revoke`
- Codex Responses endpoint: `https://chatgpt.com/backend-api/codex/responses`
- Codex usage endpoint: `https://chatgpt.com/backend-api/wham/usage`, read manually from the settings page
- OpenCode model catalog: `https://models.opencode.ai/api.json`, reading `openai.models`
- Catalog requests are unauthenticated; Codex OAuth credentials are only used for inference requests.
- OpenCode `experimental.modes.fast` entries are exposed as `<model>-fast` and send `service_tier=priority` with the base model ID.
- OAuth client ID: the public ID used by the current Codex client
- Authorization code exchange: form encoded PKCE request
- Refresh: current Codex token request format, with rotated refresh credentials
- Account routing: `ChatGPT-Account-ID` from the token claims
- Loopback callback: `http://localhost:1455/auth/callback`
- Authorization scope: `openid profile email offline_access`
- Usage payload: `plan_type` plus `rate_limit.primary_window` and `rate_limit.secondary_window`; each window exposes `used_percent`, `limit_window_seconds`, and `reset_at`
- A missing primary window is displayed as an explicit unavailable five-hour row; no token activity profile is requested.
- Codex PDF attachments use Responses `input_file` with `filename` and `file_data` data URI fields.

OAuth access and refresh credentials are never stored in `ModelConfigData`,
exported model backups, request logs, or custom request headers.
