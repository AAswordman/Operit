# Market Publish

## Existing

Artifact publication registers JSON metadata and a release asset. Market entries
may include an optional `logoUrl` returned by the server.

## Change

- Decode and display the optional `logoUrl` supplied in market entry responses.
- Keep `manifest.logo` as an archive-local ToolPkg resource for package previews
  and installed package rendering.
- Do not upload, host, or send logo data in publish, update, or new-version
  requests.

[DONE]
