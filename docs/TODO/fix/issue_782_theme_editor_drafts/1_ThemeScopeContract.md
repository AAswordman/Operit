# Theme Scope Contract

## Previous Behavior

Theme keys are grouped by primitive type and copied between the shared projection and a card or group prefix. A save writes the projection first and copies it later in a second DataStore transaction. A reset deletes every scoped key, including the AI avatar and custom chat title that are target presentation metadata.

## Change

Introduce a complete resolved theme snapshot for a specific card or group target. The snapshot always reads that target's prefix and uses the same defaults as Android theme flows when the prefix has no visual values. Add an atomic replacement operation that clears the target's visual key set, writes the complete draft, and updates the shared projection only when the saved target remains active.

AI avatar and custom chat title remain outside the visual reset key set. A complete draft save writes those target metadata values in the same DataStore transaction. Entity deletion retains a separate full cleanup operation for target metadata.

## Expected Result

Saving cannot leave a shared projection and target scope out of sync. A reset changes only visual configuration, while deleting a character or group removes all of that deleted target's stored data.

[DONE]
