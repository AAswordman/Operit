# Record Only Successful Formal Inference

## Previous Behavior

`TokenTrackingAIService` creates a record for every terminal outcome, including
failed, cancelled, timed-out, and connection-test calls. It writes in a
non-cancellable context and can create a request row without provider usage.

## Intended Behavior

The tracker records only a completed formal inference stream with at least one
provider-confirmed usage component. Explicit zero values remain valid usage.
Test and probe calls bypass tracking. Failed internal attempts are excluded when
a later attempt completes successfully.

## Work

- Remove outcome classification and category persistence.
- Require formal-inference intent at the request boundary so test routes bypass
  the tracker without preserving a persisted call-type concept.
- Keep only the terminal successful usage snapshot when a provider retries.

## Completion

[DONE]

- `AIService.sendMessage` gains `onUsageFinalized`, invoked only on normal
  completion with the final successful attempt number.
- `TokenTrackingAIService` keeps per-attempt usage snapshots, persists only the
  successful attempt with known fields, and covers cancellation between stream
  creation and collection through a cancellation epoch for both tracked and
  untracked requests.
- `RateLimitedAIService` registration and cancellation share one lock, and
  rate-limit or concurrency waits poll a cancellation flag instead of blocking
  uninterruptibly.
- OpenAI, Gemini, and Claude streams confirm completion via protocol terminal
  signals (`[DONE]`, `response.completed`, non-null `finish_reason`,
  Gemini terminal `finishReason` whitelist, `message_stop`); bare EOF throws a
  retryable network interruption instead of finalizing.
- Gemini `promptFeedback` rejections fail without retry and never finalize.
- Probe and connection-test routes keep `recordTokenUsage = false`.
- JVM tests cover terminal confirmation, bare EOF, unspecified finish reasons,
  prompt feedback, JSONL compatibility, and cold-stream cancellation.
