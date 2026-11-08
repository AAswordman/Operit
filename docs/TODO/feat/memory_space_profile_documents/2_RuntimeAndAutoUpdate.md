# Runtime and automatic updates

Status: [DONE]

Use the active memory-space identifier to load one profile document for prompt injection and tool updates.

The existing memory auto-save candidate pipeline already runs per memory space. Its profile extraction writes only the matching space document and checks whole-document automatic-update locking at the repository boundary.

`update_user_profile` remains the prompt-visible document tool. `update_user_preferences` remains a hidden adapter for released packages and persisted calls; both resolve the active memory-space document.

[DONE]
