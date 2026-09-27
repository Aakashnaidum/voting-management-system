package io.github.aakashnaidum.voting.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Minimal JDBC access: one connection per unit of work, closed by the caller. */
public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        loadDriver(url);
    }

    /**
     * Servlet containers load web-app jars in a separate class loader, so JDBC's
     * automatic driver discovery can miss them. Register the driver explicitly.
     */
    private static void loadDriver(String url) {
        String driver = url.startsWith("jdbc:h2:") ? "org.h2.Driver"
                : url.startsWith("jdbc:mysql:") ? "com.mysql.cj.jdbc.Driver" : null;
        if (driver == null) return;
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not on classpath: " + driver, e);
        }
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Runs a classpath SQL script whose statements are separated by ';' at line end. */
    public void runScript(String resource) {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        StringBuilder current = new StringBuilder();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String line : sql.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String stmt = current.toString().trim();
                    st.execute(stmt.substring(0, stmt.length() - 1));
                    current.setLength(0);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed running " + resource + ": " + e.getMessage(), e);
        }
    }

    public void createSchema() {
        runScript("/db/schema.sql");
    }
}
