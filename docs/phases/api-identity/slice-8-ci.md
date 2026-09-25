# api-identity slice 8 — CI

Stage: `api-identity` (Phase 1). Slice 8 only.

Date: 2026-09-25

## Done

- `.github/workflows/api.yml` runs on every push.
- The job uses Java 21, builds `api`, and runs `./mvnw -B test`. Surefire includes the `*IT` classes.
- `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` are not set, so CI uses Testcontainers PostgreSQL. A failed test fails the job.

## Tested

The workflow file is in the repo. GitHub has not run it yet. That happens on the next push that includes this file.

## Failed

The first GitHub run reported 1 error. `SchemaIT` inserted `+8801700000001`, the same phone bootstrap creates, so the insert threw before the uniqueness check. The test now uses its own phone and email. `SchemaIT` passed locally after that change.

## Next

Push this branch. The Actions run for that push is the first execution of the suite in CI.
