package com.kashish.bank.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kashish.bank.config.Database;
import com.kashish.bank.exception.AccountNotFoundException;
import com.kashish.bank.exception.BankingException;
import com.kashish.bank.exception.DuplicateTransactionException;
import com.kashish.bank.exception.InsufficientBalanceException;
import com.kashish.bank.exception.InvalidTransactionException;
import com.kashish.bank.model.TransactionRequest;
import com.kashish.bank.model.TransactionResult;
import com.kashish.bank.model.TransactionStatus;
import com.kashish.bank.model.TransactionType;

public class TransactionProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionProcessor.class);

    public TransactionResult process(TransactionRequest request)
            throws BankingException {

        long started = System.nanoTime();

        validateShape(request);

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                List<String> accounts = involvedAccounts(request);
                Map<String, BigDecimal> balances =
                        lockAccounts(connection, accounts);

                if (alreadyProcessed(connection, request.transactionId())) {
                    throw new DuplicateTransactionException(
                            request.transactionId());
                }

                validateAccounts(request, balances);

                BigDecimal sourceBalance =
                        request.sourceAccount() == null
                                ? null
                                : balances.get(request.sourceAccount());

                BigDecimal destinationBalance =
                        request.destinationAccount() == null
                                ? null
                                : balances.get(request.destinationAccount());

                switch (request.type()) {
                    case DEPOSIT ->
                            destinationBalance = credit(
                                    connection,
                                    request.destinationAccount(),
                                    request.amount());

                    case WITHDRAWAL ->
                            sourceBalance = debit(
                                    connection,
                                    request.sourceAccount(),
                                    request.amount(),
                                    sourceBalance);

                    case TRANSFER -> {
                        sourceBalance = debit(
                                connection,
                                request.sourceAccount(),
                                request.amount(),
                                sourceBalance);

                        destinationBalance = credit(
                                connection,
                                request.destinationAccount(),
                                request.amount());
                    }
                }

                insertTransaction(
                        connection,
                        request,
                        TransactionStatus.SUCCESS,
                        null);

                insertAudit(
                        connection,
                        request,
                        TransactionStatus.SUCCESS,
                        "Processed successfully");

                connection.commit();

                long duration = elapsed(started);

                log.info(
                        "timestamp={} transactionId={} account={} thread={} operation={} result=SUCCESS durationMs={}",
                        new Timestamp(System.currentTimeMillis()),
                        request.transactionId(),
                        safeAccount(request),
                        Thread.currentThread().getName(),
                        request.type(),
                        duration);

                return new TransactionResult(
                        request.transactionId(),
                        TransactionStatus.SUCCESS,
                        "Processed successfully",
                        sourceBalance,
                        destinationBalance,
                        duration);

            } catch (BankingException exception) {
                rollback(connection, exception);

                persistFailure(
                        request,
                        exception instanceof DuplicateTransactionException
                                ? TransactionStatus.REJECTED
                                : TransactionStatus.FAILED,
                        exception.getMessage());

                long duration = elapsed(started);

                log.warn(
                        "transactionId={} account={} thread={} operation={} result={} errorCategory={} durationMs={}",
                        request.transactionId(),
                        safeAccount(request),
                        Thread.currentThread().getName(),
                        request.type(),
                        exception instanceof DuplicateTransactionException
                                ? "REJECTED"
                                : "FAILED",
                        exception.getClass().getSimpleName(),
                        duration);

                throw exception;

            } catch (SQLException exception) {
                rollback(connection, exception);

                String reason = isDuplicateKey(exception)
                        ? "DUPLICATE_TRANSACTION_ID"
                        : "DATABASE_ERROR";

                persistFailure(
                        request,
                        TransactionStatus.FAILED,
                        reason);

                log.error(
                        "transactionId={} account={} thread={} operation={} result=FAILED errorCategory={}",
                        request.transactionId(),
                        safeAccount(request),
                        Thread.currentThread().getName(),
                        request.type(),
                        reason,
                        exception);

                if (isDuplicateKey(exception)) {
                    throw new DuplicateTransactionException(
                            request.transactionId());
                }

                throw new BankingException(
                        "Transaction could not be completed",
                        exception);
            }

        } catch (SQLException exception) {
            persistFailure(
                    request,
                    TransactionStatus.FAILED,
                    "DATABASE_CONNECTION_ERROR");

            throw new BankingException(
                    "Unable to connect to the banking database",
                    exception);
        }
    }

    private void validateShape(TransactionRequest request)
            throws InvalidTransactionException {

        if (request.amount().scale() > 2
                || request.amount().signum() <= 0) {
            throw new InvalidTransactionException(
                    "Amount must be positive and have at most two decimal places");
        }

        switch (request.type()) {
            case DEPOSIT -> {
                if (blank(request.destinationAccount())
                        || !blank(request.sourceAccount())) {
                    throw new InvalidTransactionException(
                            "Deposit requires destinationAccount only");
                }
            }

            case WITHDRAWAL -> {
                if (blank(request.sourceAccount())
                        || !blank(request.destinationAccount())) {
                    throw new InvalidTransactionException(
                            "Withdrawal requires sourceAccount only");
                }
            }

            case TRANSFER -> {
                if (blank(request.sourceAccount())
                        || blank(request.destinationAccount())
                        || request.sourceAccount().equals(
                                request.destinationAccount())) {
                    throw new InvalidTransactionException(
                            "Transfer requires two different accounts");
                }
            }
        }
    }

    private boolean alreadyProcessed(Connection connection, String id)
            throws SQLException {

        String sql =
                "SELECT transaction_id FROM transactions WHERE transaction_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> involvedAccounts(TransactionRequest request) {

        List<String> accounts = new ArrayList<>();

        if (request.sourceAccount() != null) {
            accounts.add(request.sourceAccount());
        }

        if (request.destinationAccount() != null) {
            accounts.add(request.destinationAccount());
        }

        Collections.sort(accounts);

        return accounts;
    }

    private Map<String, BigDecimal> lockAccounts(
            Connection connection,
            List<String> accountIds)
            throws SQLException, BankingException {

        Map<String, BigDecimal> balances = new HashMap<>();

        String sql =
                "SELECT balance, status FROM account WHERE account_no = ? FOR UPDATE";

        for (String id : accountIds) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AccountNotFoundException(id);
                    }

                    String status = rs.getString("status");

                    if (!"ACTIVE".equals(status)) {
                        throw new InvalidTransactionException(
                                "Account is not active: " + id);
                    }

                    balances.put(id, rs.getBigDecimal("balance"));
                }
            }
        }

        return balances;
    }

    private void validateAccounts(
            TransactionRequest request,
            Map<String, BigDecimal> balances)
            throws AccountNotFoundException, InsufficientBalanceException {

        if (request.sourceAccount() != null
                && !balances.containsKey(request.sourceAccount())) {
            throw new AccountNotFoundException(
                    request.sourceAccount());
        }

        if (request.destinationAccount() != null
                && !balances.containsKey(request.destinationAccount())) {
            throw new AccountNotFoundException(
                    request.destinationAccount());
        }

        if (request.sourceAccount() != null
                && request.type() != TransactionType.DEPOSIT
                && balances.get(request.sourceAccount())
                .compareTo(request.amount()) < 0) {

            throw new InsufficientBalanceException(
                    request.sourceAccount());
        }
    }

    private BigDecimal debit(
            Connection connection,
            String accountId,
            BigDecimal amount,
            BigDecimal oldBalance)
            throws SQLException, InsufficientBalanceException {

        String sql =
                "UPDATE account SET balance = balance - ? " +
                "WHERE account_no = ? AND status = 'ACTIVE' AND balance >= ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountId);
            ps.setBigDecimal(3, amount);

            if (ps.executeUpdate() != 1) {
                throw new InsufficientBalanceException(accountId);
            }
        }

        return oldBalance.subtract(amount);
    }

    private BigDecimal credit(
            Connection connection,
            String accountId,
            BigDecimal amount)
            throws SQLException {

        String sql =
                "UPDATE account SET balance = balance + ? " +
                "WHERE account_no = ? AND status = 'ACTIVE'";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, accountId);

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "Destination account is not active: " + accountId);
            }
        }

        String selectSql =
                "SELECT balance FROM account WHERE account_no = ?";

        try (PreparedStatement ps =
                     connection.prepareStatement(selectSql)) {

            ps.setString(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Account disappeared after credit: " + accountId);
                }

                return rs.getBigDecimal("balance");
            }
        }
    }

    private void insertTransaction(
            Connection connection,
            TransactionRequest request,
            TransactionStatus status,
            String reason)
            throws SQLException {

        String sql =
                "INSERT INTO transactions " +
                "(transaction_id, source_account, destination_account, amount, transaction_type, status, failure_reason) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, request.transactionId());
            ps.setString(2, request.sourceAccount());
            ps.setString(3, request.destinationAccount());
            ps.setBigDecimal(4, request.amount());
            ps.setString(5, request.type().name());
            ps.setString(6, status.name());
            ps.setString(7, reason);

            ps.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            TransactionRequest request,
            TransactionStatus status,
            String reason)
            throws SQLException {

        String sql =
                "INSERT INTO transaction_audit " +
                "(transaction_id, transaction_type, status, reason, thread_name) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, request.transactionId());
            ps.setString(2, request.type().name());
            ps.setString(3, status.name());
            ps.setString(4, reason);
            ps.setString(5, Thread.currentThread().getName());

            ps.executeUpdate();
        }
    }

    private void persistFailure(
            TransactionRequest request,
            TransactionStatus status,
            String reason) {

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String failureSql =
                        "INSERT INTO failed_transaction " +
                        "(transaction_id, transaction_type, source_account, destination_account, amount, status, failure_reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement ps =
                             connection.prepareStatement(failureSql)) {

                    ps.setString(1, request.transactionId());
                    ps.setString(2, request.type().name());
                    ps.setString(3, request.sourceAccount());
                    ps.setString(4, request.destinationAccount());
                    ps.setBigDecimal(5, request.amount());
                    ps.setString(6, status.name());
                    ps.setString(7, reason);

                    ps.executeUpdate();
                }

                insertAudit(
                        connection,
                        request,
                        status,
                        reason);

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();

                log.error(
                        "Could not persist failure audit for transactionId={}",
                        request.transactionId(),
                        exception);
            }

        } catch (SQLException exception) {
            log.error(
                    "Failure-audit connection error: transactionId={}, SQLState={}, ErrorCode={}, Message={}",
                    request.transactionId(),
                    exception.getSQLState(),
                    exception.getErrorCode(),
                    exception.getMessage(),
                    exception);
        }
    }

    private boolean isDuplicateKey(SQLException exception) {

        SQLException current = exception;

        while (current != null) {
            if (current.getErrorCode() == 1062
                    || "23000".equals(current.getSQLState())) {
                return true;
            }

            current = current.getNextException();
        }

        return false;
    }

    private void rollback(Connection connection, Exception cause) {

        try {
            connection.rollback();
        } catch (SQLException exception) {
            cause.addSuppressed(exception);
        }
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safeAccount(TransactionRequest request) {
        return request.sourceAccount() != null
                ? request.sourceAccount()
                : request.destinationAccount();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}