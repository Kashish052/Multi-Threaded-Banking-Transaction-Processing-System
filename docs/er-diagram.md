# Entity Relationship Diagram

```mermaid
erDiagram
    CUSTOMER ||--o{ ACCOUNT : owns
    ACCOUNT ||--o{ TRANSACTION : source
    ACCOUNT ||--o{ TRANSACTION : destination
    TRANSACTION ||--o{ TRANSACTION_AUDIT : produces

    CUSTOMER { BIGINT customer_id PK VARCHAR full_name VARCHAR email }
    ACCOUNT { VARCHAR account_no PK BIGINT customer_id FK DECIMAL balance VARCHAR status }
    TRANSACTION { VARCHAR transaction_id PK VARCHAR source_account FK VARCHAR destination_account FK DECIMAL amount VARCHAR status }
    TRANSACTION_AUDIT { BIGINT audit_id PK VARCHAR transaction_id VARCHAR status VARCHAR reason }
    FAILED_TRANSACTION { BIGINT failure_id PK VARCHAR transaction_id VARCHAR failure_reason }
```

`FAILED_TRANSACTION` intentionally retains failure details independently of the rolled-back monetary transaction. It is linked by the submitted transaction identifier rather than a foreign key so failures can also be recorded when an account identifier is invalid.
