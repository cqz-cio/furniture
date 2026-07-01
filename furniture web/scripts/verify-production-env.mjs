import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REQUIRED_KEYS = [
  "VITE_YUDAO_APP_API_BASE",
  "VITE_YUDAO_APP_TENANT_ID",
  "VITE_YUDAO_US_DEFAULT_AREA_ID",
  "VITE_YUDAO_PAY_CHANNEL_CODE",
];

const DEFAULT_ENV_FILE = ".env.production";

const stripInlineComment = (value) => {
  let quote = "";
  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if ((character === '"' || character === "'") && value[index - 1] !== "\\") {
      quote = quote === character ? "" : quote || character;
    }
    if (character === "#" && !quote && /\s/.test(value[index - 1] || "")) {
      return value.slice(0, index).trim();
    }
  }
  return value.trim();
};

const unquote = (value) => {
  const trimmed = stripInlineComment(value);
  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1);
  }
  return trimmed;
};

export const parseEnvFileContent = (content) =>
  String(content || "")
    .split(/\r?\n/)
    .reduce((env, line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return env;
      const nextLine = trimmed.startsWith("export ") ? trimmed.slice("export ".length).trim() : trimmed;
      const separatorIndex = nextLine.indexOf("=");
      if (separatorIndex === -1) return env;
      const key = nextLine.slice(0, separatorIndex).trim();
      if (!key) return env;
      env[key] = unquote(nextLine.slice(separatorIndex + 1));
      return env;
    }, {});

const isPositiveInteger = (value) => /^[1-9]\d*$/.test(String(value || "").trim());

const isSafeProductionApiBase = (value) => {
  try {
    const url = new URL(value);
    return (
      ["http:", "https:"].includes(url.protocol) &&
      !["localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]"].includes(url.hostname)
    );
  } catch {
    return false;
  }
};

const isPath = (value) => !value || String(value).startsWith("/");

export const validateProductionEnv = (env) => {
  const errors = [];
  const warnings = [];

  for (const key of REQUIRED_KEYS) {
    if (!String(env[key] || "").trim()) {
      errors.push(`${key} is required.`);
    }
  }

  if (env.VITE_YUDAO_APP_API_BASE && !isSafeProductionApiBase(env.VITE_YUDAO_APP_API_BASE)) {
    errors.push("VITE_YUDAO_APP_API_BASE must be an absolute http(s) URL that is not localhost.");
  }

  if (env.VITE_YUDAO_US_DEFAULT_AREA_ID && !isPositiveInteger(env.VITE_YUDAO_US_DEFAULT_AREA_ID)) {
    errors.push("VITE_YUDAO_US_DEFAULT_AREA_ID must be a positive integer.");
  }

  if (!String(env.VITE_YUDAO_PAY_CHANNEL_CODE || "").trim()) {
    errors.push("VITE_YUDAO_PAY_CHANNEL_CODE is required before accepting production payment.");
  }

  if (String(env.VITE_SHOW_AUTH_TOKEN_PANEL || "").trim().toLowerCase() === "true") {
    errors.push("VITE_SHOW_AUTH_TOKEN_PANEL must not be true in production.");
  }

  if (!isPath(env.VITE_ADDRESS_VERIFICATION_PATH)) {
    errors.push("VITE_ADDRESS_VERIFICATION_PATH must be an app-api path beginning with /.");
  }

  if (!isPath(env.VITE_ADDRESS_VERIFICATION_STATUS_PATH)) {
    errors.push("VITE_ADDRESS_VERIFICATION_STATUS_PATH must be an app-api path beginning with /.");
  }

  if (env.VITE_YUDAO_API_BASE_URL || env.VITE_YUDAO_TENANT_ID) {
    warnings.push("Legacy VITE_YUDAO_API_BASE_URL/VITE_YUDAO_TENANT_ID are accepted by code but should not be used for launch.");
  }

  return {
    ok: errors.length === 0,
    errors,
    warnings,
  };
};

export const readAndValidateProductionEnv = (envFile = DEFAULT_ENV_FILE) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    return {
      ok: false,
      errors: [`Production env file not found: ${envPath}`],
      warnings: [],
    };
  }
  return validateProductionEnv(parseEnvFileContent(readFileSync(envPath, "utf8")));
};

const getEnvFileArg = (argv) => {
  const flagIndex = argv.findIndex((arg) => arg === "--env-file");
  if (flagIndex !== -1) return argv[flagIndex + 1] || DEFAULT_ENV_FILE;
  const inlineFlag = argv.find((arg) => arg.startsWith("--env-file="));
  if (inlineFlag) return inlineFlag.slice("--env-file=".length);
  return DEFAULT_ENV_FILE;
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const envFile = getEnvFileArg(process.argv.slice(2));
  const result = readAndValidateProductionEnv(envFile);
  if (result.ok) {
    console.log(`Production env check passed: ${envFile}`);
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
  } else {
    console.error(`Production env check failed: ${envFile}`);
    result.errors.forEach((error) => console.error(`error: ${error}`));
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
    process.exitCode = 1;
  }
}
