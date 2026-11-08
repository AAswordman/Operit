# Standalone user-configuration UI

Status: [DONE]

## Existing state

The first implementation put profile-document editing, automatic updates, and locking in the
memory library's space selector. The selector also allowed creating, renaming, and deleting the
same spaces. This makes personal-profile management look like a memory-library operation.

## Change

Restore the User Preferences entry in Settings. It owns the configuration selector and actions,
then presents the selected configuration as a `user.md` Markdown editor with automatic-update and
whole-document-lock controls.

The memory library keeps only the active-space selector required to browse the matching memory
database. It no longer creates, renames, deletes, or edits user configurations.

## Compatibility

Configurations continue to use the existing memory-space IDs. Consequently the associated
`memory-space-profiles/<id>/user.md`, ObjectBox database, and fixed character-card binding remain
unchanged for users upgrading from +4, +5, or the current worktree implementation.

[DONE]
