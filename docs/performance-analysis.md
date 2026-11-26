# Performance Analysis

Run the benchmark against a dedicated database with the schema and seed data loaded. The benchmark requires at least 50,000 requests and compares 1, 5, 10, and 20 worker threads.

Command:

```bash
mvn -q exec:java -Dexec.mainClass=com.kashish.bank.benchmark.BenchmarkRunner -Dexec.args=50000
```

Record the actual output for each worker count:

| Workers | Total | Successful | Failed | Elapsed seconds | TPS |
|---:|---:|---:|---:|---:|---:|
| 1 | 50000 | To measure | To measure | To measure | To measure |
| 5 | 50000 | To measure | To measure | To measure | To measure |
| 10 | 50000 | To measure | To measure | To measure | To measure |
| 20 | 50000 | To measure | To measure | To measure | To measure |

Do not fill this table with estimated values. More threads can stop improving throughput when database connections, disk I/O, lock contention, CPU, or connection limits become bottlenecks. Use MySQL slow-query logging and EXPLAIN plans to identify database costs.
