# Known issues

| Issue | Impact | Evidence / owner |
|---|---|---|
| Docker daemon is unavailable locally | Cannot build or run the container health/restart scenario | Local environment operator must start Docker Desktop/daemon |
| Local runtime-role credentials are not provisioned | Jetty liveness is up but readiness is `503`; no `.env.runtime` or SQL-role environment variables exist | Secret-store owner must provision all `DB_{BUYER,MANAGER,CHECK_IN,ADMIN,AUTH,WORKER}_{USER,PASSWORD}` values |
| No approved `TicketsCenter_Benchmark*` database | No valid index baseline or write/storage measurement | Project owner/database operator must provide isolated target |
| SP08 payment-initiation procedure absent | Legacy browser payment endpoint remains retired | Payment owner must approve and implement a server-side initiation contract |
| Fresh-machine/container acceptance not run by an independent operator | Runbook is documented but its clean-machine claim is not yet verified | A separate operator must follow `README.md`, start Docker, and record the result |
| Four HTTP E2E scenarios not executed against provider sandboxes | Backend flow evidence remains SQL/unit/integration level rather than external end-to-end | Run the scenarios in `docs/backend/demo-scenarios.md` with approved sandbox credentials |
