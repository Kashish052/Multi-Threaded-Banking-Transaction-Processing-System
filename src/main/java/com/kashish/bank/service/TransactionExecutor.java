package com.kashish.bank.service;

import com.kashish.bank.exception.BankingException;
import com.kashish.bank.model.TransactionRequest;
import com.kashish.bank.model.TransactionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class TransactionExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final TransactionProcessor processor;

    public TransactionExecutor(TransactionProcessor processor, int workers, int queueCapacity) {
        if (workers < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("Workers and queue capacity must be positive");
        }
        this.processor = processor;
        this.executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public Future<TransactionResult> submit(TransactionRequest request) {
        return executor.submit(() -> processor.process(request));
    }

    public List<Future<TransactionResult>> submitAll(List<TransactionRequest> requests) {
        List<Future<TransactionResult>> futures = new ArrayList<>(requests.size());
        for (TransactionRequest request : requests) {
            futures.add(submit(request));
        }
        return futures;
    }

    public void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}