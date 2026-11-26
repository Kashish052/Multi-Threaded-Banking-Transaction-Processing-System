# Multi-Threaded Banking Transaction Processing System

A Java-based banking transaction processing system focused on **safe concurrent transactions, database consistency, and measurable throughput**. The project uses Java 17, Maven, JDBC, MySQL, and a bounded thread pool to process banking operations concurrently.

## Overview

The system is designed to process multiple monetary requests while protecting account balances from race conditions and invalid updates. Each request is handled within a database transaction, with account rows locked before balances are changed.

The current transaction-processing implementation supports deposits, withdrawals, and account-to-account transfers. It includes validation, duplicate transaction-ID checks, conditional debit updates, transaction/audit persistence, and rollback handling.

## Key Features

- **Concurrent processing:** Uses a bounded `ThreadPoolExecutor` to process requests using configurable worker threads and queue capacity.
- **Database connection pooling:** Uses HikariCP to manage JDBC connections.
- **Transaction safety:** Executes monetary operations within JDBC transactions and commits only after the required database writes succeed.
- **Pessimistic row locking:** Uses `SELECT ... FOR UPDATE` and acquires transfer account locks in sorted order.
- **Balance protection:** Conditional debit updates prevent withdrawals or transfers from taking an account below the available balance.
- **Duplicate request protection:** Checks transaction identifiers and relies on the database uniqueness constraint as the final safeguard.
- **Audit and failure handling:** Records successful transaction/audit information and persists failure details separately after rollback.
- **Benchmark runner:** Compares execution with 1, 5, 10, and 20 worker threads using at least 50,000 requests per configuration.
- **Automated tests:** Includes a concurrency design test for competing withdrawals.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Application development |
| Maven | Build and dependency management |
| MySQL | Persistent transaction and account data |
| JDBC | Database access and transaction control |
| HikariCP | Database connection pooling |
| JUnit 5 | Automated testing |
| SLF4J / Logback | Application logging |

## Architecture

The main processing flow is:

```text
Transaction Request
       |
       v
TransactionExecutor
       |
       v
TransactionProcessor
       |
       v
Input Validation
       |
       v
JDBC Transaction
       |
       v
Lock Account Rows
       |
       v
Update Balances + Write Transaction/Audit
       |
       v
      Commit
```

If processing fails, the monetary transaction is rolled back and failure details are persisted separately.

See the detailed documentation:

- [Application Architecture](docs/architecture.md)
- [Design Notes](docs/design-note.md)
- [Entity Relationship Diagram](docs/er-diagram.md)
- [Performance Analysis](docs/performance-analysis.md)

## Project Structure

```text
.
├── docs/
│   ├── architecture.md
│   ├── design-note.md
│   ├── er-diagram.md
│   └── performance-analysis.md
├── src/
│   ├── main/java/com/kashish/bank/
│   │   ├── benchmark/
│   │   ├── config/
│   │   └── service/
│   └── test/java/com/kashish/bank/
├── pom.xml
└── README.md
```

## Prerequisites

- JDK 17 or later
- Maven
- MySQL Server
- A MySQL database initialized with the account, transaction, audit, and failure tables expected by the application

The benchmark also expects a customer record with `customer_id = 1`. The benchmark runner creates or resets its own `BENCH-LOAD-*` accounts.

## Database Configuration

The application reads database settings from environment variables first, then from an optional `db.properties` file on the application classpath.

| Setting | Environment variable | Default |
|---|---|---|
| JDBC URL | `DB_URL` | `jdbc:mysql://localhost:3306/bankdb` |
| Username | `DB_USER` | `root` |
| Password | `DB_PASSWORD` | Empty |

Example `db.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/bankdb
db.user=your_mysql_username
db.password=your_mysql_password
```

Keep real credentials out of version control. Use environment variables or a local, untracked configuration file for your machine.

## Build and Test

Run the Maven test suite:

```bash
mvn clean test
```

## Run the Performance Benchmark

Run the benchmark with 50,000 transactions per worker configuration:

```bash
mvn -q exec:java -Dexec.mainClass=com.kashish.bank.benchmark.BenchmarkRunner -Dexec.args=50000
```

The runner compares 1, 5, 10, and 20 worker threads. Run it only after the MySQL schema and required seed data have been prepared. Benchmark execution resets the balances of its dedicated `BENCH-LOAD-*` accounts, so use a dedicated test database.

Record the actual output in [Performance Analysis](docs/performance-analysis.md). Do not use estimated throughput values.

## Author

**Kashish**  
Computer Science Engineering (Artificial Intelligence)  
Chitkara University
