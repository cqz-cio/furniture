import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const normalize = (value) => value.replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
const quote = (value) => `'${value.replaceAll("'", "''")}'`;

const baselineVanzBootstrap = normalize(`-- Baseline-only VANZ bootstrap.
-- Production upgrades keep V035's fail-closed account guard; a clean baseline
-- creates the canonical tenant and operator account before that guard runs.
SET @oakved_baseline_vanz_package_marker =
  _utf8mb4'oakved:baseline:vanz-b2b' COLLATE utf8mb4_unicode_ci;

INSERT INTO \`system_tenant_package\`
  (\`name\`, \`status\`, \`remark\`, \`menu_ids\`, \`creator\`, \`create_time\`,
   \`updater\`, \`update_time\`, \`deleted\`)
SELECT
  'Vanz B2B 初始化套餐', source_package.\`status\`,
  @oakved_baseline_vanz_package_marker, source_package.\`menu_ids\`,
  'baseline-vanz', CURRENT_TIMESTAMP, 'baseline-vanz', CURRENT_TIMESTAMP, b'0'
FROM \`system_tenant\` AS source_tenant
INNER JOIN \`system_tenant_package\` AS source_package
  ON source_package.\`id\` = source_tenant.\`package_id\`
 AND source_package.\`status\` = 0
 AND source_package.\`deleted\` = b'0'
WHERE source_tenant.\`id\` = 121
  AND source_tenant.\`status\` = 0
  AND source_tenant.\`deleted\` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM \`system_tenant_package\`
    WHERE \`remark\` = @oakved_baseline_vanz_package_marker
      AND \`deleted\` = b'0'
  );

SET @oakved_baseline_vanz_package_id = (
  SELECT MIN(\`id\`)
  FROM \`system_tenant_package\`
  WHERE \`remark\` = @oakved_baseline_vanz_package_marker
    AND \`status\` = 0
    AND \`deleted\` = b'0'
);

INSERT INTO \`system_tenant\`
  (\`id\`, \`name\`, \`code\`, \`contact_user_id\`, \`contact_name\`,
   \`contact_mobile\`, \`status\`, \`websites\`, \`business_mode\`,
   \`website_product_fields\`, \`package_id\`, \`expire_time\`,
   \`account_count\`, \`creator\`, \`create_time\`, \`updater\`,
   \`update_time\`, \`deleted\`)
SELECT
  162, 'Vanz家具', 'VANZ', NULL, 'vanz运营', '', 0, '', 'B2B',
  source_tenant.\`website_product_fields\`, @oakved_baseline_vanz_package_id,
  '2099-12-31 23:59:59', 20, 'baseline-vanz', CURRENT_TIMESTAMP,
  'baseline-vanz', CURRENT_TIMESTAMP, b'0'
FROM \`system_tenant\` AS source_tenant
WHERE source_tenant.\`id\` = 121
  AND source_tenant.\`deleted\` = b'0'
  AND @oakved_baseline_vanz_package_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM \`system_tenant\` WHERE \`id\` = 162 AND \`deleted\` = b'0'
  );

INSERT INTO \`system_users\`
  (\`username\`, \`password\`, \`nickname\`, \`remark\`, \`dept_id\`,
   \`post_ids\`, \`email\`, \`mobile\`, \`sex\`, \`avatar\`, \`status\`,
   \`login_ip\`, \`login_date\`, \`creator\`, \`create_time\`, \`updater\`,
   \`update_time\`, \`deleted\`, \`tenant_id\`)
SELECT
  'vanzadmin', source_user.\`password\`, 'vanz运营', 'VANZ B2B 本地基线账号',
  NULL, NULL, '', '', 0, NULL, 0, '', NULL, 'baseline-vanz',
  CURRENT_TIMESTAMP, 'baseline-vanz', CURRENT_TIMESTAMP, b'0', 162
FROM \`system_users\` AS source_user
WHERE source_user.\`username\` = 'admin'
  AND source_user.\`tenant_id\` = 1
  AND source_user.\`deleted\` = b'0'
  AND EXISTS (
    SELECT 1 FROM \`system_tenant\` WHERE \`id\` = 162 AND \`deleted\` = b'0'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM \`system_users\`
    WHERE \`username\` = 'vanzadmin' AND \`tenant_id\` = 162 AND \`deleted\` = b'0'
  )
ORDER BY source_user.\`id\`
LIMIT 1;

SET @oakved_baseline_vanz_user_id = (
  SELECT MIN(\`id\`)
  FROM \`system_users\`
  WHERE \`username\` = 'vanzadmin' AND \`tenant_id\` = 162 AND \`deleted\` = b'0'
);

UPDATE \`system_tenant\`
SET \`contact_user_id\` = @oakved_baseline_vanz_user_id,
    \`updater\` = 'baseline-vanz',
    \`update_time\` = CURRENT_TIMESTAMP
WHERE \`id\` = 162
  AND \`deleted\` = b'0';

INSERT INTO \`system_role\`
  (\`name\`, \`code\`, \`sort\`, \`data_scope\`, \`data_scope_dept_ids\`,
   \`status\`, \`type\`, \`remark\`, \`creator\`, \`create_time\`, \`updater\`,
   \`update_time\`, \`deleted\`, \`tenant_id\`)
SELECT
  '租户管理员', 'tenant_admin', 1, 1, '', 0, 1,
  'VANZ B2B 基线租户管理员', 'baseline-vanz', CURRENT_TIMESTAMP,
  'baseline-vanz', CURRENT_TIMESTAMP, b'0', 162
FROM \`system_tenant\`
WHERE \`id\` = 162
  AND \`deleted\` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM \`system_role\`
    WHERE \`tenant_id\` = 162 AND \`code\` = 'tenant_admin' AND \`deleted\` = b'0'
  );

SET @oakved_baseline_source_admin_role_id = (
  SELECT MIN(\`id\`) FROM \`system_role\`
  WHERE \`tenant_id\` = 121 AND \`code\` = 'tenant_admin' AND \`deleted\` = b'0'
);
SET @oakved_baseline_vanz_admin_role_id = (
  SELECT MIN(\`id\`) FROM \`system_role\`
  WHERE \`tenant_id\` = 162 AND \`code\` = 'tenant_admin' AND \`deleted\` = b'0'
);

INSERT INTO \`system_role_menu\`
  (\`role_id\`, \`menu_id\`, \`creator\`, \`create_time\`, \`updater\`,
   \`update_time\`, \`deleted\`, \`tenant_id\`)
SELECT
  @oakved_baseline_vanz_admin_role_id, source_menu.\`menu_id\`,
  'baseline-vanz', CURRENT_TIMESTAMP, 'baseline-vanz', CURRENT_TIMESTAMP, b'0', 162
FROM \`system_role_menu\` AS source_menu
WHERE source_menu.\`role_id\` = @oakved_baseline_source_admin_role_id
  AND source_menu.\`deleted\` = b'0'
  AND @oakved_baseline_vanz_admin_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM \`system_role_menu\` AS existing
    WHERE existing.\`role_id\` = @oakved_baseline_vanz_admin_role_id
      AND existing.\`menu_id\` = source_menu.\`menu_id\`
      AND existing.\`tenant_id\` = 162
      AND existing.\`deleted\` = b'0'
  );
`);

export const sanitizeBootstrapSql = (value) => normalize(value)
  .split("\n")
  .map((line) => {
    const isCloudFileConfig = line.startsWith("INSERT INTO `infra_file_config`") && line.includes('\\"accessKey\\"');
    const isSmsChannelConfig = line.startsWith("INSERT INTO `system_sms_channel`");
    const isMailAccountConfig = line.startsWith("INSERT INTO `system_mail_account`");
    if (isCloudFileConfig || isSmsChannelConfig || isMailAccountConfig) {
      return "-- Sensitive external-service seed omitted; configure credentials after installation.";
    }
    return line;
  })
  .join("\n");

export const discoverMigrations = (directory) => {
  if (!existsSync(directory)) throw new Error(`Migration directory does not exist: ${directory}`);
  const pattern = /^V(\d{3})__([a-z0-9_]+)\.sql$/;
  const migrations = readdirSync(directory)
    .filter((name) => name.endsWith(".sql"))
    .map((name) => {
      const match = name.match(pattern);
      if (!match) throw new Error(`Invalid migration file name: ${name}`);
      const source = normalize(readFileSync(join(directory, name), "utf8"));
      return {
        version: match[1],
        description: match[2].replaceAll("_", " "),
        scriptName: name,
        source,
        checksum: createHash("sha256").update(source).digest("hex"),
      };
    })
    .sort((left, right) => left.version.localeCompare(right.version));
  migrations.forEach((migration, index) => {
    const expected = String(index + 1).padStart(3, "0");
    if (migration.version !== expected) {
      throw new Error(`Migration sequence must be contiguous; expected V${expected}, found V${migration.version}`);
    }
  });
  return migrations;
};

export const adaptMigrationForBaseline = (migration) => {
  if (migration.version === "013") {
    const originalLockName = "CONCAT(DATABASE(), ':statistics-commerce-dashboard:v2')";
    const boundedLockName = "CONCAT('oakved:stats:', LEFT(SHA2(DATABASE(), 256), 40))";
    const occurrences = migration.source.split(originalLockName).length - 1;
    if (occurrences !== 2) {
      throw new Error(`V013 baseline lock compatibility expected 2 occurrences, found ${occurrences}`);
    }
    return migration.source.replaceAll(originalLockName, boundedLockName);
  }

  if (migration.version === "026") {
    let replacements = 0;
    const source = migration.source.replace(
      /INSERT INTO `system_menu` \(`id`,([^\n]+)\)\nSELECT (?:8110|8111|8112),/g,
      (_match, remainingColumns) => {
        replacements += 1;
        return `INSERT INTO \`system_menu\` (${remainingColumns})\nSELECT `;
      },
    );
    if (replacements !== 3) {
      throw new Error(`V026 baseline menu compatibility expected 3 fixed IDs, found ${replacements}`);
    }
    return source;
  }

  if (migration.version === "035") {
    const accountGuard = "WHERE `username` = 'vanzadmin'";
    const occurrences = migration.source.split(accountGuard).length - 1;
    if (occurrences !== 1) {
      throw new Error(`V035 baseline VANZ bootstrap expected 1 account guard, found ${occurrences}`);
    }
    return `${baselineVanzBootstrap}\n${migration.source}`;
  }

  return migration.source;
};

export const buildBaseline = ({ baseFiles, migrations, seedFile }) => {
  const missing = [...baseFiles, seedFile].filter((path) => !existsSync(path));
  if (missing.length) throw new Error(`Missing baseline source file(s): ${missing.join(", ")}`);
  const sections = [
    "-- GENERATED FILE. DO NOT EDIT. Run build-oakved-baseline.mjs.\n",
    "SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\n",
    ...baseFiles.map((path) => `\n-- BEGIN ${path.split(/[\\/]/).at(-1)}\n${sanitizeBootstrapSql(readFileSync(path, "utf8"))}`),
    ...migrations.map((migration) =>
      `\n-- BEGIN ${migration.scriptName}\n${adaptMigrationForBaseline(migration)}`,
    ),
    `\n-- BEGIN Oakved demo catalog\n${normalize(readFileSync(seedFile, "utf8"))}`,
    `\nCREATE TABLE IF NOT EXISTS \`schema_migrations\` (
  \`version\` varchar(16) NOT NULL,
  \`description\` varchar(255) NOT NULL,
  \`script_name\` varchar(255) NOT NULL,
  \`checksum_sha256\` char(64) NOT NULL,
  \`installed_at\` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (\`version\`),
  UNIQUE KEY \`uk_schema_migrations_script_name\` (\`script_name\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;\n`,
    ...migrations.map((migration) =>
      `INSERT INTO \`schema_migrations\`(version,description,script_name,checksum_sha256) VALUES(${quote(migration.version)},${quote(migration.description)},${quote(migration.scriptName)},${quote(migration.checksum)}) ON DUPLICATE KEY UPDATE checksum_sha256=VALUES(checksum_sha256);\n`,
    ),
    "SET FOREIGN_KEY_CHECKS = 1;\n",
  ];
  return sections.join("");
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isCli) {
  const root = dirname(fileURLToPath(import.meta.url));
  const migrations = discoverMigrations(join(root, "migrations"));
  const output = buildBaseline({
    baseFiles: [join(root, "ruoyi-vue-pro.sql"), join(root, "quartz.sql")],
    migrations,
    seedFile: join(root, "oakved-demo-data.sql"),
  });
  const outputPath = join(root, "oakved-baseline.sql");
  writeFileSync(outputPath, output, "utf8");
  console.log(`Generated ${outputPath} with ${migrations.length} migrations.`);
}
