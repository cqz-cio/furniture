import { fileURLToPath } from "node:url";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

import { parseEnvFileContent, readAndValidateProductionEnv } from "./verify-production-env.mjs";
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

const extractSeededAccount = (content = "") => {
  const match = String(content).match(/"seededAccount"\s*:\s*\{([\s\S]*?)\}/);
  if (!match) return null;
  const seeded = {};
  for (const [, key, value] of match[1].matchAll(/"([^"]+)"\s*:\s*"([^"]*)"/g)) {
    seeded[key] = value.trim();
  }
  return seeded;
};

const firstValue = (...values) => values.find((value) => String(value ?? "").trim());

const normalizeSeedValue = (value) => String(value ?? "").trim();
const normalizeSeedEmail = (value) => normalizeSeedValue(value).toLowerCase();
const normalizePathValue = (value) => resolve(process.cwd(), String(value || "").trim());
const normalizeUrlValue = (value) => String(value || "").trim().replace(/\/+$/, "");

const readLaunchManifest = (evidenceDir) => {
  const manifestPath = resolve(process.cwd(), evidenceDir, "launch-manifest.json");
  if (!existsSync(manifestPath)) {
    return { manifest: null, error: `launch-manifest.json is missing at ${manifestPath}` };
  }

  try {
    return { manifest: JSON.parse(readFileSync(manifestPath, "utf8")), error: "" };
  } catch (error) {
    return { manifest: null, error: `launch-manifest.json is not valid JSON: ${error.message}` };
  }
};

const validateLaunchManifestAlignment = ({ envFile, smokeEnvFile, backendEnvFile, baseUrl, evidenceDir }) => {
  const { manifest, error } = readLaunchManifest(evidenceDir);
  if (error) return { ok: false, errors: [error], warnings: [] };

  const errors = [];
  const pathMappings = [
    ["envFile", envFile, "--env-file"],
    ["smokeEnvFile", smokeEnvFile, "--smoke-env-file"],
    ["backendEnvFile", backendEnvFile, "--backend-env-file"],
  ];

  for (const [manifestKey, expectedValue, flagName] of pathMappings) {
    if (normalizePathValue(manifest[manifestKey]) !== normalizePathValue(expectedValue)) {
      errors.push(`launch-manifest.json ${manifestKey} must match ${flagName}`);
    }
  }

  if (normalizeUrlValue(manifest.baseUrl) !== normalizeUrlValue(baseUrl)) {
    errors.push("launch-manifest.json baseUrl must match --base-url");
  }

  return { ok: errors.length === 0, errors, warnings: [] };
};

const validateRealAccountSeedAlignment = ({ smokeEnvFile, evidenceDir }) => {
  const errors = [];
  const smokeEnvPath = resolve(process.cwd(), smokeEnvFile);
  const realAccountSmokePath = resolve(process.cwd(), evidenceDir, "real-account-smoke.txt");

  if (!existsSync(smokeEnvPath)) {
    return { ok: false, errors: [`Launch smoke env file not found: ${smokeEnvPath}`], warnings: [] };
  }
  if (!existsSync(realAccountSmokePath)) {
    return { ok: false, errors: [`real-account-smoke.txt is missing at ${realAccountSmokePath}`], warnings: [] };
  }

  const env = parseEnvFileContent(readFileSync(smokeEnvPath, "utf8"));
  const seededAccount = extractSeededAccount(readFileSync(realAccountSmokePath, "utf8"));
  if (!seededAccount) {
    return { ok: false, errors: ["real-account-smoke.txt must include seededAccount block"], warnings: [] };
  }
  if (String(env.YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER || "").trim().toLowerCase() !== "true") {
    errors.push("YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER must be true for final initial launch evidence");
  }

  const mappings = [
    ["userId", "YUDAO_REAL_ACCOUNT_SMOKE_USER_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_USER_ID],
    ["cartId", "YUDAO_REAL_ACCOUNT_SMOKE_CART_ID", firstValue(env.YUDAO_REAL_ACCOUNT_SMOKE_CART_ID, env.YUDAO_ORDER_SMOKE_CART_ID)],
    ["skuId", "YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID", firstValue(env.YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID, env.YUDAO_ORDER_SMOKE_SKU_ID)],
    ["addressId", "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID],
    ["orderId", "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID],
    ["giftRegistryPublicCode", "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE", env.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE],
    ["tradeId", "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID],
    ["tradeEmail", "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL", env.YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL],
    ["membershipStatus", "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS", env.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS],
    ["membershipPlanCode", "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE", env.YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE],
    ["giftRegistryItemSpuId", "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID],
    ["giftRegistryItemSkuId", "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID", env.YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID],
  ];

  for (const [seedKey, envKey, expected] of mappings) {
    const expectedValue = seedKey === "tradeEmail" ? normalizeSeedEmail(expected) : normalizeSeedValue(expected);
    const actualValue = seedKey === "tradeEmail" ? normalizeSeedEmail(seededAccount[seedKey]) : normalizeSeedValue(seededAccount[seedKey]);
    if (expectedValue && actualValue && actualValue !== expectedValue) {
      errors.push(`real-account-smoke.txt seeded ${seedKey} must match ${envKey}`);
    }
  }

  return { ok: errors.length === 0, errors, warnings: [] };
};

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
    const launchManifestAlignment = validateLaunchManifestAlignment({ envFile, smokeEnvFile, backendEnvFile, baseUrl, evidenceDir });
    checks.push(check("launch-manifest-alignment", launchManifestAlignment, "Launch manifest matches audited env files and base URL"));
    const realAccountSeedAlignment = validateRealAccountSeedAlignment({ smokeEnvFile, evidenceDir });
    checks.push(check("real-account-seed-alignment", realAccountSeedAlignment, "Real-account smoke evidence matches launch smoke env"));
  } else {
    checks.push({
      name: "launch-evidence",
      ok: false,
      details: ["Launch evidence directory is required. Pass --evidence-dir launch-evidence/<timestamp>."],
      warnings: [],
    });
    checks.push({
      name: "launch-manifest-alignment",
      ok: false,
      details: ["Launch evidence directory is required before checking launch manifest alignment."],
      warnings: [],
    });
    checks.push({
      name: "real-account-seed-alignment",
      ok: false,
      details: ["Launch evidence directory is required before checking real-account seed alignment."],
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
