# Asset Cache Signature

## Previous Behavior

Every APK update invalidated every built-in ToolPkg cache because the asset signature included the whole APK size and modification time.

## Change

Build the asset cache signature from the ToolPkg asset's APK ZIP entry CRC32 and uncompressed size. These values identify the individual packaged asset without extracting it.

## Expected Result

An APK update reuses an existing extracted cache for every unchanged built-in ToolPkg and invalidates only an asset whose packaged bytes changed.

[DONE]
