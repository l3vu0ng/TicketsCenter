# Evidence — Day 19

- `023_harden_runtime_permissions.sql` applied to the SQL Server integration database. It explicitly denies check-in V06, financial base tables, commission execution, and settlement blocker reads.
- `mvn -B -Dit.test=Day07IT,Day19IT,PrincipalIsolationIT -DskipTests=false failsafe:integration-test failsafe:verify` passed: 3 tests. `Day19IT` creates contained users per runtime role, verifies allowed/denied permissions and a GRANT→REVOKE case, then tears them down. `PrincipalIsolationIT` verifies selected EntityManager factories are not shared and close after their transaction; `Day07IT` covers the F03-backed zone read.
- `mvn -B test` passed before the SQL run, including `HttpSecurityTest`: the retired client-controlled VNPAY endpoint returns 410 and is covered by CSRF mapping.
- Benchmark baseline is intentionally not run. There is no approved separate benchmark database; the supplied seed guard refuses non-benchmark names, and `queries.sql` is ready for D20 measurements.

## Open items

- `usp_BeginOrderPayment` (SP08) does not exist despite the old inventory claiming migration 010. The unsafe legacy endpoint is retired; a replacement needs an approved payment-initiation contract and SP before reopening this capability.
- V05/V07 have no scoped endpoint/use case. They remain listed as gaps rather than adding read APIs solely to satisfy inventory.
- Full route-by-route E2E fuzzing and benchmark fixture generation require the Day 21 fixture/benchmark environment.
