---
title: Integration And Validation
status: completed
---

# Integration And Validation

## Integration

Register only `ro` alongside the existing locale-config entries. Add Romanian to `LocaleUtils` so it can be selected in the application. Add its explicit translation target in `ConversationService`; without this case, the feature defaults to Chinese.

## Validation

After creating the authorized local implementation commit, run:

```bash
python3 ci/script/check_localizations.py --base <upstream-main-sha> --candidate HEAD
```

The command validates the candidate tree against the recorded upstream base. Gradle compilation and builds are intentionally out of scope.

## Delivery

Inspect the final diff and local worktree status. Pushing and creating a pull request require separate authorization.

## Result

The localization gate completed with zero errors and zero warnings against the recorded upstream baseline.

[DONE]
