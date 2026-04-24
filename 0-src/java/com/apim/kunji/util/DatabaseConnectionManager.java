package com.apim.kunji.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Singleton HikariCP connection pool for Azure SQL.
 *
 * <p>Initialized lazily on first call via double-checked locking.
 * Reads connection string from the {@code DB_CONNECTION_STRING} environment variable
 * (full JDBC URL with embedded credentials, matching local.settings.json / Azure App Settings).
 *
 * <p>Usage:
 * <pre>
 *   try (Connection conn = DatabaseConnectionManager.getConnection()) {
 *       PreparedStatement ps = conn.prepareStatement("SELECT ...");
 *       // ...
 *   }
 *   // Connection returned to pool automatically
 * </pre>
 *
 * <p>Critical config: {@code keepaliveTime=30s} prevents Azure SQL from dropping idle connections.
 */
public final class DatabaseConnectionManager {

    private static final Logger LOG = Logger.getLogger(DatabaseConnectionManager.class.getName());
    private static volatile HikariDataSource dataSource;

    private DatabaseConnectionManager() {}

    public static Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public static HikariDataSource getDataSource() {
        if (dataSource == null) {
            synchronized (DatabaseConnectionManager.class) {
                if (dataSource == null) {
                    dataSource = createDataSource();
                    LOG.info("HikariCP pool initialized: " + dataSource.getPoolName());
                }
            }
        }
        return dataSource;
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();

        String connectionString = System.getenv("DB_CONNECTION_STRING");
        if (connectionString == null || connectionString.isBlank()) {
            throw new IllegalStateException("DB_CONNECTION_STRING environment variable is not set");
        }
        config.setJdbcUrl(connectionString);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5_000);       // 5s to get connection from pool
        config.setIdleTimeout(300_000);            // 5m idle before eviction
        config.setMaxLifetime(1_800_000);          // 30m max connection lifetime
        config.setKeepaliveTime(30_000);           // 30s keepalive — critical for Azure SQL idle-drop
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("KunjiPool");
        config.setThreadFactory(Thread.ofVirtual().name("hikari-vt-", 0).factory());

        return new HikariDataSource(config);
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOG.info("HikariCP pool shut down");
        }
        dataSource = null;
    }

    // For testing: allows injecting a mock DataSource
    static void setDataSource(HikariDataSource ds) {
        dataSource = ds;
    }
}
