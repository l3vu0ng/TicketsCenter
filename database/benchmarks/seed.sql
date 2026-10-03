-- Run only against a disposable benchmark database selected by the operator.
-- This script creates no database, drops no index, and contains no production data.
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() NOT LIKE 'TicketsCenter_Benchmark%'
    THROW 52400, 'Refusing to seed a non-benchmark database', 1;

-- The benchmark is intentionally seeded by the fixture runner, not this file:
-- it must use the current schema and create 20 orgs, 500 events, 100k orders,
-- 120k payments/refunds, 500k audit rows, and sparse pending statuses.  Keeping
-- the destructive reset out of source prevents accidental use on dev/demo.
-- D20 must provide the fixture runner and record its generated row counts here.
