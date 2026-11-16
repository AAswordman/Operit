# OAuth AI Provider example

This package demonstrates the new **host-managed** OAuth extension. It requires an Operit build containing that extension; it refuses to register on older hosts.

Before importing the folder as a ToolPkg, replace the `.invalid` endpoints and placeholder `clientId` in `main.js` with a public client you are authorized to use. Register a permitted native loopback redirect with the authorization server. Do not add a client secret. The default is `http://127.0.0.1:<ephemeral-port>/oauth/callback`; fixed ports and explicitly configured `localhost` are supported when required by an existing registration.

Enable the package, select “OAuth 示例供应商” in model settings, and enter the full HTTPS OpenAI-compatible `/chat/completions` endpoint. Click the browser sign-in button and choose a model. This sample uses `/models` for discovery.

The example supports text and non-streaming chat only. Tool-call and media transport are deliberately not implemented here; adapt the provider's four handlers for production use. OAuth itself is not limited to OpenAI-compatible AI transports.

No provider account was used to end-to-end test this sample. Run its isolated HTTP-mock tests with:

```sh
node --test examples/oauth_ai_provider/tests/provider.test.js
```

`calculateInputTokens` works without login. Other handlers require `event.eventPayload.auth`, use only its access token, and do not persist or log credentials. They never read a refresh token, fall back to an old API key, or replay a failed chat request.

See `docs/TOOLPKG_PROVIDER_OAUTH.md` for the host contract, limitations and security boundaries.
