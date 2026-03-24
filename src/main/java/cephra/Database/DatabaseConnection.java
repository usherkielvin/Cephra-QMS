package cephra.Database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DatabaseConnection - Manages a HikariCP connection pool for the Cephra system.
 *
 * Uses HikariCP (already a project dependency) instead of raw DriverManager calls.
 * JDBC 4.0+ auto-discovers the MySQL driver, so no Class.forName() is needed.
 */
public class DatabaseConnection {

    private static final HikariDataSource DATA_SOURCE;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/cephradb");
        config.setUsername("root");
        config.setPassword("ushpons08");

        // Pool tuning — sensible defaults for a desktop/demo app
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);   // 30 s
        config.setIdleTimeout(600_000);        // 10 min
        config.setMaxLifetime(1_800_000);      // 30 min
        config.setPoolName("CephraPool");

        // Recommended MySQL properties
        config.addDataSourceProperty("cachePrepStmts",          "true");
        config.addDataSourceProperty("prepStmtCacheSize",       "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit",   "2048");
        config.addDataSourceProperty("useServerPrepStmts",      "true");

        DATA_SOURCE = new HikariDataSource(config);
    }

    /** Returns a pooled connection. Caller must close it (try-with-resources recommended). */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /** Quick health-check — useful on startup. */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Database connection test failed: " + e.getMessage());
            return false;
        }
    }

    /** Shuts down the pool gracefully (call on app exit if needed). */
    public static void close() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}