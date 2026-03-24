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
        try {
            // Load credentials from db.properties — never hardcode them here.
            HikariConfig config = new HikariConfig("/db.properties");
            DATA_SOURCE = new HikariDataSource(config);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                "Failed to load db.properties: " + e.getMessage());
        }
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

    /** Shuts down the pool gracefully. Called by the shutdown hook in Launcher. */
    public static void close() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }
}
