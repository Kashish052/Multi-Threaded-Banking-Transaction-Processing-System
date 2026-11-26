package com.kashish.bank;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ConcurrencyDesignTest {

    @Test
    void onlyOneConcurrentWithdrawalCanConsumeTenThousand() throws Exception {
        AtomicReference<BigDecimal> balance =
                new AtomicReference<>(new BigDecimal("10000.00"));

        AtomicInteger successes = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = pool.submit(() ->
                    withdraw(balance, new BigDecimal("7000.00"), successes, start));

            Future<?> second = pool.submit(() ->
                    withdraw(balance, new BigDecimal("5000.00"), successes, start));

            start.countDown();

            first.get();
            second.get();

            assertEquals(1, successes.get());
            assertTrue(balance.get().compareTo(BigDecimal.ZERO) >= 0);
        } finally {
            pool.shutdownNow();
        }
    }

    private void withdraw(
            AtomicReference<BigDecimal> balance,
            BigDecimal amount,
            AtomicInteger successes,
            CountDownLatch start) {

        try {
            start.await();

            while (true) {
                BigDecimal currentBalance = balance.get();

                if (currentBalance.compareTo(amount) < 0) {
                    return;
                }

                BigDecimal updatedBalance = currentBalance.subtract(amount);

                if (balance.compareAndSet(currentBalance, updatedBalance)) {
                    successes.incrementAndGet();
                    return;
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}