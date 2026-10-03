# Baseline index benchmark

The SQL in `database/benchmarks/queries.sql` maps one fixed query to IX01–IX15 in SPEC §14.8. It must run only after a disposable database named `TicketsCenter_Benchmark*` has been populated with synthetic fixtures. The seed guard refuses dev/demo names and never drops indexes.

Migration 024 declares IX01–IX15 (and recognizes the six that already belonged to earlier object migrations). No baseline is recorded: the SQL integration database is not an approved benchmark target and became unavailable during Day 20. D20 must run each query after warm-up three times with actual plan and `STATISTICS IO, TIME`, record median logical reads/CPU/elapsed/count, then compare the same inputs after each candidate index. Do not claim an improvement before that measurement exists.
