import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const normalize = (value) => value.replace(/\r\n/g, "\n").replace(/\s+$/, "") + "\n";
const quote = (value) => `'${value.replaceAll("'", "''")}'`;

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
