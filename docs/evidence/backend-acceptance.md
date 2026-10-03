# Backend acceptance

| Area | Status | Evidence |
|---|---|---|
| Build and unit regression | PASS | `mvn -B -Psqlserver-it verify`: 126 unit tests passed |
| SQL integration, transaction and security regression | PASS | Same command: 43 integration tests passed, including role isolation, concurrency, trigger and rollback cases |
| SQL role permission matrix | PASS | [Day 19 evidence](day-19.md): contained-user allow/deny and GRANT→REVOKE test passed; `Day03IT` verifies runtime roles lack `ALTER`/`CONTROL` and no runtime role is `db_owner` |
| Public cache consistency | PASS (unit) | `PublicEventCacheTest`: publish/cancel path deletes detail and zone keys; TTL is 60 seconds |
| SQL inventory/index final candidate | PASS | `Day21IT` ran `inventory.sql` and `acceptance.sql` after migration 024; all 15 named indexes and runtime-role guard passed |
| Four HTTP E2E scenarios | BLOCKED | No HTTP server plus approved provider sandbox session was run; scenarios are documented, not claimed executed |
| Docker build/restart/RSS | BLOCKED | Docker daemon unavailable locally |
| Azure migration/deploy | BLOCKED | No approved Azure environment or credentials |
| Index benchmark | BLOCKED | No approved benchmark database; no before/after metrics are claimed |

Known functional gap: source migrations do not define SP08 despite the inventory contract naming it. The retired browser payment endpoint must not be reopened until a server-owned payment initiation procedure and contract are approved.

This is backend acceptance only. Responsive UI, camera UI, JSP polish, report/slides, and any claim of production deployment are outside this acceptance record.
