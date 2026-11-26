package com.kashish.bank.benchmark;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kashish.bank.config.Database;
import com.kashish.bank.model.TransactionRequest;
import com.kashish.bank.model.TransactionType;
import com.kashish.bank.service.TransactionExecutor;
import com.kashish.bank.service.TransactionProcessor;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

public final class BenchmarkRunner {

    private static final Logger log =
            LoggerFactory.getLogger(BenchmarkRunner.class);

    private static final int ACCOUNT_COUNT = 100;
    private static final int BATCH_SIZE = 200;

    private static final String ACCOUNT_PREFIX = "BENCH-LOAD-";

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws Exception {

        try {
            int transactionCount = args.length > 0
                    ? Integer.parseInt(args[0])
                    : 50000;

            if (transactionCount < 50000) {
                throw new IllegalArgumentException(
                        "Benchmark requires at least 50,000 transactions");
            }

            prepareAccounts();

            TransactionProcessor processor = new TransactionProcessor();

            System.out.println("\nBANKING TRANSACTION PERFORMANCE TEST");
            System.out.println("Total transactions per configuration: " + transactionCount);
            System.out.println("Benchmark accounts: " + ACCOUNT_COUNT);

            for (int workers : new int[]{1, 5, 10, 20}) {
                prepareAccounts();
                run(processor, transactionCount, workers);
            }

        } finally {
            Database.shutdown();
            AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }

    private static void run(
            TransactionProcessor processor,
            int count,
            int workers) throws Exception {

        String runId = UUID.randomUUID().toString();

        int successful = 0;
        int failed = 0;
        int submitted = 0;

        Map<String, Integer> failureReasons = new HashMap<>();

        long start = System.nanoTime();

        try (TransactionExecutor executor =
                     new TransactionExecutor(
                             processor,
                             workers,
                             workers * 100)) {

            while (submitted < count) {

                int currentBatch = Math.min(
                        BATCH_SIZE,
                        count - submitted);

                List<Future<com.kashish.bank.model.TransactionResult>> futures =
                        new ArrayList<>(currentBatch);

                for (int i = 0; i < currentBatch; i++) {

                    int transactionNumber = submitted + i;

                    String account = ACCOUNT_PREFIX
                            + String.format("%03d",
                                    transactionNumber % ACCOUNT_COUNT);

                    TransactionRequest request = new TransactionRequest(
                            "BENCH-" + runId + "-" + workers + "-" + transactionNumber,
                            null,
                            account,
                            new BigDecimal("0.01"),
                            TransactionType.DEPOSIT
                    );

                    futures.add(executor.submit(request));
                }

                for (Future<?> future : futures) {
                    try {
                        future.get();
                        successful++;
                    } catch (ExecutionException exception) {
                        failed++;

                        Throwable cause = exception.getCause();

                        String reason = cause == null
                                ? "UNKNOWN_ERROR"
                                : cause.getClass().getSimpleName();

                        failureReasons.merge(reason, 1, Integer::sum);
                    }
                }

                submitted += currentBatch;
            }

            executor.shutdown();
        }

        double seconds =
                (System.nanoTime() - start) / 1_000_000_000.0;

        double tps = successful / seconds;

        log.info(
                "benchmark workers={} total={} successful={} failed={} elapsedSeconds={} TPS={} failureReasons={}",
                workers,
                count,
                successful,
                failed,
                seconds,
                tps,
                failureReasons
        );

        System.out.printf(
                Locale.ROOT,
                "%nWorkers: %d | Total: %d | Successful: %d | Failed: %d | Time: %.3f seconds | TPS: %.2f%n",
                workers,
                count,
                successful,
                failed,
                seconds,
                tps
        );

        if (!failureReasons.isEmpty()) {
            System.out.println("Failure reasons: " + failureReasons);
        }
    }

    private static void prepareAccounts() throws Exception {

        String insertSql = """
                INSERT IGNORE INTO account
                (customer_id, account_no, account_type, balance, status)
                VALUES (1, ?, 'SAVINGS', 0.00, 'ACTIVE')
                """;

        String updateSql = """
                UPDATE account
                SET balance = 0.00, status = 'ACTIVE'
                WHERE account_no = ?
                """;

        try (Connection connection = Database.getConnection();
             PreparedStatement insert =
                     connection.prepareStatement(insertSql);
             PreparedStatement update =
                     connection.prepareStatement(updateSql)) {

            for (int i = 0; i < ACCOUNT_COUNT; i++) {

                String account = ACCOUNT_PREFIX
                        + String.format("%03d", i);

                insert.setString(1, account);
                insert.executeUpdate();

                update.setString(1, account);

                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException(
                            "Unable to prepare benchmark account: " + account);
                }
            }
        }
    }
}