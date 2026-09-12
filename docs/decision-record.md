# Wallet transfer design record

## Data model and money representation

Every amount is an integer paise `BIGINT`; no API, database column, or calculation uses a decimal currency value. A user owns at most one wallet through a database unique constraint. Wallet balance is a materialized, non-negative balance for efficient reads. Each completed movement also writes one immutable debit and one immutable credit ledger entry in the same transaction, making the balance changes auditable.

Funding is not a special balance mutation. It is a normal transfer from a finite system treasury wallet, so both funding and P2P transfers preserve the total of every wallet in the database.

## Correctness under concurrent requests

The database is the concurrency authority. A transfer first reserves its globally unique idempotency key in the same transaction that locks wallets, changes balances, writes ledger entries, and commits its final status. A concurrent duplicate waits on PostgreSQL's unique index, then reads the committed record. It returns that original response only when the stored SHA-256 fingerprint matches the operation type, actor, source, destination, and amount; otherwise it returns `409`.

Both wallets are locked in ascending UUID order before checking balances. This avoids opposite-direction deadlocks. The debit remains conditional (`balance >= amount`) as a second guard, while a bounded credit prevents overflow. Insufficient funds becomes a committed `DECLINED` transfer with no ledger or wallet mutation. The transaction is short and retries only PostgreSQL deadlock, lock-timeout, and serialization SQL states with a bounded jittered backoff.

`SERIALIZABLE` isolation was rejected as the default because it introduces broader retry pressure for this two-row workload. An in-memory lock or pre-transaction idempotency lookup was rejected because it fails across instances and creates a race window. A read-modify-write balance update was rejected because it loses updates under contention.

## Consistency, availability, and operation

For money movement, the service chooses consistency. During a database outage or lock timeout it returns a retryable failure and asks the caller to reuse the idempotency key, rather than accepting an uncommitted payment. JSON logs carry correlation and trace IDs, and metrics expose HTTP rate/latency/error data plus money-domain counters. Domain events are logged only after commit, so logs do not claim a movement that rolled back.

The submitted free deployment is an assessment demo, not an HA production runtime. Production would use managed HA PostgreSQL with PITR, private metrics collection, a secret manager, central telemetry, and multiple app instances. The implementation itself has no instance-local correctness state and is safe to scale against the same PostgreSQL database.

## AI disclosure — complete honestly before submission

Replace this section with an accurate account of your own process. State which architecture and product decisions you directed, which implementation details you accepted from AI assistance, and which code/tests/deployment evidence you personally reviewed. Do not claim live deployment, logs, or test results that were not actually produced.

