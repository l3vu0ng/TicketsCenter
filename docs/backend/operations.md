# Operations notes

## Public event cache

Public event detail and zone responses use cache-aside Redis keys with a 60-second TTL. Redis failure is fail-open: the request reads SQL Server and remains correct. After a successful publish or cancellation transaction commits, the application deletes both keys; it never rewrites cached payloads from the mutation path. Owner, payment, ticket, refund, and admin data are not cached here.

## Recovery

On restart, session memory may be lost, but holds, payment/refund status, outbox work, and cancellation work remain in SQL Server. Workers re-read durable work after startup. If SQL Server is unavailable, readiness must return 503 and payment/retry callers must reconcile persisted state before retrying.

## Measured limits

No RSS, container cold-start, or benchmark measurements are recorded yet. They require a working Docker daemon and a separate benchmark database; do not substitute estimates for measurements.
