package io.github.aakashnaidum.voting.config;

/**
 * Runtime configuration from environment variables or JVM system properties
 * (system property names are the lower-case, dot-separated form, e.g. voting.db.url).
 * No credentials are stored in source code.
 */
public final class AppConfig {
    private AppConfig() {}

    public static String get(String envName, String defaultValue) {
        String prop = System.getProperty(envName.toLowerCase().replace('_', '.'));
        if (prop != null && !prop.isEmpty()) return prop;
        String env = System.getenv(envName);
        return env != null && !env.isEmpty() ? env : defaultValue;
    }

    /** JDBC URL. Default: an in-memory H2 database (data is lost on restart). */
    public static String dbUrl() {
        return get("VOTING_DB_URL", "jdbc:h2:mem:voting;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    public static String dbUser() { return get("VOTING_DB_USER", "sa"); }

    public static String dbPassword() { return get("VOTING_DB_PASSWORD", ""); }

    /** When true, the schema is created and synthetic demo accounts are loaded at startup. */
    public static boolean demoMode() { return Boolean.parseBoolean(get("VOTING_DEMO", "false")); }

    /** Initial administrator; created at startup only if no admin exists. */
    public static String adminEmail() { return get("VOTING_ADMIN_EMAIL", null); }

    public static String adminPassword() { return get("VOTING_ADMIN_PASSWORD", null); }
}
