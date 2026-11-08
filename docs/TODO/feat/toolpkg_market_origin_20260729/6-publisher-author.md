---
For_Agent: Use the authenticated publishing account as marketplace provenance author
---

# Publisher Author

## Previous State

Standalone scripts wrote the locally declared package author into marketplace provenance. ToolPkg archives read the author from their manifest. Either local value can differ from the account that actually publishes the market asset.

## Change

Construct marketplace provenance after the publisher account is retrieved from GitHub. Both standalone scripts and ToolPkg primary entries use that authenticated login as their only provenance author. ToolPkg package ID and version continue to come from its manifest so import validation matches the installed package.

## Expected Result

Marketplace provenance identifies the account that published the asset instead of arbitrary local metadata.

[DONE]
