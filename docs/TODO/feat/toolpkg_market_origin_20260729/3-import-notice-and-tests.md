# Import Notice And Tests

## Previous State

External ToolPkg imports return only the destination message, with no provenance notice.

## Change

Append an author-support and resale-warning notice for imported packages with validated Operit marketplace origin. Add focused unit coverage for origin parsing, marker absence, invalid metadata, and the import notice.

## Expected Result

Users receive marketplace provenance only when it is available and validated, and the full Kotlin implementation is covered by relevant automated tests.

[DONE]
