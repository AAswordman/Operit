# Import Origin

## Previous State

The registration bridge captures ToolPkg registrations only. Parser output and container runtime do not represent marketplace origin.

## Change

Add a bridge registration method, decode and parse the payload in native Kotlin, validate it against the manifest ToolPkg ID, and retain valid origin data in the container runtime.

## Expected Result

Only a valid Operit origin marker tied to the imported package is exposed to import callers. Existing packages without a marker continue to load normally.

[DONE]
