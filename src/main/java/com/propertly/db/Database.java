package com.propertly.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class Database {

    private static HikariDataSource dataSource;

    public static void init() {
        String rawUrl = System.getenv("DATABASE_URL");

        HikariConfig config = new HikariConfig();

        if (rawUrl == null) {
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "5432");
            String name = System.getenv().getOrDefault("DB_NAME", "propertly");
            String user = System.getenv().getOrDefault("DB_USER", "postgres");
            String pass = System.getenv().getOrDefault("DB_PASS", "postgres");
            config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + name);
            config.setUsername(user);
            config.setPassword(pass);
        } else if (rawUrl.startsWith("jdbc:")) {
            config.setJdbcUrl(rawUrl);
        } else {
            // Railway provides postgresql:// or postgres:// — parse and split out credentials
            try {
                String normalized = rawUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = new URI(normalized);
                String userInfo = uri.getUserInfo();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath(); // "/railway"
                config.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + path);
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    config.setUsername(parts[0]);
                    if (parts.length > 1) config.setPassword(parts[1]);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse DATABASE_URL: " + rawUrl, e);
            }
        }
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
