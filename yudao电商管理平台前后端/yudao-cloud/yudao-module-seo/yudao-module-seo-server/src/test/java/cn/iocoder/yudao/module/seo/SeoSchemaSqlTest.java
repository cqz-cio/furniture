package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            assertThat(tableNames(metadata)).contains("seo_site_config", "seo_metadata");
            assertThat(columnNames(metadata, "seo_site_config"))
                    .contains("site_id", "site_url", "default_locale", "tenant_id", "deleted");
            assertThat(columnNames(metadata, "seo_metadata"))
                    .contains("entity_type", "entity_id", "locale", "related_keyphrases", "publish_status", "version");

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

            assertIndex(metadata, "seo_site_config", "uk_tenant_site_deleted", true,
                    "tenant_id", "site_id", "deleted");
            assertIndex(metadata, "seo_metadata", "uk_entity_locale_deleted", true,
                    "tenant_id", "site_id", "entity_type", "entity_id", "locale", "deleted");
            assertIndex(metadata, "seo_metadata", "idx_public_resolve", false,
                    "tenant_id", "site_id", "entity_type", "entity_id", "locale", "publish_status");
        }
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
