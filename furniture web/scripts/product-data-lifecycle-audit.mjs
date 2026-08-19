import { spawnSync } from "node:child_process";
import { mkdirSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, extname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const storefrontRoot = resolve(dirname(scriptPath), "..");
const repositoryRoot = resolve(storefrontRoot, "..");
const defaultComposeFile = resolve(
  repositoryRoot,
  "yudao电商管理平台前后端/yudao-cloud/script/docker/docker-compose-local-infra.yml",
);
const migrationDirectory = resolve(
  repositoryRoot,
  "yudao电商管理平台前后端/yudao-cloud/sql/mysql/migrations",
);

const canonicalProductTypes = [
  "dining-chair",
  "bar-stool",
  "dining-table",
  "sofa",
  "coffee-table",
  "bookcase",
  "media-console",
  "bed",
  "nightstand",
  "dresser",
  "bench",
  "dressing-table",
  "wardrobe",
];
const canonicalProductTypeSql = canonicalProductTypes.map((value) => `'${value}'`).join(", ");

export const auditSections = [
  {
    key: "missing_p2_product_type",
    title: "SPUs missing a P2 Product type",
    requirement: "PRD 9.1: list SPUs whose category is missing or is not an active child category.",
    issueTypes: [
      "missing_category",
      "category_is_p1",
      "missing_p1_parent",
      "noncanonical_p2",
      "noncanonical_room",
    ],
    findingsSql: `
SELECT JSON_OBJECT(
  'spuId', p.id,
  'spuName', p.name,
  'categoryId', p.category_id,
  'categoryName', c.name,
  'categoryParentId', c.parent_id,
  'issueType', CASE
    WHEN c.id IS NULL THEN 'missing_category'
    WHEN c.parent_id = 0 THEN 'category_is_p1'
    WHEN parent.id IS NULL THEN 'missing_p1_parent'
    ELSE 'unknown'
  END
) AS record_json
FROM product_spu p
LEFT JOIN product_category c
  ON c.id = p.category_id
 AND c.tenant_id = p.tenant_id
 AND c.deleted = b'0'
LEFT JOIN product_category parent
  ON parent.id = c.parent_id
 AND parent.tenant_id = c.tenant_id
 AND parent.deleted = b'0'
WHERE p.tenant_id = {{TENANT_ID}}
  AND p.deleted = b'0'
  AND (c.id IS NULL OR c.parent_id = 0 OR parent.id IS NULL)`,
    findingsSqlWithCategoryCode: `
SELECT JSON_OBJECT(
  'spuId', p.id,
  'spuName', p.name,
  'categoryId', p.category_id,
  'categoryCode', c.code,
  'categoryName', c.name,
  'categoryParentId', c.parent_id,
  'roomCode', parent.code,
  'issueType', CASE
    WHEN c.id IS NULL THEN 'missing_category'
    WHEN c.parent_id = 0 THEN 'category_is_p1'
    WHEN parent.id IS NULL THEN 'missing_p1_parent'
    WHEN c.code NOT IN (${canonicalProductTypeSql}) THEN 'noncanonical_p2'
    WHEN parent.code NOT IN ('dining-room', 'living-room', 'bedroom') THEN 'noncanonical_room'
    ELSE 'unknown'
  END
) AS record_json
FROM product_spu p
LEFT JOIN product_category c
  ON c.id = p.category_id
 AND c.tenant_id = p.tenant_id
 AND c.deleted = b'0'
LEFT JOIN product_category parent
  ON parent.id = c.parent_id
 AND parent.tenant_id = c.tenant_id
 AND parent.deleted = b'0'
WHERE p.tenant_id = {{TENANT_ID}}
  AND p.deleted = b'0'
  AND (
    c.id IS NULL
    OR c.parent_id = 0
    OR parent.id IS NULL
    OR c.code NOT IN (${canonicalProductTypeSql})
    OR parent.code NOT IN ('dining-room', 'living-room', 'bedroom')
  )`,
  },
  {
    key: "unknown_product_type",
    title: "Legacy, ambiguous, or unknown detailConfig.productType values",
    requirement: "PRD 9.1/A-03: classify every non-canonical legacy Product type without guessing from names.",
    issueTypes: ["deterministic_legacy", "ambiguous_manual_review", "unknown_manual_review"],
    findingsSql: `
SELECT JSON_OBJECT(
  'spuId', typed.spu_id,
  'spuName', typed.spu_name,
  'categoryId', typed.category_id,
  'productType', typed.product_type,
  'issueType', CASE
    WHEN typed.product_type IN ('bed-bench', 'vanity', 'round-table', 'single-sofa') THEN 'deterministic_legacy'
    WHEN typed.product_type IN ('chair', 'sideboard') THEN 'ambiguous_manual_review'
    ELSE 'unknown_manual_review'
  END,
  'targetCode', CASE typed.product_type
    WHEN 'bed-bench' THEN 'bench'
    WHEN 'vanity' THEN 'dressing-table'
    WHEN 'round-table' THEN 'dining-table'
    WHEN 'single-sofa' THEN 'sofa'
    ELSE NULL
  END
) AS record_json
FROM (
  SELECT p.id AS spu_id,
         p.name AS spu_name,
         p.category_id,
         LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(p.detail_config, '$.productType')))) AS product_type
  FROM product_spu p
  WHERE p.tenant_id = {{TENANT_ID}}
    AND p.deleted = b'0'
) typed
WHERE typed.product_type IS NOT NULL
  AND typed.product_type NOT IN ('', 'null', ${canonicalProductTypeSql})`,
  },
  {
    key: "legacy_packing_shapes",
    title: "Legacy Packing JSON shapes",
    requirement: "PRD 9.1/B-01: list packingDisplay, object-shaped packing, and records containing both.",
    issueTypes: ["packing_display", "packing_object", "both_legacy_shapes"],
    findingsSql: `
SELECT JSON_OBJECT(
  'spuId', p.id,
  'spuName', p.name,
  'issueType', CASE
    WHEN JSON_CONTAINS_PATH(p.detail_config, 'one', '$.packingDisplay') = 1
     AND JSON_TYPE(JSON_EXTRACT(p.detail_config, '$.packing')) = 'OBJECT' THEN 'both_legacy_shapes'
    WHEN JSON_CONTAINS_PATH(p.detail_config, 'one', '$.packingDisplay') = 1 THEN 'packing_display'
    ELSE 'packing_object'
  END,
  'packingDisplay', JSON_UNQUOTE(JSON_EXTRACT(p.detail_config, '$.packingDisplay')),
  'packing', JSON_EXTRACT(p.detail_config, '$.packing')
) AS record_json
FROM product_spu p
WHERE p.tenant_id = {{TENANT_ID}}
  AND p.deleted = b'0'
  AND p.detail_config IS NOT NULL
  AND (
    JSON_CONTAINS_PATH(p.detail_config, 'one', '$.packingDisplay') = 1
    OR JSON_TYPE(JSON_EXTRACT(p.detail_config, '$.packing')) = 'OBJECT'
  )`,
  },
  {
    key: "unverified_default_finish",
    title: "Unverified Natural Oak Finish values",
    requirement: "PRD 9.1/B-02: list Finish values matching the former automatic default when provenance is unavailable.",
    issueTypes: ["unverified_default_finish"],
    findingsSql: `
SELECT JSON_OBJECT(
  'issueType', 'unverified_default_finish',
  'spuId', p.id,
  'spuName', p.name,
  'finish', JSON_UNQUOTE(JSON_EXTRACT(p.detail_config, '$.finish')),
  'creator', p.creator,
  'updater', p.updater,
  'lastUpdatedAt', DATE_FORMAT(p.update_time, '%Y-%m-%dT%H:%i:%s')
) AS record_json
FROM product_spu p
WHERE p.tenant_id = {{TENANT_ID}}
  AND p.deleted = b'0'
  AND LOWER(TRIM(JSON_UNQUOTE(JSON_EXTRACT(p.detail_config, '$.finish')))) = 'natural oak'`,
  },
  {
    key: "erp_mapping_integrity",
    title: "ERP mapping integrity risks",
    requirement: "PRD 9.1/C: list unmapped SKUs, duplicate mappings, orphan mappings, and mappings to deleted ERP products.",
    issueTypes: [
      "unmapped_sku",
      "duplicate_mall_sku_mapping",
      "duplicate_erp_product_mapping",
      "orphan_mapping_missing_sku",
      "orphan_mapping_deleted_sku",
      "orphan_mapping_missing_spu",
      "orphan_mapping_deleted_spu",
      "orphan_mapping_spu_mismatch",
      "orphan_mapping_unknown",
      "mapping_missing_erp_product",
      "mapping_deleted_erp_product",
    ],
    findingsSql: `
SELECT JSON_OBJECT(
  'issueType', 'unmapped_sku',
  'spuId', p.id,
  'spuName', p.name,
  'skuId', s.id
) AS record_json
FROM product_sku s
JOIN product_spu p
  ON p.id = s.spu_id
 AND p.tenant_id = s.tenant_id
 AND p.deleted = b'0'
LEFT JOIN mall_erp_product_mapping m
  ON m.mall_sku_id = s.id
 AND m.tenant_id = s.tenant_id
 AND m.deleted = b'0'
WHERE s.tenant_id = {{TENANT_ID}}
  AND s.deleted = b'0'
  AND m.id IS NULL
UNION ALL
SELECT JSON_OBJECT(
  'issueType', 'duplicate_mall_sku_mapping',
  'skuId', m.mall_sku_id,
  'mappingCount', COUNT(*),
  'mappingIds', JSON_ARRAYAGG(m.id)
) AS record_json
FROM mall_erp_product_mapping m
WHERE m.tenant_id = {{TENANT_ID}}
  AND m.deleted = b'0'
GROUP BY m.mall_sku_id
HAVING COUNT(*) > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', 'duplicate_erp_product_mapping',
  'erpProductId', m.erp_product_id,
  'mappingCount', COUNT(*),
  'mappingIds', JSON_ARRAYAGG(m.id)
) AS record_json
FROM mall_erp_product_mapping m
WHERE m.tenant_id = {{TENANT_ID}}
  AND m.deleted = b'0'
GROUP BY m.erp_product_id
HAVING COUNT(*) > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE
    WHEN s.id IS NULL THEN 'orphan_mapping_missing_sku'
    WHEN s.deleted = b'1' THEN 'orphan_mapping_deleted_sku'
    WHEN p.id IS NULL THEN 'orphan_mapping_missing_spu'
    WHEN p.deleted = b'1' THEN 'orphan_mapping_deleted_spu'
    WHEN s.spu_id <> m.mall_spu_id THEN 'orphan_mapping_spu_mismatch'
    ELSE 'orphan_mapping_unknown'
  END,
  'mappingId', m.id,
  'mappingSpuId', m.mall_spu_id,
  'skuId', m.mall_sku_id,
  'actualSkuSpuId', s.spu_id,
  'erpProductId', m.erp_product_id
) AS record_json
FROM mall_erp_product_mapping m
LEFT JOIN product_sku s
  ON s.id = m.mall_sku_id
 AND s.tenant_id = m.tenant_id
LEFT JOIN product_spu p
  ON p.id = m.mall_spu_id
 AND p.tenant_id = m.tenant_id
WHERE m.tenant_id = {{TENANT_ID}}
  AND m.deleted = b'0'
  AND (s.id IS NULL OR s.deleted = b'1' OR p.id IS NULL OR p.deleted = b'1' OR s.spu_id <> m.mall_spu_id)
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE
    WHEN e.id IS NULL THEN 'mapping_missing_erp_product'
    ELSE 'mapping_deleted_erp_product'
  END,
  'mappingId', m.id,
  'skuId', m.mall_sku_id,
  'erpProductId', m.erp_product_id,
  'erpProductCode', m.erp_product_code
) AS record_json
FROM mall_erp_product_mapping m
LEFT JOIN erp_product e
  ON e.id = m.erp_product_id
 AND e.tenant_id = m.tenant_id
WHERE m.tenant_id = {{TENANT_ID}}
  AND m.deleted = b'0'
  AND (e.id IS NULL OR e.deleted = b'1')`,
  },
  {
    key: "furniture_projection_integrity",
    title: "Furniture search projection integrity risks",
    requirement: "PRD 9.1/E: list missing SKU projections and orphan active projections.",
    issueTypes: [
      "missing_projection",
      "orphan_projection_missing_sku",
      "orphan_projection_deleted_sku",
      "orphan_projection_missing_spu",
      "orphan_projection_deleted_spu",
      "orphan_projection_spu_mismatch",
      "orphan_projection_unknown",
    ],
    findingsSql: `
SELECT JSON_OBJECT(
  'issueType', 'missing_projection',
  'spuId', p.id,
  'spuName', p.name,
  'skuId', s.id
) AS record_json
FROM product_sku s
JOIN product_spu p
  ON p.id = s.spu_id
 AND p.tenant_id = s.tenant_id
 AND p.deleted = b'0'
LEFT JOIN product_furniture_sku_search f
  ON f.sku_id = s.id
 AND f.tenant_id = s.tenant_id
 AND f.deleted = b'0'
WHERE s.tenant_id = {{TENANT_ID}}
  AND s.deleted = b'0'
  AND f.id IS NULL
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE
    WHEN s.id IS NULL THEN 'orphan_projection_missing_sku'
    WHEN s.deleted = b'1' THEN 'orphan_projection_deleted_sku'
    WHEN p.id IS NULL THEN 'orphan_projection_missing_spu'
    WHEN p.deleted = b'1' THEN 'orphan_projection_deleted_spu'
    WHEN s.spu_id <> f.spu_id THEN 'orphan_projection_spu_mismatch'
    ELSE 'orphan_projection_unknown'
  END,
  'projectionId', f.id,
  'projectionSpuId', f.spu_id,
  'skuId', f.sku_id,
  'actualSkuSpuId', s.spu_id
) AS record_json
FROM product_furniture_sku_search f
LEFT JOIN product_sku s
  ON s.id = f.sku_id
 AND s.tenant_id = f.tenant_id
LEFT JOIN product_spu p
  ON p.id = f.spu_id
 AND p.tenant_id = f.tenant_id
WHERE f.tenant_id = {{TENANT_ID}}
  AND f.deleted = b'0'
  AND (s.id IS NULL OR s.deleted = b'1' OR p.id IS NULL OR p.deleted = b'1' OR s.spu_id <> f.spu_id)`,
  },
  {
    key: "orphan_seo_records",
    title: "SEO records referencing missing products or categories",
    requirement: "PRD 9.1/F: list active SEO metadata whose PRODUCT or CATEGORY source entity does not exist.",
    issueTypes: ["missing_product", "missing_category"],
    findingsSql: `
SELECT JSON_OBJECT(
  'issueType', 'missing_product',
  'seoMetadataId', seo.id,
  'siteId', seo.site_id,
  'entityType', seo.entity_type,
  'entityId', seo.entity_id,
  'locale', seo.locale,
  'publishStatus', seo.publish_status
) AS record_json
FROM seo_metadata seo
LEFT JOIN product_spu p
  ON p.id = seo.entity_id
 AND p.tenant_id = seo.tenant_id
 AND p.deleted = b'0'
WHERE seo.tenant_id = {{TENANT_ID}}
  AND seo.deleted = b'0'
  AND UPPER(seo.entity_type) = 'PRODUCT'
  AND p.id IS NULL
UNION ALL
SELECT JSON_OBJECT(
  'issueType', 'missing_category',
  'seoMetadataId', seo.id,
  'siteId', seo.site_id,
  'entityType', seo.entity_type,
  'entityId', seo.entity_id,
  'locale', seo.locale,
  'publishStatus', seo.publish_status
) AS record_json
FROM seo_metadata seo
LEFT JOIN product_category c
  ON c.id = seo.entity_id
 AND c.tenant_id = seo.tenant_id
 AND c.deleted = b'0'
WHERE seo.tenant_id = {{TENANT_ID}}
  AND seo.deleted = b'0'
  AND UPPER(seo.entity_type) = 'CATEGORY'
  AND c.id IS NULL`,
  },
  {
    key: "erp_soft_delete_unique_key_risks",
    title: "ERP soft-delete unique-key lifecycle risks",
    requirement: "PRD 9.1/G-03: expose legacy (business key, deleted) indexes and data that can collide on another delete/recreate cycle.",
    issueTypes: ["legacy_unique_index", "active_and_deleted_history", "deleted_history"],
    findingsSql: `
SELECT JSON_OBJECT(
  'issueType', 'legacy_unique_index',
  'tableName', s.table_name,
  'businessKey', s.index_name,
  'activeCount', NULL,
  'deletedCount', NULL
) AS record_json
FROM information_schema.statistics s
WHERE s.table_schema = DATABASE()
  AND s.table_name IN ('erp_product_unit', 'erp_product_category', 'erp_product', 'erp_warehouse', 'erp_stock')
  AND s.non_unique = 0
GROUP BY s.table_name, s.index_name
HAVING SUM(s.column_name = 'deleted') > 0
   AND SUM(s.column_name = 'active_record') = 0
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE WHEN SUM(u.deleted = b'0') > 0 THEN 'active_and_deleted_history' ELSE 'deleted_history' END,
  'tableName', 'erp_product_unit',
  'businessKey', u.name,
  'activeCount', SUM(u.deleted = b'0'),
  'deletedCount', SUM(u.deleted = b'1')
) AS record_json
FROM erp_product_unit u
WHERE u.tenant_id = {{TENANT_ID}}
GROUP BY u.name
HAVING SUM(u.deleted = b'1') > 0 OR SUM(u.deleted = b'0') > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE WHEN SUM(c.deleted = b'0') > 0 THEN 'active_and_deleted_history' ELSE 'deleted_history' END,
  'tableName', 'erp_product_category',
  'businessKey', c.code,
  'activeCount', SUM(c.deleted = b'0'),
  'deletedCount', SUM(c.deleted = b'1')
) AS record_json
FROM erp_product_category c
WHERE c.tenant_id = {{TENANT_ID}}
GROUP BY c.code
HAVING SUM(c.deleted = b'1') > 0 OR SUM(c.deleted = b'0') > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE WHEN SUM(e.deleted = b'0') > 0 THEN 'active_and_deleted_history' ELSE 'deleted_history' END,
  'tableName', 'erp_product',
  'businessKey', e.bar_code,
  'activeCount', SUM(e.deleted = b'0'),
  'deletedCount', SUM(e.deleted = b'1')
) AS record_json
FROM erp_product e
WHERE e.tenant_id = {{TENANT_ID}}
GROUP BY e.bar_code
HAVING SUM(e.deleted = b'1') > 0 OR SUM(e.deleted = b'0') > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE WHEN SUM(w.deleted = b'0') > 0 THEN 'active_and_deleted_history' ELSE 'deleted_history' END,
  'tableName', 'erp_warehouse',
  'businessKey', w.name,
  'activeCount', SUM(w.deleted = b'0'),
  'deletedCount', SUM(w.deleted = b'1')
) AS record_json
FROM erp_warehouse w
WHERE w.tenant_id = {{TENANT_ID}}
GROUP BY w.name
HAVING SUM(w.deleted = b'1') > 0 OR SUM(w.deleted = b'0') > 1
UNION ALL
SELECT JSON_OBJECT(
  'issueType', CASE WHEN SUM(st.deleted = b'0') > 0 THEN 'active_and_deleted_history' ELSE 'deleted_history' END,
  'tableName', 'erp_stock',
  'businessKey', CONCAT(st.product_id, ':', st.warehouse_id),
  'activeCount', SUM(st.deleted = b'0'),
  'deletedCount', SUM(st.deleted = b'1')
) AS record_json
FROM erp_stock st
WHERE st.tenant_id = {{TENANT_ID}}
GROUP BY st.product_id, st.warehouse_id
HAVING SUM(st.deleted = b'1') > 0 OR SUM(st.deleted = b'0') > 1`,
  },
];

const stripSqlLiteralsAndComments = (sql) => sql
  .replace(/\/\*[\s\S]*?\*\//g, " ")
  .replace(/--[^\r\n]*/g, " ")
  .replace(/'(?:''|\\.|[^'])*'/g, "''")
  .replace(/"(?:""|\\.|[^"])*"/g, '""')
  .replace(/`(?:``|[^`])*`/g, "``");

export const assertReadOnlySql = (sql) => {
  if (typeof sql !== "string" || !sql.trim()) {
    throw new Error("Audit SQL must be a non-empty read-only SELECT.");
  }
  const normalized = stripSqlLiteralsAndComments(sql).trim();
  if (!/^(SELECT|WITH)\b/i.test(normalized)) {
    throw new Error("Audit SQL is not read-only: only SELECT or WITH statements are allowed.");
  }
  if (/\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|TRUNCATE|CREATE|GRANT|REVOKE|CALL|LOAD|LOCK|UNLOCK|INTO|OUTFILE|DUMPFILE|SET)\b/i.test(normalized)) {
    throw new Error("Audit SQL is not read-only: a mutation or side-effect statement was detected.");
  }
  if (normalized.includes(";")) {
    throw new Error("Audit SQL is not read-only: multiple statements are not allowed.");
  }
  return sql;
};

const parsePositiveInteger = (raw, label, maximum = Number.MAX_SAFE_INTEGER) => {
  if (!/^\d+$/.test(String(raw ?? ""))) {
    throw new Error(`${label} must be a positive integer.`);
  }
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < 1 || value > maximum) {
    throw new Error(`${label} must be between 1 and ${maximum}.`);
  }
  return value;
};

export const parseAuditArgs = (argv = process.argv.slice(2)) => {
  const options = {
    tenantId: 121,
    limit: 200,
    outputPath: null,
    composeFile: defaultComposeFile,
    database: "ruoyi-vue-pro",
    mysqlService: "mysql",
    timeoutMs: 30_000,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const value = argv[index + 1];
    if (argument === "--tenant") {
      options.tenantId = parsePositiveInteger(value, "tenant");
    } else if (argument === "--limit") {
      options.limit = parsePositiveInteger(value, "limit", 10_000);
    } else if (argument === "--output") {
      if (!value) throw new Error("output requires a path.");
      options.outputPath = value;
    } else if (argument === "--compose-file") {
      if (!value) throw new Error("compose-file requires a path.");
      options.composeFile = value;
    } else if (argument === "--database") {
      if (!/^[a-zA-Z0-9_-]+$/.test(value || "")) throw new Error("database contains unsafe characters.");
      options.database = value;
    } else if (argument === "--mysql-service") {
      if (!/^[a-zA-Z0-9_-]+$/.test(value || "")) throw new Error("mysql-service contains unsafe characters.");
      options.mysqlService = value;
    } else if (argument === "--timeout-ms") {
      options.timeoutMs = parsePositiveInteger(value, "timeout-ms", 120_000);
    } else {
      throw new Error(`Unknown audit argument: ${argument}`);
    }
    index += 1;
  }
  return options;
};

export const buildAuditQueries = (section, { tenantId, limit }) => {
  const safeTenantId = parsePositiveInteger(tenantId, "tenant");
  const safeLimit = parsePositiveInteger(limit, "limit", 10_000);
  assertReadOnlySql(section.findingsSql);
  const findingsSql = section.findingsSql.replaceAll("{{TENANT_ID}}", String(safeTenantId)).trim();
  const countSql = `SELECT COUNT(*) FROM (\n${findingsSql}\n) audit_findings`;
  const breakdownSql = `SELECT\n  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(record_json, '$.issueType')), 'unspecified') AS issue_type,\n  COUNT(*) AS issue_count\nFROM (\n${findingsSql}\n) audit_findings\nGROUP BY issue_type`;
  const detailSql = `SELECT record_json FROM (\n${findingsSql}\n) audit_findings LIMIT ${safeLimit}`;
  assertReadOnlySql(countSql);
  assertReadOnlySql(breakdownSql);
  assertReadOnlySql(detailSql);
  return { countSql, breakdownSql, detailSql };
};

const escapeMarkdown = (value) => String(value ?? "")
  .replaceAll("|", "\\|")
  .replace(/[\r\n]+/g, " ");

export const renderAuditMarkdown = (report) => {
  const lines = [
    "# Product data lifecycle Phase 0 audit",
    "",
    `- Generated at: ${escapeMarkdown(report.generatedAt)}`,
    `- Tenant: \`${escapeMarkdown(report.tenantId)}\``,
    `- Database: \`${escapeMarkdown(report.source.database)}\``,
    `- Repository migration: \`${escapeMarkdown(report.source.repositoryMigration || "unknown")}\``,
    `- Database migration: \`${escapeMarkdown(report.source.databaseMigration || "unknown")}\``,
    "- Mode: read-only; no business data was modified.",
    "",
    "## Summary",
    "",
    "| Risk key | Findings | Returned records |",
    "|---|---:|---:|",
    ...report.sections.map((section) =>
      `| \`${escapeMarkdown(section.key)}\` | ${section.totalCount} | ${section.records.length}${section.truncated ? " (truncated)" : ""} |`),
    "",
  ];

  for (const section of report.sections) {
    lines.push(
      `## ${section.title}`,
      "",
      `- Key: \`${section.key}\``,
      `- Requirement: ${section.requirement}`,
      `- Findings: ${section.totalCount}`,
    );
    if (section.truncated) {
      lines.push(`- Records truncated to ${section.records.length}; the count above is complete.`);
    }
    lines.push("", "### Finding breakdown", "", "| Issue type | Count |", "|---|---:|");
    for (const [issueType, count] of Object.entries(section.breakdown || {})) {
      lines.push(`| \`${escapeMarkdown(issueType)}\` | ${count} |`);
    }
    lines.push("", "### Records", "", "```json", JSON.stringify(section.records, null, 2), "```", "");
  }
  return `${lines.join("\n")}\n`;
};

const discoverRepositoryMigration = () => {
  const migrations = readdirSync(migrationDirectory)
    .map((fileName) => {
      const match = /^V(\d+)__.+\.sql$/.exec(fileName);
      return match ? { fileName, version: Number(match[1]) } : null;
    })
    .filter(Boolean)
    .sort((left, right) => left.version - right.version);
  return migrations.at(-1)?.fileName || null;
};

const createDockerMysqlRunner = (options) => {
  const composeFile = isAbsolute(options.composeFile)
    ? options.composeFile
    : resolve(process.cwd(), options.composeFile);
  const password = process.env.OAKVED_DB_ROOT_PASSWORD || "123456";

  return {
    query(sql) {
      assertReadOnlySql(sql);
      const result = spawnSync(
        "docker",
        [
          "compose",
          "-f",
          composeFile,
          "exec",
          "-T",
          "-e",
          `MYSQL_PWD=${password}`,
          options.mysqlService,
          "mysql",
          "--default-character-set=utf8mb4",
          "--batch",
          "--raw",
          "--skip-column-names",
          "-uroot",
          options.database,
          "-e",
          sql,
        ],
        {
          cwd: repositoryRoot,
          encoding: "utf8",
          timeout: options.timeoutMs,
          maxBuffer: 32 * 1024 * 1024,
          windowsHide: true,
        },
      );
      if (result.error) {
        throw new Error(`MySQL audit command failed: ${result.error.message}`);
      }
      if (result.status !== 0) {
        throw new Error(`MySQL audit query failed (${result.status}): ${(result.stderr || result.stdout).trim()}`);
      }
      return result.stdout.trim();
    },
  };
};

const discoverDatabaseMigration = (runner) => {
  const flywayExists = Number(runner.query(`
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history'`)) > 0;
  if (flywayExists) {
    return runner.query(`
SELECT script
FROM flyway_schema_history
WHERE success = 1
ORDER BY installed_rank DESC
LIMIT 1`) || null;
  }

  const legacyLedgerExists = Number(runner.query(`
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'schema_migrations'`)) > 0;
  if (!legacyLedgerExists) return null;
  return runner.query(`
SELECT script_name
FROM schema_migrations
ORDER BY CAST(SUBSTRING(version, 2) AS UNSIGNED) DESC
LIMIT 1`) || null;
};

const parseJsonLines = (stdout, sectionKey) => {
  if (!stdout) return [];
  return stdout.split(/\r?\n/).filter(Boolean).map((line) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      throw new Error(`Invalid JSON row returned for ${sectionKey}: ${error.message}`);
    }
  });
};

const parseBreakdown = (stdout, section) => {
  const breakdown = Object.fromEntries(section.issueTypes.map((issueType) => [issueType, 0]));
  if (!stdout) return breakdown;
  for (const line of stdout.split(/\r?\n/).filter(Boolean)) {
    const separator = line.lastIndexOf("\t");
    if (separator < 1) {
      throw new Error(`Invalid breakdown row returned for ${section.key}: ${line}`);
    }
    const issueType = line.slice(0, separator);
    const count = Number(line.slice(separator + 1));
    if (!Number.isSafeInteger(count) || count < 0) {
      throw new Error(`Invalid breakdown count returned for ${section.key}/${issueType}: ${count}`);
    }
    breakdown[issueType] = count;
  }
  return breakdown;
};

export const runAudit = (options) => {
  const runner = createDockerMysqlRunner(options);
  const categoryCodeAvailable = Number(runner.query(`
SELECT COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'product_category'
  AND column_name = 'code'`)) > 0;
  const sections = auditSections.map((section) => {
    const effectiveSection = categoryCodeAvailable && section.findingsSqlWithCategoryCode
      ? { ...section, findingsSql: section.findingsSqlWithCategoryCode }
      : section;
    const { countSql, breakdownSql, detailSql } = buildAuditQueries(effectiveSection, options);
    const totalCount = Number(runner.query(countSql));
    if (!Number.isSafeInteger(totalCount) || totalCount < 0) {
      throw new Error(`Invalid count returned for ${section.key}: ${totalCount}`);
    }
    const breakdown = parseBreakdown(runner.query(breakdownSql), section);
    const records = parseJsonLines(runner.query(detailSql), section.key);
    return {
      key: section.key,
      title: section.title,
      requirement: section.requirement,
      totalCount,
      breakdown,
      records,
      truncated: totalCount > records.length,
    };
  });

  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    tenantId: options.tenantId,
    source: {
      database: options.database,
      repositoryMigration: discoverRepositoryMigration(),
      databaseMigration: discoverDatabaseMigration(runner),
    },
    sections,
  };
};

const writeReport = (report, outputPath) => {
  const absoluteMarkdownPath = isAbsolute(outputPath)
    ? outputPath
    : resolve(process.cwd(), outputPath);
  const jsonPath = extname(absoluteMarkdownPath).toLowerCase() === ".md"
    ? `${absoluteMarkdownPath.slice(0, -3)}.json`
    : `${absoluteMarkdownPath}.json`;
  mkdirSync(dirname(absoluteMarkdownPath), { recursive: true });
  writeFileSync(absoluteMarkdownPath, renderAuditMarkdown(report), "utf8");
  writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  return { markdownPath: absoluteMarkdownPath, jsonPath };
};

const main = () => {
  const options = parseAuditArgs();
  const defaultName = `${new Date().toISOString().slice(0, 10)}-product-data-lifecycle-phase-0.md`;
  const outputPath = options.outputPath || resolve(repositoryRoot, "docs/audits", defaultName);
  const report = runAudit(options);
  const paths = writeReport(report, outputPath);
  for (const section of report.sections) {
    console.log(`audit ${section.key}=${section.totalCount}`);
  }
  console.log(`audit repository_migration=${report.source.repositoryMigration || "unknown"}`);
  console.log(`audit database_migration=${report.source.databaseMigration || "unknown"}`);
  console.log(`audit markdown=${paths.markdownPath}`);
  console.log(`audit json=${paths.jsonPath}`);
};

if (process.argv[1] && resolve(process.argv[1]) === scriptPath) {
  try {
    main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
