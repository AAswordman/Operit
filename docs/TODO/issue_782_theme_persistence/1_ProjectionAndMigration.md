# Projection and Migration

## Previous Behavior

Character-card activation skips projection when its scoped preference prefix is empty. The shared keys then retain the preceding target's theme. Existing legacy installations can also have a global theme without a default-card snapshot.

## Change

Apply every selected card's prefix, including an empty prefix. An empty prefix clears the active projection so Compose resolves application defaults. Target activation removes projection keys absent from the selected scope, while regular saves retain separately maintained target metadata such as avatar and chat title. A one-time migration creates the default-card snapshot only when no scoped theme exists and the global data is clearly associated with the default card.

## Expected Result

No target can display another target's stale projection, and unambiguous legacy default themes remain available after the update.

[DONE]
