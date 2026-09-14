import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Compiles with the JDK alone; at runtime all Flyway/JDBC libraries come from the CI image. */
public final class CloneMigration {
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private static Object call(Object object, String method, Class<?>[] types, Object... values) throws Exception {
        try {
            return object.getClass().getMethod(method, types).invoke(object, values);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Flyway operation failed: " + method, error.getCause());
        }
    }
    private static Object call(Object object, String method) throws Exception {
        return call(object, method, new Class<?>[0]);
    }
    private static int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            require(rows.next(), "Expected one database value");
            return rows.getInt(1);
        }
    }
    public static void main(String[] args) throws Exception {
        require(args.length >= 2 && args.length <= 4, "Expected a verified clone profile");
        String environment = args.length >= 3 ? args[2] : "test";
        require(environment.equals("test") || environment.equals("production"), "Unknown clone profile");
        boolean production = environment.equals("production");
        int sourceVersion = production ? 48 : 47;
        int targetVersion = args.length == 4 ? Integer.parseInt(args[3]) : 49;
        require(targetVersion == 49 || (production && targetVersion == 50), "Unreviewed target schema");
        require(args[0].matches("oakved_cd_" + environment + "_(rehearse|live)_[0-9a-f]{16}"), "Only an owned clone is allowed");
        require(args[1].equals(production ? "codex_release_v47_20260814_162836" : "oakved_v032_20260729"), "Unexpected legacy database");
        String database = args[0], user = System.getenv("ERP_CLONE_USER"), password = System.getenv("ERP_CLONE_PASSWORD");
        require(user != null && user.matches((production ? "erp_pd_m_" : "erp_cd_m_") + "[0-9a-f]{16}") && password != null && !password.isBlank(), "Restricted clone credentials required");
        String url = "jdbc:mysql://127.0.0.1:3306/" + database
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=60000";
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            require(database.equals(connection.getCatalog()), "Wrong connection catalog");
            require(scalar(connection, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1") == sourceVersion, "Unexpected source clone version");
            require(scalar(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0") == 0, "Failed migration history");
            boolean denied = false;
            try (Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT 1 FROM " + args[1] + ".flyway_schema_history LIMIT 1");
            } catch (SQLException expected) {
                denied = expected.getErrorCode() == 1044 || expected.getErrorCode() == 1142;
            }
            require(denied, "Clone account can access the legacy database");
        }
        Object configuration = Class.forName("org.flywaydb.core.Flyway").getMethod("configure").invoke(null);
        call(configuration, "dataSource", new Class<?>[]{String.class, String.class, String.class}, url, user, password);
        call(configuration, "locations", new Class<?>[]{String[].class}, (Object) new String[]{"classpath:db/migration"});
        call(configuration, "encoding", new Class<?>[]{String.class}, "UTF-8");
        for (String key : new String[]{"validateMigrationNaming", "validateOnMigrate", "cleanDisabled"})
            call(configuration, key, new Class<?>[]{boolean.class}, true);
        for (String key : new String[]{"baselineOnMigrate", "outOfOrder"})
            call(configuration, key, new Class<?>[]{boolean.class}, false);
        Object flyway = call(configuration, "load");
        Object result = call(flyway, "migrate");
        int executed = result.getClass().getField("migrationsExecuted").getInt(result);
        require(executed == targetVersion - sourceVersion, "Unexpected migration count");
        Object validation = call(flyway, "validateWithResult");
        require(validation.getClass().getField("validationSuccessful").getBoolean(validation), "Flyway checksum validation failed");
        Object repeated = call(flyway, "migrate");
        require(repeated.getClass().getField("migrationsExecuted").getInt(repeated) == 0, "Repeated migration must be a no-op");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            require(scalar(connection, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1") == targetVersion, "Unexpected migrated schema version");
        }
        System.out.println("ERP_CLONE_RESULT={\"version\":" + targetVersion + ",\"migrations_executed\":" + executed + ",\"repeat_migrations_executed\":0,\"checksums_valid\":true,\"source_access_denied\":true}");
    }
}
