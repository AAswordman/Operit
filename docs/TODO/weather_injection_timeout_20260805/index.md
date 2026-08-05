---
Fork: https://github.com/AAswordman/Operit.git
Scope: examples/message_insert and the matching packaged example in assistance2
---

# Weather injection timeout

The weather attachment first reads device location and then requests wttr.in. It must not cause the pre-send Hook chain to wait without a bounded deadline.

The weather-only path uses five seconds for location, HTTP connection, and HTTP response reads. It obtains only latitude and longitude so Android reverse geocoding is not added as an unbounded third step. The weather attachment displays coordinates as its location. Standalone location injection keeps its existing address lookup and timeout. `Tools.System.getLocation` exposes an `includeAddress` parameter in both runtimes so packages can make that choice through the public SDK.

The default shared ToolPkg pre-hook deadline is ten seconds in both runtimes. Saved user values remain user choices.

- Update the TypeScript source and checked-in `dist/shared.js` in both repositories
- Keep the timeout local to weather injection rather than changing the user-configured location injection path
- Add the public `includeAddress` location option in the Kotlin and Rust SDKs
- Set the Kotlin and Rust default shared pre-hook deadline to ten seconds
- Verify the source and packaged artifact use the same timeout values

[DONE]
