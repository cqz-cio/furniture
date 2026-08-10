import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const WEB_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SQL_ROOT = resolve(WEB_ROOT, "../yudao电商管理平台前后端/yudao-cloud/sql/mysql");
const DOCKER_ROOT = resolve(SQL_ROOT, "../../script/docker");
const CLOUD_ROOT = resolve(SQL_ROOT, "../..");
const SERVER_ROOT = resolve(CLOUD_ROOT, "yudao-server");
const WORKFLOW_PATH = resolve(WEB_ROOT, "../.github/workflows/database-and-backend-ci.yml");

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
    [resolve(SQL_ROOT, "build-oakved-baseline.mjs"), ["flywayBaselineName", "extractBootstrapSections", "schema_migrations"]],
    [resolve(SQL_ROOT, "oakved-baseline.sql"), ["CREATE TABLE IF NOT EXISTS `schema_migrations`", "Oakved demo catalog"]],
    [resolve(SQL_ROOT, "oakved-demo-data.sql"), ["CALL seed_oakved_product", "expected 26 active demo products"]],
    [resolve(DOCKER_ROOT, "invoke-local-migrations.ps1"), ["standalone SQL migration runner has been retired", "Flyway"]],
    [resolve(DOCKER_ROOT, "reset-local-infra.ps1"), ["mysqldump", "RESET OAKVED LOCAL DATA", "down", "-v"]],
    [resolve(DOCKER_ROOT, "docker-compose-local-infra.yml"), ["yudao_mysql_data:/var/lib/mysql"]],
    [resolve(DOCKER_ROOT, "start-local-infra.ps1"), ["packaged Flyway migrations"]],
    [resolve(SERVER_ROOT, "pom.xml"), ["flyway-core", "flyway-mysql", "../sql/mysql/flyway", "db/migration"]],
    [resolve(SERVER_ROOT, "src/main/resources/application.yaml"), ["flyway:", "validate-on-migrate: true", "clean-disabled: true"]],
    [WORKFLOW_PATH, ["pull_request:", "branches: [main]", "OakvedFlywayIntegrationTest", "yudao-server.jar"]],
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

  const flywayRoot = resolve(SQL_ROOT, "flyway");
  const flywayBaselines = existsSync(flywayRoot)
    ? readdirSync(flywayRoot).filter((name) => /^B\d{3}__oakved_baseline\.sql$/.test(name)).sort()
    : [];
  const latestVersion = checks.at(-1)?.fileName.slice(1, 4);
  const latestBaselineName = flywayBaselines.at(-1);
  if (!latestBaselineName) {
    errors.push(`expected at least one Flyway baseline under ${flywayRoot}`);
  } else {
    const baselineVersion = latestBaselineName.slice(1, 4);
    if (!latestVersion || Number(baselineVersion) > Number(latestVersion)) {
      errors.push(`${latestBaselineName} is ahead of the numbered migration catalog`);
    }
    const legacyBaseline = readFileSync(resolve(SQL_ROOT, "oakved-baseline.sql"), "utf8").replace(/\r\n/g, "\n");
    const flywayBaseline = readFileSync(resolve(flywayRoot, latestBaselineName), "utf8").replace(/\r\n/g, "\n");
    const baselineMigrationVersions = [...flywayBaseline.matchAll(/^-- BEGIN V(\d{3})__/gm)]
      .map((match) => match[1]);
    if (baselineMigrationVersions.at(-1) !== baselineVersion) {
      errors.push(`${latestBaselineName} does not end at its declared V${baselineVersion} checkpoint`);
    }
    if (baselineVersion === latestVersion && flywayBaseline !== legacyBaseline) {
      errors.push(`${latestBaselineName} is not byte-equivalent to the generated compatibility baseline`);
    }
  }

  const composePath = resolve(DOCKER_ROOT, "docker-compose-local-infra.yml");
  if (existsSync(composePath)) {
    const compose = readFileSync(composePath, "utf8");
    if (/docker-entrypoint-initdb\.d|oakved-baseline\.sql/.test(compose)) {
      errors.push(`${composePath}: SQL bind mounts are forbidden; Flyway must initialize the database from the JAR`);
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
