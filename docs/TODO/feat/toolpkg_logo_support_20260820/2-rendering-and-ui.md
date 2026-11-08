# Rendering And UI

## Existing

`ProviderLogoLoader` renders APK asset SVG and PNG files. Plugin lists and market
cards currently render fixed Material icons or title-derived initials.

## Change

- Reuse the AndroidSVG and bitmap scaling implementation through a generic
  byte/stream renderer.
- Keep provider-specific asset lookup and provider color tinting in the existing
  provider API.
- Render plugin logos with their original colors in the package manager, market
  list and market detail header.
- Keep the current generic icon for packages and entries without a logo.

[DONE]
