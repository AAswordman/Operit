---
For_Agent: Display marketplace provenance after an external package import
---

# Import Notice Dialog

## Previous State

The package manager appended validated marketplace provenance to its successful import message. The package screen discarded successful messages and always displayed only a generic Snackbar, so users never saw the source notice.

## Change

Return validated marketplace provenance as structured external-import data. The package screen displays a dedicated dialog with the package ID, version, and author whenever this data is present.

## Expected Result

Importing a market-published script or ToolPkg displays its provenance notice. Imports without a valid marketplace marker retain the normal success Snackbar.

[DONE]
