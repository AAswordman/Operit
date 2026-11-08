# Qualified API Implementation

## Existing behavior

`JsCalculator` advertises `Math.sin`, `Math.cos`, and `stats.*` functions.
The parser only recognizes `Math.method(...)`, while the evaluator only dispatches
unqualified math names. It does not parse `stats.method(...)` at all. `Math.PI`,
used by the in-source calculator example, is also parsed as an invalid method call.

## Intended correction

Recognize the documented `Math` and `stats` namespaces for function calls. Resolve
the `Math.PI` and `Math.E` constants using the existing calculator constants, and
normalize only the `Math.` function namespace before the established math dispatch.

No general object member syntax is added. Array `.length` retains its existing
special handling, and arbitrary dotted identifiers continue to be rejected.

## Verification

Perform static review and whitespace validation. Builds and tests are not run under
the repository execution rules because the user did not request them.

[DONE]
