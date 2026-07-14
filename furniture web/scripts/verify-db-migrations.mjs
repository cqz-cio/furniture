import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SQL_ROOT = resolve(WEB_ROOT, "../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const DOCKER_ROOT = resolve(SQL_ROOT, "../../script/docker");

export const buildMigrationChecks = () => {
  const migrationsRoot = resolve(SQL_ROOT, "migrations");
  return readdirSync(migrationsRoot)
    .filter((name) => /^V\d{3}__[a-z0-9_]+\.sql$/.test(name))
    .sort()
    .map((fileName, index) => ({
      name: fileName.replace(/\.sql$/, ""),
      fileName,
      version: index + 1,
      absolutePath: resolve(migrationsRoot, fileName),
      requiredTokens: [],
    }));
};

export const verifyDbMigrations = (checks = buildMigrationChecks()) => {
  const errors = [];
  const checked = [];

  checks.forEach((check, index) => {
    const expectedPrefix = `V${String(index + 1).padStart(3, "0")}__`;
    if (!check.fileName.startsWith(expectedPrefix)) {
      errors.push(`${check.name}: expected contiguous version ${expectedPrefix}`);
    }
    if (!existsSync(check.absolutePath)) {
      errors.push(`${check.name}: missing migration file at ${check.absolutePath}`);
      return;
    }
    const source = readFileSync(check.absolutePath, "utf8");
    const missingTokens = check.requiredTokens.filter((token) => !source.includes(token));
    if (missingTokens.length) {
      errors.push(`${check.name}: missing required token(s): ${missingTokens.join(", ")}`);
    }
    checked.push(check);
  });

  const infrastructureChecks = [
    [resolve(SQL_ROOT, "build-oakved-baseline.mjs"), ["schema_migrations", "checksum_sha256"]],
    [resolve(SQL_ROOT, "oakved-baseline.sql"), ["CREATE TABLE IF NOT EXISTS `schema_migrations`", "Oakved demo catalog"]],
    [resolve(SQL_ROOT, "oakved-demo-data.sql"), ["CALL seed_oakved_product", "expected 26 active demo products"]],
    [resolve(DOCKER_ROOT, "invoke-local-migrations.ps1"), ["GET_LOCK", "checksum_sha256"]],
    [resolve(DOCKER_ROOT, "reset-local-infra.ps1"), ["mysqldump", "RESET OAKVED LOCAL DATA", "down", "-v"]],
    [resolve(DOCKER_ROOT, "docker-compose-local-infra.yml"), ["oakved-baseline.sql:/docker-entrypoint-initdb.d/01-oakved-baseline.sql:ro"]],
    [resolve(DOCKER_ROOT, "start-local-infra.ps1"), ["invoke-local-migrations.ps1"]],
  ];

  for (const [absolutePath, requiredTokens] of infrastructureChecks) {
    if (!existsSync(absolutePath)) {
      errors.push(`missing infrastructure file at ${absolutePath}`);
      continue;
    }
    const source = readFileSync(absolutePath, "utf8");
    const missingTokens = requiredTokens.filter((token) => !source.includes(token));
    if (missingTokens.length) {
      errors.push(`${absolutePath}: missing required token(s): ${missingTokens.join(", ")}`);
    }
  }

  return { ok: errors.length === 0, errors, checked };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const result = verifyDbMigrations();
  if (result.ok) {
    console.log(`Database migration check passed: ${result.checked.length} numbered migration(s)`);
  } else {
    console.error("Database migration check failed:");
    result.errors.forEach((error) => console.error(`error: ${error}`));
    process.exitCode = 1;
  }
}
