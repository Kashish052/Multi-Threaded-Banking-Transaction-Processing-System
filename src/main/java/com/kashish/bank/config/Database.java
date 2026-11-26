package com.kashish.bank.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class Database {

    private static final Properties PROPERTIES = load();

    private static final HikariDataSource DATA_SOURCE = createDataSource();

    private Database() {
    }

    private static Properties load() {
        Properties properties = new Properties();

        try (InputStream input = Database.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (input != null) {
                properties.load(input);
            }

        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        return properties;
    }

    private static String setting(
            String key,
            String env,
            String fallback) {

        String value = System.getenv(env);

        if (value == null || value.isBlank()) {
            value = PROPERTIES.getProperty(key, fallback);
        }

        return value;
    }

    private static HikariDataSource createDataSource() {

        HikariConfig config = new HikariConfig();

        String url = setting(
                "db.url",
                "DB_URL",
                "jdbc:mysql://localhost:3306/bankdb");

        String user = setting(
                "db.user",
                "DB_USER",
                "root");

        String password = setting(
                "db.password",
                "DB_PASSWORD",
                "");

        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        config.setPoolName("BankingHikariPool");

        return new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    public static void shutdown() {
        DATA_SOURCE.close();
    }
}