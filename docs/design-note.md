# Design Note

## Transaction boundaries
Each monetary request runs inside one JDBC transaction. The processor disables auto-commit, locks affected account rows, applies balance changes, writes the transaction and audit rows, then commits. Any processing exception rolls back the monetary changes.

## Locking strategy
The implementation uses pessimistic row locking with `SELECT ... FOR UPDATE`. For transfers, account identifiers are sorted before locks are acquired. This consistent order reduces circular-wait deadlocks. Conditional debit SQL also checks that the balance is sufficient.

## Idempotency
`transaction_id` is the primary key of the transaction table. A request checks for an existing identifier inside its transaction, and the database uniqueness constraint is the final protection against concurrent duplicate requests. The duplicate test must be run against the configured database as part of integration verification.

## Audit and failure handling
Successful transaction and audit rows are committed with balance updates. On a rejected or failed request, the monetary transaction is rolled back and failure/audit details are written using a separate connection so that the failure record survives the rollback.

## Index choices
Indexes support customer account lookup, account status filtering, transaction history by source/destination and time, failure grouping, and audit lookup by transaction ID. Actual plans must be checked with MySQL EXPLAIN using representative data.

## Isolation and consistency
The design relies on InnoDB transactions and row locks. A transaction is not reported successful until its database commit completes. The system should be run against InnoDB tables.
