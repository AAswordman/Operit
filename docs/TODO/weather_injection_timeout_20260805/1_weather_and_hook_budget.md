---
Fork: https://github.com/AAswordman/Operit.git
Scope: weather injection examples and ToolPkg pre-hook default preferences in assistance and assistance2
---

# Weather timeout and shared Hook budget

## Previous behavior

Weather used a one-second location and HTTP timeout while the shared Hook budget defaulted to five seconds. The weather location call used the public location wrapper, which requests Android address resolution by default. That synchronous reverse-geocoding operation has no deadline.

## Intended behavior

Weather has five seconds for location and each HTTP phase, with a ten-second default shared pre-hook deadline. Weather obtains coordinates only and renders them as the location, leaving reverse geocoding out of the pre-send path. `Tools.System.getLocation(highAccuracy?, timeout?, includeAddress?)` exposes that choice in the Kotlin and Rust SDKs. Existing persisted Hook timeout choices are not changed.

## Scope

- Update weather source and checked-in distribution artifacts in both repositories.
- Add `includeAddress` to the public Kotlin and Rust location wrappers and their SDK documentation.
- Update the Kotlin and Rust preference defaults and Kotlin initial UI value.
- Inspect matching values with text search; do not run a build or test for this configuration-only change.

[DONE]
