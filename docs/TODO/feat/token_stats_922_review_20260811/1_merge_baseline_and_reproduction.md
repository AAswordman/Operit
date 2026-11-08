# 1. Merge Baseline And Reproduction [DONE]

## Previous State

`main` at `f83e69cb` contains the MNN schema-generation repair from #926. #922 was
nine commits behind it and its two candidate runs failed while compiling MNN with
stale generated schema headers.

## Change

Created `fix/token-stats-922-review` from `main` and normally merged
`origin/review-pr-922` into it. The resulting merge commit is `663a3a59`.

## Expected State

The branch contains both the #922 feature work and the current MNN build repair,
so subsequent checks test the actual candidate intended for review rather than the
obsolete PR head.
