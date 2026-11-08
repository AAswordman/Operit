# Incremental Private Projection

## Previous behavior

Every synchronous private publisher materialization receives only an author ID.
The renderer queries every owned and contributed entry, then queries version and
review reasons for each entry before rewriting the author's R2 bucket.

## Intended behavior

Entry mutations pass the changed entry ID with each affected publisher ID. The
renderer loads the existing shard, rebuilds one author-entry summary, replaces that
summary in the author bucket, sorts the bucket, and writes the shard.

The no-entry scope remains the explicit full-build mode used by batch rebuilds.

## Verification

- Add a test showing an entry-scoped materialization does not query other entries
- Run the Worker test suite and production build
- Deploy the Worker and confirm the deployment version

[DONE] Entry-scoped private publisher materialization now updates one summary while
the explicit no-entry full-build mode remains available to batch rebuilds. The test
creates 24 historical entries and verifies an entry update stays below 10 D1 reads.

[DONE] Dirty-plan settlement preserves the exact projection scope, so a failed
synchronous materialization can be retried by the cron job and clear its own marker.
