# Architecture

```mermaid
flowchart TD
    A[Transaction Request] --> B[Bounded TransactionExecutor]
    B --> C[TransactionProcessor]
    C --> D[Input Validation]
    D --> E[JDBC Transaction]
    E --> F[Lock Account Rows in Sorted Order]
    F --> G[Apply Conditional Balance Update]
    G --> H[Write Transaction and Audit]
    H --> I[Commit]
    E --> J[Rollback on Failure]
    J --> K[Persist Failure Record]
    I --> L[Result and Structured Log]
    K --> L
```

The application is separated into request/result models, a transaction processor, a bounded executor, database configuration, and benchmark tooling. The database owns durable balances, uniqueness, foreign keys, checks, and audit records.
