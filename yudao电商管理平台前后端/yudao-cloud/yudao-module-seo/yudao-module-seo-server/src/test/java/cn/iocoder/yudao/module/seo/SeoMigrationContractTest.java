package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeoMigrationContractTest {

    private static final Path MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V016__seo_foundation.sql");

    @Test
    void shouldFailWhenReservedMenuIdsBelongToUnrelatedRows() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains("CREATE TEMPORARY TABLE `seo_menu_id_guard`");
        assertThat(sql).contains("CHECK (`valid` = 1)");
        String guardSql = sql.substring(sql.indexOf("CREATE TEMPORARY TABLE `seo_menu_id_guard`"),
                sql.indexOf("-- Insert and resolve the SEO root menu."));
        for (int id = 8100; id <= 8109; id++) {
            assertThat(guardSql).contains("`id` = " + id);
        }
    }

    @Test
    void shouldResolveActualMenuIdsAndScopeChildPathsByParent() throws IOException {
        String sql = migrationSql();

        assertThat(sql).containsSubsequence(
                "SELECT 8100,",
                "SET @seo_root_menu_id =",
                "SELECT 8101,",
                "SET @seo_metadata_menu_id =",
                "SELECT 8102,",
                "SET @seo_site_config_menu_id =");
        assertThat(selectRow(sql, 8101)).contains(",@seo_root_menu_id,'metadata',");
        assertThat(selectRow(sql, 8102)).contains(",@seo_root_menu_id,'site-config',");
        assertThat(sql).contains(
                "`parent_id` = @seo_root_menu_id AND `path` = 'metadata'",
                "`parent_id` = @seo_root_menu_id AND `path` = 'site-config'");
    }

    @Test
    void shouldRejectReservedMetadataIdWhenSamePathBelongsToAnotherParent() throws IOException {
        String sql = migrationSql();

        assertPostResolutionReservedIdGuard(sql, 8101, "@seo_metadata_menu_id", "SELECT 8102,");
    }

    @Test
    void shouldRejectReservedSiteConfigIdWhenSamePathBelongsToAnotherParent() throws IOException {
        String sql = migrationSql();

        assertPostResolutionReservedIdGuard(sql, 8102, "@seo_site_config_menu_id", "SELECT 8103,");
    }

    @Test
    void shouldAttachEveryButtonToItsResolvedChildMenu() throws IOException {
        String sql = migrationSql();

        for (int id = 8103; id <= 8107; id++) {
            assertThat(selectRow(sql, id)).contains(",@seo_metadata_menu_id,'','','','',");
        }
        for (int id = 8108; id <= 8109; id++) {
            assertThat(selectRow(sql, id)).contains(",@seo_site_config_menu_id,'','','','',");
        }
    }

    private static String migrationSql() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path migration = directory.resolve(MIGRATION_RELATIVE_PATH);
            if (Files.isRegularFile(migration)) {
                return Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
            directory = directory.getParent();
        }
        throw new IOException("Cannot find " + MIGRATION_RELATIVE_PATH + " from " + Path.of("").toAbsolutePath());
    }

    private static String selectRow(String sql, int id) {
        Matcher matcher = Pattern.compile("(?m)^SELECT " + id + ",.*$").matcher(sql);
        assertThat(matcher.find()).as("menu SELECT for id %s", id).isTrue();
        return matcher.group();
    }

    private static void assertPostResolutionReservedIdGuard(String sql, int reservedId,
                                                              String resolvedIdVariable, String nextMenuRow) {
        int resolutionIndex = sql.indexOf("SET " + resolvedIdVariable + " =");
        int nextMenuIndex = sql.indexOf(nextMenuRow, resolutionIndex);

        assertThat(resolutionIndex).as("resolution for %s", resolvedIdVariable).isNotNegative();
        assertThat(nextMenuIndex).as("next menu row after %s", resolvedIdVariable).isGreaterThan(resolutionIndex);
        assertThat(sql.substring(resolutionIndex, nextMenuIndex))
                .as("post-resolution reserved ID guard for %s", reservedId)
                .contains("INSERT INTO `seo_menu_id_guard` (`valid`)",
                        "`id` = " + reservedId + " AND `id` <> " + resolvedIdVariable);
    }

}
