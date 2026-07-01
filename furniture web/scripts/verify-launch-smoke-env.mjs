import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.launch-smoke";

const REQUIRED_KEYS = [
  "YUDAO_SMOKE_BASE_URL",
  "YUDAO_SMOKE_TENANT_ID",
  "YUDAO_SMOKE_TOKEN",
  "YUDAO_ORDER_SMOKE_SKU_ID",
  "YUDAO_ORDER_SMOKE_CART_ID",
  "YUDAO_ORDER_SMOKE_ADDRESS_ID",
  "YUDAO_ORDER_SMOKE_COUNT",
  "YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE",
];

const OPTIONAL_URL_KEYS = ["YUDAO_ORDER_SMOKE_RETURN_ORIGIN", "YUDAO_ORDER_SMOKE_RETURN_URL"];

export const parseLaunchSmokeEnvArgs = (argv = []) => {
  const options = {
    envFile: DEFAULT_ENV_FILE,
    allowPlaceholders: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    } else if (arg === "--allow-placeholders") {
      options.allowPlaceholders = true;
    }
  }

  return options;
};

const isPositiveInteger = (value) => /^[1-9]\d*$/.test(String(value || "").trim());

const isHttpUrl = (value) => {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol);
  } catch {
    return false;
  }
};

const isPlaceholder = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  return !normalized || normalized.includes("<") || normalized.includes(">") || normalized.includes("replace-me");
};

export const validateLaunchSmokeEnv = (env, options = {}) => {
  const errors = [];
  const warnings = [];

  for (const key of REQUIRED_KEYS) {
    const value = String(env[key] || "").trim();
    if (!value) {
      errors.push(`${key} is required.`);
    } else if (!options.allowPlaceholders && isPlaceholder(value)) {
      errors.push(`${key} must be replaced with a real launch smoke value.`);
    }
  }

  if (env.YUDAO_SMOKE_BASE_URL && !isHttpUrl(env.YUDAO_SMOKE_BASE_URL)) {
    errors.push("YUDAO_SMOKE_BASE_URL must be an absolute http(s) URL.");
  }

  for (const key of [
    "YUDAO_SMOKE_TENANT_ID",
    "YUDAO_ORDER_SMOKE_SKU_ID",
    "YUDAO_ORDER_SMOKE_CART_ID",
    "YUDAO_ORDER_SMOKE_ADDRESS_ID",
    "YUDAO_ORDER_SMOKE_COUNT",
  ]) {
    if (env[key] && !options.allowPlaceholders && !isPositiveInteger(env[key])) {
      errors.push(`${key} must be a positive integer.`);
    }
  }

  for (const key of OPTIONAL_URL_KEYS) {
    if (env[key] && !isHttpUrl(env[key])) {
      errors.push(`${key} must be an absolute http(s) URL when provided.`);
    }
  }

  if (String(env.YUDAO_ORDER_SMOKE_CREATE_ORDER || "").toLowerCase() === "true") {
    warnings.push("YUDAO_ORDER_SMOKE_CREATE_ORDER=true will allow smoke checks to create a real order.");
  }

  return {
    ok: errors.length === 0,
    errors,
    warnings,
  };
};

export const readAndValidateLaunchSmokeEnv = (envFile = DEFAULT_ENV_FILE, options = {}) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    return {
      ok: false,
      errors: [`Launch smoke env file not found: ${envPath}`],
      warnings: [],
    };
  }

  return validateLaunchSmokeEnv(parseEnvFileContent(readFileSync(envPath, "utf8")), options);
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parseLaunchSmokeEnvArgs(process.argv.slice(2));
  const result = readAndValidateLaunchSmokeEnv(options.envFile, options);
  if (result.ok) {
    console.log(`Launch smoke env check passed: ${options.envFile}`);
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
  } else {
    console.error(`Launch smoke env check failed: ${options.envFile}`);
    result.errors.forEach((error) => console.error(`error: ${error}`));
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
    process.exitCode = 1;
  }
}
