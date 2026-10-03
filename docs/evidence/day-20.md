# Evidence — Day 20

- Added `024_create_performance_indexes.sql`; it declares IX01–IX15 and preserves indexes already created by the object-owning migrations. Its guards prevent duplicate creation in a fresh migration run.
- Public Event cache uses cache-aside Redis with 60-second TTL and deletes detail/zone keys after publish or cancellation commits. `PublicEventCacheTest` verifies invalidation deletes both keys.
- `mvn -B -Dtest=HttpSecurityTest,JsonObjectParserTest test` passed (6 tests) before the SQL integration environment became unavailable.
- Docker CLI/buildx are installed, but the daemon socket is absent. `docker build` could not start; no image/container health result is claimed.
- The SQL Server target returned “database is not currently available” during the Day 20 rerun. No index plan, RSS, container restart, Azure deployment, or benchmark result is claimed.
