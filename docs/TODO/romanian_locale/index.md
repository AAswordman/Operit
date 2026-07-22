---
title: Romanian Locale
repo: https://github.com/luojiaping/Operit
upstream: https://github.com/AAswordman/Operit
base: 9dbc573002091730a8aafe47c23484e640888819
status: completed
---

# Romanian Locale

## Original State

The application has seven registered locales and no Romanian resource directory or in-app Romanian option. Pull request #796 attempted to add Romanian resources, but its locale configuration, resource keys, and placeholders do not satisfy the repository localization gate.

## Intent

Add Romanian as a complete application locale from the upstream Chinese resource source. The implementation does not reuse the candidate translation text from pull request #796.

## Scope

- `app/src/main/res/values-ro/strings.xml`
- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/java/com/ai/assistance/operit/util/LocaleUtils.kt`
- `app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ConversationService.kt`
- This TODO directory

No existing locale is removed or renamed. No Gradle build is part of this work.

## Expected Result

Romanian appears in the in-app language picker and Android locale configuration. Its resource file covers every source resource, preserves XML structure and formatting tokens, and passes the repository localization gate against the upstream baseline.

## Steps

1. [Resource Translation](./1_ResourceTranslation.md)
2. [Integration And Validation](./2_IntegrationAndValidation.md)

## Completion

- Added 7,195 Romanian resources with the same key set as the Chinese source
- Registered `ro` without changing existing locale-config entries
- Added Romanian to the in-app selector and translation target mapping
- `check_localizations.py --base 9dbc573002091730a8aafe47c23484e640888819 --candidate HEAD` reported zero errors and zero warnings

[DONE]
