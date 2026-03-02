package com.propertly.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class Database {

    private static HikariDataSource dataSource;

    public static void init() {
        String jdbcUrl = System.getenv("DATABASE_URL");
        if (jdbcUrl == null) {
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "5432");
            String name = System.getenv().getOrDefault("DB_NAME", "propertly");
            String user = System.getenv().getOrDefault("DB_USER", "postgres");
            String pass = System.getenv().getOrDefault("DB_PASS", "postgres");
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + name + "?user=" + user + "&password=" + pass;
        } else if (!jdbcUrl.startsWith("jdbc:")) {
            // Railway provides postgres:// or postgresql:// format
            jdbcUrl = jdbcUrl.replaceFirst("^postgres(ql)?://", "jdbc:postgresql://");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
        runMigrations();
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static void runMigrations() {
        try (InputStream is = Database.class.getClassLoader().getResourceAsStream("schema.sql")) {
            if (is == null) throw new RuntimeException("schema.sql not found");
            String sql = new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run migrations", e);
        }
    }
}
