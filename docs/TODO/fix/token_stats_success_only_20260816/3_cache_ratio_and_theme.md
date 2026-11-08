# Replace Cache Display And Standardize Theme Colors

## Previous Behavior

The lifetime card shows cache-write tokens, a provider-specific measurement.
The page mixes primary, secondary, tertiary, error, and neutral theme roles.
Custom theme generation only rebuilds primary and secondary roles.

## Intended Behavior

The lifetime card retains uncached input and cache read, and replaces cache
write display with cache rate. Cache rate is cache read divided by total input
only when both values are fully known; otherwise it displays `--`. Statistics
visuals derive from active primary and secondary colors, their containers, and
their matching content colors in both light and dark themes.

## Work

- Leave cache-write measurements and pricing internal where required for
  provider billing; remove only obsolete statistics display behavior.
- Replace tertiary and error page accents with the standardized local palette.
- Use deterministic primary and secondary family variants for charts and model
  series without literal colors.

## Completion

[DONE]

- The lifetime card keeps uncached input and cache read and shows cache rate;
  cache-write tokens stay internal for billing only.
- `TokenStatsColorsProvider` derives every statistics surface role from the
  active primary and secondary families, including matched container and
  content pairs for chips, fields, and dialogs in light and dark themes.
