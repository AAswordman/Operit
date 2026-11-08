# Plugin List State

## Previous Behavior

The screen stored the loaded ToolPkg container map and a second filtered map. A `LaunchedEffect` copied the first into the second asynchronously, while `isLoading` was cleared immediately after loading.

## Change

Derive the filtered plugin map synchronously from the container map and debounced search query. Keep only the search-input debounce; remove the asynchronous filtering state.

## Expected Result

The screen stays in the loading state until package data is available and never renders a transient empty-plugin state between loading and the populated list.

[DONE]
