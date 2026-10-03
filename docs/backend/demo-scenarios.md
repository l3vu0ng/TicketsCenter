# Demo scenarios

These are acceptance scripts, not evidence that a provider sandbox has run. Use a fresh integration database and redact all cookies, OTPs, QR values, and provider references from saved output.

1. Register and verify a buyer; request/approve an organization; create, upload, submit, and publish an Event; hold seats/standing inventory; apply coupon; use the approved payment initiation path; verify ticket/QR then check in once.
2. Purchase two tickets; request a partial refund; reject once, request again, approve, run the simulated refund worker; verify `paidAmount`, ticket state, and inventory.
3. Create zero/paid/pending/UNKNOWN cases; cancel Event as admin; restart workers; verify compensation/refund work and the USED-ticket exception.
4. Finish an Event; verify settlement blockers; resolve them; recalculate, confirm, record partial payouts, then compare JSON and CSV totals.

At present these scenarios are blocked by the unavailable integration database. Do not manually update status columns to make a scenario pass.
