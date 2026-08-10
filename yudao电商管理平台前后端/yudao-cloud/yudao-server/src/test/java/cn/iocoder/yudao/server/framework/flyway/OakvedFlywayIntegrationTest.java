package cn.iocoder.yudao.server.framework.flyway;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OakvedFlywayIntegrationTest {

    @Test
    void migratesAnEmptyDatabaseAndSafelyAdoptsTheLegacyLedger() throws Exception {
        String adminUrl = System.getenv("OAKVED_FLYWAY_TEST_ADMIN_URL");
        assumeTrue(adminUrl != null && !adminUrl.isBlank(),
                "Set OAKVED_FLYWAY_TEST_ADMIN_URL to run the MySQL integration test.");
        String user = environment("OAKVED_FLYWAY_TEST_USER", "root");
        String password = environment("OAKVED_FLYWAY_TEST_PASSWORD", "root");
        String database = "oakved_flyway_test_" + UUID.randomUUID().toString().replace("-", "")
                .toLowerCase(Locale.ROOT);

        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setUrl(databaseUrl(adminUrl, database));
            dataSource.setUser(user);
            dataSource.setPassword(password);
            PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
            List<OakvedLegacyMigrationAdoptionPlan.MigrationDescriptor> catalog =
                    OakvedLegacyMigrationAdoptionPlan.loadCatalog(resourceResolver);
            String expectedVersion = catalog.get(catalog.size() - 1).version();
            String expectedLegacyVersion = expectedVersion;
            String baselineVersion = latestBaselineVersion(resourceResolver);

            Flyway freshFlyway = baseConfiguration(dataSource).load();
            MigrateResult freshResult = freshFlyway.migrate();
            assertTrue(freshResult.migrationsExecuted > 0);
            assertEquals(expectedVersion, freshFlyway.info().current().getVersion().getVersion());
            assertEquals(Integer.parseInt(baselineVersion), scalar(dataSource, "SELECT COUNT(*) FROM schema_migrations"));
            assertTrue(scalar(dataSource, "SELECT COUNT(*) FROM system_users") > 0);
            assertTrue(freshFlyway.validateWithResult().validationSuccessful);

            int usersBeforeAdoption = scalar(dataSource, "SELECT COUNT(*) FROM system_users");
            List<OakvedLegacyMigrationAdoptionPlan.MigrationDescriptor> baselineLedger;
            try (Connection connection = dataSource.getConnection()) {
                baselineLedger = OakvedLegacyMigrationAdoptionPlan.loadLegacyLedger(connection);
            }
            OakvedLegacyMigrationAdoptionPlan.validateLegacyPrefix(catalog, baselineLedger);
            appendLegacyLedger(dataSource, catalog, baselineLedger.size());
            assertEquals(catalog.size(), scalar(dataSource, "SELECT COUNT(*) FROM schema_migrations"));
            execute(dataSource, "DROP TABLE flyway_schema_history");
            OakvedLegacyMigrationAdoptionPlan plan = OakvedLegacyMigrationAdoptionPlan.inspect(
                    dataSource, resourceResolver);
            assertTrue(plan.requiresAdoption());
            assertEquals(expectedLegacyVersion, plan.baselineVersion());

            Flyway adoptedFlyway = baseConfiguration(dataSource)
                    .baselineVersion(MigrationVersion.fromVersion(plan.baselineVersion()))
                    .baselineDescription("CI legacy adoption")
                    .baselineOnMigrate(true)
                    .load();
            adoptedFlyway.migrate();
            assertEquals(expectedVersion, adoptedFlyway.info().current().getVersion().getVersion());
            assertEquals(usersBeforeAdoption, scalar(dataSource, "SELECT COUNT(*) FROM system_users"));
            assertEquals(0, adoptedFlyway.migrate().migrationsExecuted);
            assertTrue(adoptedFlyway.validateWithResult().validationSuccessful);
        } finally {
            try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration baseConfiguration(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .validateMigrationNaming(true)
                .outOfOrder(false)
                .cleanDisabled(true);
    }

    private static int scalar(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static void execute(DataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void appendLegacyLedger(
            DataSource dataSource,
            List<OakvedLegacyMigrationAdoptionPlan.MigrationDescriptor> catalog,
            int firstMissingIndex) throws Exception {
        String sql = "INSERT INTO schema_migrations(version, description, script_name, checksum_sha256) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = firstMissingIndex; index < catalog.size(); index++) {
                OakvedLegacyMigrationAdoptionPlan.MigrationDescriptor migration = catalog.get(index);
                statement.setString(1, migration.version());
                statement.setString(2, migration.description());
                statement.setString(3, migration.scriptName());
                statement.setString(4, migration.checksum());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static String latestBaselineVersion(PathMatchingResourcePatternResolver resourceResolver) throws Exception {
        String latest = null;
        for (org.springframework.core.io.Resource resource
                : resourceResolver.getResources("classpath*:db/migration/B*__oakved_baseline.sql")) {
            String fileName = resource.getFilename();
            assertTrue(fileName != null && fileName.matches("B\\d{3}__oakved_baseline\\.sql"));
            String version = fileName.substring(1, 4);
            if (latest == null || version.compareTo(latest) > 0) {
                latest = version;
            }
        }
        assertTrue(latest != null, "At least one packaged Flyway baseline is required");
        return latest;
    }

    private static String databaseUrl(String adminUrl, String database) {
        int queryIndex = adminUrl.indexOf('?');
        String query = queryIndex >= 0 ? adminUrl.substring(queryIndex) : "";
        String withoutQuery = queryIndex >= 0 ? adminUrl.substring(0, queryIndex) : adminUrl;
        String base = withoutQuery.endsWith("/") ? withoutQuery : withoutQuery + "/";
        return base + database + query;
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

}
