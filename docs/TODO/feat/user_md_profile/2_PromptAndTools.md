# Prompt and tools

Status: DONE

Remove structured field formatting from conversation preparation. Inject the non-empty Markdown document inside a clearly delimited `user_profile` section when user-profile injection is enabled.

Character-card prompts remain independent and cannot replace the global user document. Rename the preference update tool to a document-oriented user-profile tool and retain normal tool permission confirmation. Keep the released tool name as a hidden compatibility adapter for installed packages and persisted calls; it writes into `user.md` and is not exposed in new prompts.
