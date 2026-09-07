# Codex PDF Input

## Existing State

Direct image attachments are stored in `ImagePoolManager` and represented by
internal media links. `OpenAIProvider.buildContentField` converts those links
to content parts, while `OpenAIResponsesPayloadAdapter` maps them to Responses
input parts. PDF attachments currently remain ordinary attachment text.

## Intended Change

- Reuse the existing Codex image direct-input switch for the combined
  image/PDF capability, avoiding a new persisted configuration field.
- Store PDF bytes in the existing media pool and represent them as an internal
  file link.
- Enable file-link conversion only for Codex.
- Convert Codex PDF links to Responses `input_file` parts with filename and
  `file_data` data URI.
- Leave audio, video, generic OpenAI, and XML ToolCall behavior unchanged.

## Completion

[DONE]
