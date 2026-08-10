package cn.iocoder.yudao.server.framework.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes Flyway the only migration engine used by {@code yudao-server} while
 * safely adopting databases created by the retired {@code schema_migrations}
 * PowerShell runner.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OakvedFlywayConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(OakvedFlywayConfiguration.class);

    @Bean
    FlywayConfigurationCustomizer oakvedLegacyMigrationAdoptionCustomizer(
            DataSource dataSource, ResourceLoader resourceLoader) {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        OakvedLegacyMigrationAdoptionPlan plan = OakvedLegacyMigrationAdoptionPlan.inspect(dataSource, resolver);
        if (plan.requiresAdoption()) {
            LOGGER.warn("Verified legacy database migration ledger through V{}. Flyway will adopt it before migration.",
                    plan.baselineVersion());
        }
        return configuration -> {
            if (!plan.requiresAdoption()) {
                return;
            }
            configuration.baselineVersion(MigrationVersion.fromVersion(plan.baselineVersion()));
            configuration.baselineDescription("Verified legacy schema_migrations V" + plan.baselineVersion());
            configuration.baselineOnMigrate(true);
        };
    }

}

record OakvedLegacyMigrationAdoptionPlan(Mode mode, String baselineVersion) {

    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d{3})__([a-z0-9_]+)\\.sql$");
    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";
    private static final String LEGACY_HISTORY_TABLE = "schema_migrations";

    enum Mode {
        EMPTY,
        FLYWAY,
        LEGACY
    }

    boolean requiresAdoption() {
        return mode == Mode.LEGACY;
    }

    static OakvedLegacyMigrationAdoptionPlan inspect(
            DataSource dataSource, ResourcePatternResolver resourceResolver) {
        try (Connection connection = dataSource.getConnection()) {
            int tableCount = countTables(connection);
            if (tableCount == 0) {
                return new OakvedLegacyMigrationAdoptionPlan(Mode.EMPTY, null);
            }
            if (tableExists(connection, FLYWAY_HISTORY_TABLE)) {
                return new OakvedLegacyMigrationAdoptionPlan(Mode.FLYWAY, null);
            }
            if (!tableExists(connection, LEGACY_HISTORY_TABLE)) {
                throw new IllegalStateException("Refusing Flyway adoption: the database is non-empty but has neither "
                        + FLYWAY_HISTORY_TABLE + " nor " + LEGACY_HISTORY_TABLE + ".");
            }

            List<MigrationDescriptor> catalog = loadCatalog(resourceResolver);
            List<MigrationDescriptor> ledger = loadLegacyLedger(connection);
            validateLegacyPrefix(catalog, ledger);
            return new OakvedLegacyMigrationAdoptionPlan(Mode.LEGACY, ledger.get(ledger.size() - 1).version());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect the database before Flyway migration.", exception);
        }
    }

    static List<MigrationDescriptor> loadCatalog(ResourcePatternResolver resourceResolver) {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources("classpath*:db/migration/V*.sql");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read packaged Flyway migrations.", exception);
        }
        Map<Integer, MigrationDescriptor> byVersion = new TreeMap<>();
        for (Resource resource : resources) {
            String scriptName = resource.getFilename();
            Matcher matcher = MIGRATION_NAME.matcher(scriptName == null ? "" : scriptName);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid packaged Flyway migration name: " + scriptName);
            }
            int numericVersion = Integer.parseInt(matcher.group(1));
            String source;
            try (InputStream input = resource.getInputStream()) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read packaged migration " + scriptName + ".", exception);
            }
            MigrationDescriptor descriptor = new MigrationDescriptor(
                    matcher.group(1), matcher.group(2).replace('_', ' '), scriptName, sha256(normalize(source)));
            if (byVersion.put(numericVersion, descriptor) != null) {
                throw new IllegalStateException("Duplicate packaged migration version V" + matcher.group(1) + ".");
            }
        }
        if (byVersion.isEmpty()) {
            throw new IllegalStateException("No packaged Flyway versioned migrations were found.");
        }
        List<MigrationDescriptor> catalog = new ArrayList<>(byVersion.values());
        for (int index = 0; index < catalog.size(); index++) {
            String expected = String.format("%03d", index + 1);
            if (!expected.equals(catalog.get(index).version())) {
                throw new IllegalStateException("Packaged Flyway migration catalog is not contiguous; expected V"
                        + expected + " but found V" + catalog.get(index).version() + ".");
            }
        }
        return List.copyOf(catalog);
    }

    static void validateLegacyPrefix(List<MigrationDescriptor> catalog, List<MigrationDescriptor> ledger) {
        if (ledger.isEmpty()) {
            throw new IllegalStateException("Refusing Flyway adoption: schema_migrations contains no versions.");
        }
        if (ledger.size() > catalog.size()) {
            throw new IllegalStateException("Refusing Flyway adoption: the legacy database is ahead of the packaged catalog.");
        }
        for (int index = 0; index < ledger.size(); index++) {
            MigrationDescriptor expected = catalog.get(index);
            MigrationDescriptor actual = ledger.get(index);
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Refusing Flyway adoption: legacy migration V" + actual.version()
                        + " does not match packaged " + expected.scriptName() + ".");
            }
        }
    }

    static List<MigrationDescriptor> loadLegacyLedger(Connection connection) throws SQLException {
        List<MigrationDescriptor> ledger = new ArrayList<>();
        String sql = "SELECT version, description, script_name, checksum_sha256 "
                + "FROM schema_migrations ORDER BY version";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ledger.add(new MigrationDescriptor(
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("script_name"),
                        resultSet.getString("checksum_sha256")));
            }
        }
        ledger.sort(Comparator.comparing(MigrationDescriptor::version));
        return List.copyOf(ledger);
    }

    private static int countTables(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("MySQL did not return the schema table count.");
            }
            return resultSet.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private static String normalize(String source) {
        String normalized = source.replace("\r\n", "\n");
        int end = normalized.length();
        while (end > 0 && Character.isWhitespace(normalized.charAt(end - 1))) {
            end--;
        }
        return normalized.substring(0, end) + "\n";
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    record MigrationDescriptor(String version, String description, String scriptName, String checksum) {
    }

}
