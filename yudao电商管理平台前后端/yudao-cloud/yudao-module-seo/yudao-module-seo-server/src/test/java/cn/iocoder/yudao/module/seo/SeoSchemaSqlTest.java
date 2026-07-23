package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SeoSchemaSqlTest {

    @Test
    void shouldExecuteFoundationSchemaAndExposeRequiredContracts() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:seo_foundation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/create_tables.sql"));

            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(tableNames(metadata)).contains("seo_site_config", "seo_metadata", "seo_analysis",
                    "seo_analysis_item", "seo_keyword_analysis", "seo_keyword_analysis_item");
            assertThat(columnNames(metadata, "seo_site_config"))
                    .contains("site_id", "site_url", "default_locale", "tenant_id", "deleted", "active_record");
            assertThat(columnNames(metadata, "seo_metadata"))
                    .contains("entity_type", "entity_id", "locale", "related_keyphrases", "publish_status", "version",
                            "active_record", "latest_analysis_id");
            assertThat(columnNames(metadata, "seo_analysis"))
                    .contains("site_id", "source_type", "input_snapshot", "content_hash", "idempotency_key",
                            "overall_relevance_percent", "confidence_percent", "analysis_status", "tenant_id");
            assertThat(columnNames(metadata, "seo_keyword_analysis"))
                    .contains("analysis_id", "keyword_type", "normalized_keyword", "key_position_percent",
                            "lexical_match_percent", "semantic_percent", "distribution_percent",
                            "intent_coverage_percent", "relevance_percent", "confidence_percent");
            assertThat(columnNames(metadata, "seo_keyword_analysis_item"))
                    .contains("keyword_analysis_id", "rule_code", "dimension", "severity", "content_location",
                            "evidence", "reason", "recommendation", "recoverable_score");

            assertColumn(metadata, "seo_site_config", "id", Types.BIGINT, 64, false, null);
            assertColumn(metadata, "seo_site_config", "site_id", Types.BIGINT, 64, false, null);
            assertColumn(metadata, "seo_site_config", "site_name", Types.VARCHAR, 128, false, null);
            assertColumn(metadata, "seo_site_config", "site_url", Types.VARCHAR, 512, false, null);
            assertColumn(metadata, "seo_site_config", "default_robots", Types.VARCHAR, 64, false, "'index,follow'");
            assertColumn(metadata, "seo_site_config", "default_locale", Types.VARCHAR, 32, false, "'zh-CN'");
            assertColumn(metadata, "seo_site_config", "deleted", Types.BOOLEAN, 1, false, "FALSE");
            assertColumn(metadata, "seo_site_config", "tenant_id", Types.BIGINT, 64, false, "0");

            assertColumn(metadata, "seo_metadata", "site_id", Types.BIGINT, 64, false, null);
            assertColumn(metadata, "seo_metadata", "entity_type", Types.VARCHAR, 32, false, null);
            assertColumn(metadata, "seo_metadata", "entity_id", Types.BIGINT, 64, false, null);
            assertColumn(metadata, "seo_metadata", "locale", Types.VARCHAR, 32, false, "'zh-CN'");
            assertColumn(metadata, "seo_metadata", "related_keyphrases", Types.VARCHAR, 4000, true, null);
            assertColumn(metadata, "seo_metadata", "robots_index", Types.BOOLEAN, 1, false, "TRUE");
            assertColumn(metadata, "seo_metadata", "robots_follow", Types.BOOLEAN, 1, false, "TRUE");
            assertColumn(metadata, "seo_metadata", "publish_status", Types.VARCHAR, 16, false, "'DRAFT'");
            assertColumn(metadata, "seo_metadata", "version", Types.INTEGER, 32, false, "1");
            assertColumn(metadata, "seo_metadata", "published_time", Types.TIMESTAMP, 26, true, null);
            assertColumn(metadata, "seo_metadata", "deleted", Types.BOOLEAN, 1, false, "FALSE");
            assertColumn(metadata, "seo_metadata", "tenant_id", Types.BIGINT, 64, false, "0");

            assertIndex(metadata, "seo_site_config", "uk_tenant_site_active", true,
                    "tenant_id", "site_id", "active_record");
            assertIndex(metadata, "seo_metadata", "uk_entity_locale_active", true,
                    "tenant_id", "site_id", "entity_type", "entity_id", "locale", "active_record");
            assertIndex(metadata, "seo_metadata", "idx_public_resolve", false,
                    "tenant_id", "site_id", "entity_type", "entity_id", "locale", "publish_status");
            assertIndex(metadata, "seo_analysis", "uk_analysis_idempotency_active", true,
                    "tenant_id", "idempotency_key", "active_record");
            assertIndex(metadata, "seo_keyword_analysis", "uk_keyword_order_active", true,
                    "tenant_id", "analysis_id", "keyword_type", "sort", "active_record");
            assertIndex(metadata, "seo_keyword_analysis", "uk_keyword_normalized_active", true,
                    "tenant_id", "analysis_id", "normalized_keyword", "active_record");

            assertActiveRecordLifecycle(connection);
            assertKeywordUniqueness(connection);
        }
    }

    private static void assertKeywordUniqueness(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO seo_analysis "
                    + "(site_id, source_type, entity_type, locale, focus_keyphrase, input_snapshot, content_hash, "
                    + "idempotency_key, engine_version, rule_profile_version, dictionary_version, tenant_id) "
                    + "VALUES (11, 'MANUAL', 'PRODUCT', 'zh-CN', '实木餐桌', '{}', "
                    + "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'run-1', "
                    + "'engine-v1', 'rules-v1', 'dictionary-v1', 1)");
            statement.executeUpdate("INSERT INTO seo_keyword_analysis "
                    + "(analysis_id, keyword_type, keyword, normalized_keyword, sort, analysis_status, "
                    + "dictionary_version, tenant_id) VALUES (1, 'FOCUS', '实木餐桌', '实木餐桌', 0, "
                    + "'PARTIAL', 'dictionary-v1', 1)");
            assertThatThrownBySql(() -> statement.executeUpdate("INSERT INTO seo_keyword_analysis "
                    + "(analysis_id, keyword_type, keyword, normalized_keyword, sort, analysis_status, "
                    + "dictionary_version, tenant_id) VALUES (1, 'RELATED', '实木 餐桌', '实木餐桌', 1, "
                    + "'PARTIAL', 'dictionary-v1', 1)"));
        }
    }

    private static void assertActiveRecordLifecycle(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO seo_site_config (site_id, site_name, site_url, tenant_id) "
                    + "VALUES (11, 'one', 'https://one.example', 1)");
            assertThatThrownBySql(() -> statement.executeUpdate(
                    "INSERT INTO seo_site_config (site_id, site_name, site_url, tenant_id) "
                            + "VALUES (11, 'duplicate', 'https://duplicate.example', 1)"));
            statement.executeUpdate("UPDATE seo_site_config SET deleted = TRUE WHERE site_id = 11 AND deleted = FALSE");
            statement.executeUpdate("INSERT INTO seo_site_config (site_id, site_name, site_url, tenant_id) "
                    + "VALUES (11, 'two', 'https://two.example', 1)");
            statement.executeUpdate("UPDATE seo_site_config SET deleted = TRUE WHERE site_id = 11 AND deleted = FALSE");
            statement.executeUpdate("INSERT INTO seo_site_config (site_id, site_name, site_url, tenant_id) "
                    + "VALUES (11, 'three', 'https://three.example', 1)");

            statement.executeUpdate("INSERT INTO seo_metadata (site_id, entity_type, entity_id, locale, tenant_id) "
                    + "VALUES (11, 'PRODUCT', 101, 'zh-CN', 1)");
            assertThatThrownBySql(() -> statement.executeUpdate(
                    "INSERT INTO seo_metadata (site_id, entity_type, entity_id, locale, tenant_id) "
                            + "VALUES (11, 'PRODUCT', 101, 'zh-CN', 1)"));
            statement.executeUpdate("UPDATE seo_metadata SET deleted = TRUE "
                    + "WHERE site_id = 11 AND entity_id = 101 AND deleted = FALSE");
            statement.executeUpdate("INSERT INTO seo_metadata (site_id, entity_type, entity_id, locale, tenant_id) "
                    + "VALUES (11, 'PRODUCT', 101, 'zh-CN', 1)");
            statement.executeUpdate("UPDATE seo_metadata SET deleted = TRUE "
                    + "WHERE site_id = 11 AND entity_id = 101 AND deleted = FALSE");
            statement.executeUpdate("INSERT INTO seo_metadata (site_id, entity_type, entity_id, locale, tenant_id) "
                    + "VALUES (11, 'PRODUCT', 101, 'zh-CN', 1)");
        }
    }

    private static void assertThatThrownBySql(SqlRunnable action) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run).isInstanceOf(SQLException.class);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    private static Set<String> tableNames(DatabaseMetaData metadata) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet resultSet = metadata.getTables(null, null, "seo_%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                names.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private static Set<String> columnNames(DatabaseMetaData metadata, String tableName) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                names.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return names;
    }

    private static void assertColumn(DatabaseMetaData metadata, String tableName, String columnName,
                                     int jdbcType, int size, boolean nullable, String defaultValue) throws SQLException {
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).as("column %s.%s exists", tableName, columnName).isTrue();
            assertThat(resultSet.getInt("DATA_TYPE")).as("%s.%s JDBC type", tableName, columnName).isEqualTo(jdbcType);
            assertThat(resultSet.getInt("COLUMN_SIZE")).as("%s.%s size", tableName, columnName).isEqualTo(size);
            assertThat(resultSet.getInt("NULLABLE")).as("%s.%s nullability", tableName, columnName)
                    .isEqualTo(nullable ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls);
            assertThat(resultSet.getString("COLUMN_DEF")).as("%s.%s default", tableName, columnName)
                    .isEqualTo(defaultValue);
            assertThat(resultSet.next()).as("column %s.%s is unique in metadata", tableName, columnName).isFalse();
        }
    }

    private static void assertIndex(DatabaseMetaData metadata, String tableName, String indexName,
                                    boolean unique, String... expectedColumns) throws SQLException {
        List<String> columns = new ArrayList<>();
        Boolean actualUnique = null;
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                if (indexName.equals(resultSet.getString("INDEX_NAME"))) {
                    actualUnique = !resultSet.getBoolean("NON_UNIQUE");
                    int ordinalPosition = resultSet.getInt("ORDINAL_POSITION");
                    assertThat(ordinalPosition).isEqualTo(columns.size() + 1);
                    columns.add(resultSet.getString("COLUMN_NAME"));
                }
            }
        }
        assertThat(actualUnique).as("index %s exists", indexName).isEqualTo(unique);
        assertThat(columns).as("index %s columns", indexName).containsExactly(expectedColumns);
    }

}
