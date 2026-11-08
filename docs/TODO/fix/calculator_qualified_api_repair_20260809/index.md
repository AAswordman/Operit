---
fork: https://github.com/tuxKOH/Operit
branch: test/calculator-data-model-tests
---

# Calculator Qualified API Repair

The calculator test branch adds coverage for the calculator and data-model modules.
Review found that two documented calculator APIs are currently treated as unsupported
syntax by the new tests: `Math.*` and `stats.*`.

This work keeps the documented public syntax intact, makes the parser and evaluator
agree on it, and replaces the failure expectations with regression coverage.

Scope:

- `ExpressionParser.kt`
- `ExpressionContext.kt`
- calculator parser tests
- this task record

Expected outcome:

- `Math.sin(0)` evaluates to `0`
- `Math.PI` can be used in expressions
- `stats.mean(2, 4, 6, 8)` evaluates to `5`
- arbitrary dotted identifiers remain unsupported

Steps:

- [Qualified API implementation](1_QualifiedApiImplementation.md)
