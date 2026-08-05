package cn.iocoder.yudao.module.seo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeoMigrationContractTest {

    private static final Path MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V019__seo_foundation.sql");
    private static final Path ACTIVE_RECORD_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V020__seo_active_record_uniqueness.sql");
    private static final Path KEYWORD_ANALYSIS_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V026__seo_keyword_relevance_analysis.sql");
    private static final Path KEYWORD_ANALYSIS_MENU_REPAIR_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V027__repair_seo_analysis_menu_registration.sql");
    private static final Path WEBSITE_NAVIGATION_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V039__website_navigation_preview.sql");
    private static final Path WEBSITE_NAVIGATION_OPERATOR_PERMISSION_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V040__grant_navigation_to_vanz_operator.sql");
    private static final Path OAKVED_NAVIGATION_TREE_MIGRATION_RELATIVE_PATH =
            Path.of("sql", "mysql", "migrations", "V043__oakved_b2c_navigation_tree.sql");
    private static final Path MYSQL_BASELINE_RELATIVE_PATH =
            Path.of("sql", "mysql", "oakved-baseline.sql");
    private static final String RELEASED_KEYWORD_ANALYSIS_MIGRATION_SHA256 =
            "91aeaf8bca0249c1692314c99ad5a9e7d7b380e257d003c26517c0e381114560";

    @Test
    void shouldCreateKeywordAnalysisHistoryAndEvidenceTables() throws IOException {
        String sql = migrationSql(KEYWORD_ANALYSIS_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS `seo_analysis`",
                "CREATE TABLE IF NOT EXISTS `seo_analysis_item`",
                "CREATE TABLE IF NOT EXISTS `seo_keyword_analysis`",
                "CREATE TABLE IF NOT EXISTS `seo_keyword_analysis_item`",
                "`semantic_percent` int DEFAULT NULL",
                "UNIQUE KEY `uk_analysis_idempotency_active`",
                "UNIQUE KEY `uk_keyword_normalized_active`",
                "ADD COLUMN `latest_analysis_id` bigint DEFAULT NULL");
    }

    @Test
    void shouldPreserveReleasedKeywordAnalysisMigration() throws IOException {
        String sql = migrationSql(KEYWORD_ANALYSIS_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "SET @seo_root_menu_id =",
                "SELECT 8112,'关键词分析'",
                "'analysis','ep:data-analysis','seo/analysis/index','SeoAnalysis'",
                "SET @seo_analysis_menu_id =",
                "SELECT 8110,'运行分析'",
                "SELECT 8111,'分析查询'",
                "'seo:analysis:run'",
                "'seo:analysis:query'",
                "@seo_analysis_menu_id");
        assertThat(normalizedSha256(sql)).isEqualTo(RELEASED_KEYWORD_ANALYSIS_MIGRATION_SHA256);
    }

    @Test
    void shouldRepairKeywordAnalysisMenuInForwardMigrationWithoutReservedIds() throws IOException {
        String sql = migrationSql(KEYWORD_ANALYSIS_MENU_REPAIR_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "V026 is already released and must remain byte-for-byte immutable",
                "INSERT INTO `system_menu`",
                "SELECT\n  '关键词分析'",
                "'seo:analysis:run'",
                "'seo:analysis:query'",
                "CREATE TEMPORARY TABLE `seo_analysis_menu_registration_guard`",
                "CHECK (`valid` = 1)");
        assertThat(sql).doesNotContain("SELECT 8110,", "SELECT 8111,", "SELECT 8112,");
    }

    @Test
    void shouldCreateVersionedWebsiteNavigationAndSecurePreviewPermissions() throws IOException {
        String sql = migrationSql(WEBSITE_NAVIGATION_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS `website_navigation_revision`",
                "CREATE TABLE IF NOT EXISTS `website_navigation_item`",
                "UNIQUE KEY `uk_navigation_draft_active`",
                "UNIQUE KEY `uk_navigation_published_active`",
                "'navigation','ep:guide',",
                "'seo/navigation/index','SeoNavigation'",
                "'seo:navigation:query'",
                "'seo:navigation:update'",
                "'seo:navigation:preview'",
                "'seo:navigation:publish'",
                "tenant.`id` = 162",
                "CREATE TEMPORARY TABLE `website_navigation_menu_guard`",
                "CHECK (`valid` = 1)");
        assertThat(sql).doesNotContain("UPDATE `system_tenant_package`\nSET");
    }

    @Test
    void shouldIncludeWebsiteNavigationMigrationsInInstallBaseline() throws IOException {
        String baseline = migrationSql(MYSQL_BASELINE_RELATIVE_PATH);

        assertThat(baseline).contains(
                "-- BEGIN V039__website_navigation_preview.sql",
                "CREATE TABLE IF NOT EXISTS `website_navigation_revision`",
                "VALUES('039','website navigation preview','V039__website_navigation_preview.sql',"
                        + "'33e5898a22e2fa9d8b50d7b7d08f7ec49aa4a89d034c53b04bdf1bee764f6030')",
                "-- BEGIN V040__grant_navigation_to_vanz_operator.sql",
                "VALUES('040','grant navigation to vanz operator','V040__grant_navigation_to_vanz_operator.sql',"
                        + "'c8dfc30734258cd2cd1cca1c9370461ee892f4b98f0b482f5943dfd36d7840d9')",
                "-- BEGIN V043__oakved_b2c_navigation_tree.sql",
                "VALUES('043','oakved b2c navigation tree','V043__oakved_b2c_navigation_tree.sql',"
                        + "'c916ab1dbd9333d1588874696951cb25090530bd6c38c081a9ac14e5ac39b550')");
    }

    @Test
    void shouldExtendSharedNavigationModelForOakvedWithoutTenantIdCoupling() throws IOException {
        String sql = migrationSql(OAKVED_NAVIGATION_TREE_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "ADD COLUMN `navigation_template` varchar(32) NOT NULL DEFAULT 'VANZ_B2B'",
                "ADD COLUMN `target_key` varchar(64) DEFAULT NULL",
                "ADD COLUMN `style_variant` varchar(32) NOT NULL DEFAULT 'DEFAULT'",
                "SET config.`navigation_template` = 'OAKVED_B2C'",
                "LOWER(COALESCE(tenant.`code`, '')) = 'oakved'",
                "LOWER(config.`site_name`) LIKE '%oakved%'",
                "LOWER(config.`site_url`) LIKE '%oakved%'");
        assertThat(sql).doesNotContain("tenant.`id` = 121", "tenant_id = 121", "business_mode = 'B2C'");
    }

    @Test
    void shouldGrantWebsiteNavigationToVanzOperatorRole() throws IOException {
        String sql = migrationSql(WEBSITE_NAVIGATION_OPERATOR_PERMISSION_MIGRATION_RELATIVE_PATH);

        assertThat(sql).contains(
                "@website_navigation_operator_tenant_id = 162",
                "`code` = 'mall_operator'",
                "'seo:navigation:query'",
                "'seo:navigation:update'",
                "'seo:navigation:preview'",
                "'seo:navigation:publish'",
                "INSERT INTO `system_role_menu`",
                "CREATE TEMPORARY TABLE `website_navigation_operator_guard`",
                "CHECK (`valid` = 1)");
        assertThat(sql).doesNotContain("`code` = 'tenant_admin'");
    }

    @Test
    void shouldReplaceDeletedFlagUniqueIndexesWithActiveRecordMarkers() throws IOException {
        String sql = migrationSql(ACTIVE_RECORD_MIGRATION_RELATIVE_PATH);

        assertCoherentActiveRecordAlter(sql, "seo_site_config", "uk_tenant_site_deleted",
                "UNIQUE KEY `uk_tenant_site_active` (`tenant_id`, `site_id`, `active_record`)");
        assertCoherentActiveRecordAlter(sql, "seo_metadata", "uk_entity_locale_deleted",
                "UNIQUE KEY `uk_entity_locale_active` (`tenant_id`, `site_id`, `entity_type`, `entity_id`, `locale`, `active_record`)");
        assertThat(countMatches(sql, "ADD\\s+COLUMN\\s+`active_record`\\s+tinyint\\s+GENERATED\\s+ALWAYS"))
                .as("one generated active_record column per SEO table")
                .isEqualTo(2);
    }

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
        return migrationSql(MIGRATION_RELATIVE_PATH);
    }

    private static String migrationSql(Path relativePath) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path migration = directory.resolve(relativePath);
            if (Files.isRegularFile(migration)) {
                return Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
            directory = directory.getParent();
        }
        throw new IOException("Cannot find " + relativePath + " from " + Path.of("").toAbsolutePath());
    }

    private static String selectRow(String sql, int id) {
        Matcher matcher = Pattern.compile("(?m)^SELECT " + id + ",.*$").matcher(sql);
        assertThat(matcher.find()).as("menu SELECT for id %s", id).isTrue();
        return matcher.group();
    }

    private static void assertCoherentActiveRecordAlter(String sql, String tableName, String oldIndexName,
                                                         String replacementUniqueKey) {
        Matcher matcher = Pattern.compile("(?is)ALTER\\s+TABLE\\s+`" + Pattern.quote(tableName)
                + "`\\s+(.*?);").matcher(sql);
        assertThat(matcher.find()).as("ALTER TABLE for %s", tableName).isTrue();
        String operations = matcher.group(1);
        assertThat(matcher.find()).as("exactly one ALTER TABLE for %s", tableName).isFalse();
        assertThat(operations).contains(
                "DROP INDEX `" + oldIndexName + "`",
                "ADD COLUMN `active_record` tinyint GENERATED ALWAYS AS "
                        + "(CASE WHEN `deleted` = b'0' THEN 1 ELSE NULL END) STORED",
                replacementUniqueKey);
        assertThat(countMatches(operations, "ADD\\s+COLUMN\\s+`active_record`"))
                .as("generated active_record column for %s", tableName)
                .isEqualTo(1);
    }

    private static int countMatches(String value, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String normalizedSha256(String sql) {
        String normalized = sql.replace("\r\n", "\n").replace('\r', '\n')
                .replaceFirst("\\s+$", "") + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
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
