# Evidence — Day 21

- Replaced the obsolete README with a UTF-8 clean-machine runbook; added `.env.example` with names only, no credentials.
- Added `database/tests/acceptance.sql` and `Day21IT`. They check inventory/index/role invariants against the actual candidate database and intentionally do not skip missing objects.
- Applied `024_create_performance_indexes.sql`; the first `Day21IT` run failed on the missing index guard, then passed after the migration.
- Corrected two SQL test fixtures that created a `PUBLISHED` event before adding its zones; the real published-layout trigger correctly rejected that invalid setup.
- `mvn -B test` passed 126 unit tests. `mvn -B -Psqlserver-it verify` passed 126 unit tests and 43 SQL Server integration tests, with zero failures/errors.
- Runtime preflight on 2026-10-01: Jetty served `GET /health/live` on port 8081 with `200`; `/health/ready` returned `503` because no local runtime-role credentials were provisioned. Jetty was stopped afterward; the existing Tomcat on port 8080 was not changed.
- Added four reproducible HTTP demo scenarios. They are not marked executed because no HTTP server plus approved provider sandbox session was run.
- Final status is recorded in [backend acceptance](backend-acceptance.md) and the operational blockers remain in [known issues](../backend/known-issues.md).
