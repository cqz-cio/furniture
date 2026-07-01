import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

import { readAndValidateProductionEnv } from "./verify-production-env.mjs";
import { readAndValidateLaunchSmokeEnv } from "./verify-launch-smoke-env.mjs";
import { readAndValidateBackendProductionEnv } from "./verify-backend-production-env.mjs";
import { readAndValidateLaunchEnvAlignment } from "./verify-launch-env-alignment.mjs";
import { verifyBackendProductionConfig } from "./verify-backend-production-config.mjs";
import { verifyDbMigrations } from "./verify-db-migrations.mjs";
import { auditLaunchEvidence } from "./audit-launch-evidence.mjs";

const DEFAULT_ENV_FILE = ".env.production";
const DEFAULT_SMOKE_ENV_FILE = ".env.launch-smoke";
const DEFAULT_BACKEND_ENV_FILE = ".env.backend-production";

export const parseInitialLaunchReadinessArgs = (argv = [], env = process.env) => {
  const options = {
    envFile: env.LAUNCH_ENV_FILE || DEFAULT_ENV_FILE,
    smokeEnvFile: env.LAUNCH_SMOKE_ENV_FILE || DEFAULT_SMOKE_ENV_FILE,
    backendEnvFile: env.LAUNCH_BACKEND_ENV_FILE || DEFAULT_BACKEND_ENV_FILE,
    baseUrl: env.LAUNCH_HEALTH_BASE_URL || "",
    evidenceDir: env.LAUNCH_EVIDENCE_DIR || "",
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    } else if (arg === "--smoke-env-file") {
      options.smokeEnvFile = argv[index + 1] || DEFAULT_SMOKE_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--smoke-env-file=")) {
      options.smokeEnvFile = arg.slice("--smoke-env-file=".length);
    } else if (arg === "--backend-env-file") {
      options.backendEnvFile = argv[index + 1] || DEFAULT_BACKEND_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--backend-env-file=")) {
      options.backendEnvFile = arg.slice("--backend-env-file=".length);
    } else if (arg === "--base-url") {
      options.baseUrl = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--base-url=")) {
      options.baseUrl = arg.slice("--base-url=".length);
    } else if (arg === "--evidence-dir") {
      options.evidenceDir = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--evidence-dir=")) {
      options.evidenceDir = arg.slice("--evidence-dir=".length);
    }
  }

  return options;
};

const check = (name, result, okMessage) => ({
  name,
  ok: result.ok,
  details: result.ok ? [okMessage] : result.errors,
  warnings: result.warnings || [],
});

export const auditInitialLaunchReadiness = (options = {}) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const smokeEnvFile = options.smokeEnvFile || DEFAULT_SMOKE_ENV_FILE;
  const backendEnvFile = options.backendEnvFile || DEFAULT_BACKEND_ENV_FILE;
  const baseUrl = options.baseUrl || "";
  const evidenceDir = options.evidenceDir || "";
  const checks = [];
  const blockers = [];

  const productionEnv = readAndValidateProductionEnv(envFile);
  checks.push(check("production-env", productionEnv, `Production env is valid: ${envFile}`));

  const launchSmokeEnv = readAndValidateLaunchSmokeEnv(smokeEnvFile);
  checks.push(check("launch-smoke-env", launchSmokeEnv, `Launch smoke env is valid: ${smokeEnvFile}`));

  const backendProductionEnv = readAndValidateBackendProductionEnv(backendEnvFile);
  checks.push(check("backend-production-env", backendProductionEnv, `Backend production env is valid: ${backendEnvFile}`));

  const launchEnvAlignment = readAndValidateLaunchEnvAlignment({ envFile, smokeEnvFile, backendEnvFile, baseUrl });
  checks.push(check("launch-env-alignment", launchEnvAlignment, "Launch env files are aligned"));

  const backendProductionConfig = verifyBackendProductionConfig();
  checks.push(check("backend-production-config", backendProductionConfig, "Backend production config is valid"));

  const dbMigrations = verifyDbMigrations();
  checks.push(check("db-migrations", dbMigrations, `DB migration files are present: ${dbMigrations.checked.length}`));

  if (evidenceDir) {
    const launchEvidence = auditLaunchEvidence({ dir: evidenceDir });
    checks.push(check("launch-evidence", launchEvidence, `Launch evidence is complete: ${launchEvidence.dir}`));
  } else {
    checks.push({
      name: "launch-evidence",
      ok: false,
      details: ["Launch evidence directory is required. Pass --evidence-dir launch-evidence/<timestamp>."],
      warnings: [],
    });
  }

  for (const item of checks) {
    if (!item.ok) blockers.push(...item.details);
  }

  return {
    ok: blockers.length === 0,
    blockers,
    checks,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parseInitialLaunchReadinessArgs(process.argv.slice(2));
  const result = auditInitialLaunchReadiness(options);
  if (result.ok) {
    console.log("Initial launch readiness audit passed.");
    result.checks.forEach((item) => console.log(`- ${item.name}: ok`));
  } else {
    console.error("Initial launch readiness audit failed:");
    result.checks.forEach((item) => {
      console.error(`- ${item.name}: ${item.ok ? "ok" : "blocked"}`);
      if (!item.ok) item.details.forEach((detail) => console.error(`  error: ${detail}`));
    });
    process.exitCode = 1;
  }
}
