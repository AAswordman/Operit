# Picker Mode Implementation

## Existing behavior

The picker supports document selection and an unreleased `photo` option. The
photo option only selects images and uses separate parameter rules that expose
Android implementation details to ToolPkg authors.

## Intended correction

Use a single `picker` value to choose the input source. `mimeTypes` applies
only to document selection. `allowMultiple` applies only to document, image,
video, and media selection. Persistent URI access applies only to document and
directory selection. Unsupported combinations fail at request parsing instead
of being silently ignored.

## Verification

Review the request parser tests and the static diff. No local build or test
command is run because repository guidance requires explicit user authorization.

[DONE]
