import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.backend-production";

const REQUIRED_KEYS = [
  "SPRING_PROFILES_ACTIVE",
  "YUDAO_DB_URL",
  "YUDAO_DB_USERNAME",
  "YUDAO_DB_PASSWORD",
  "YUDAO_REDIS_HOST",
  "YUDAO_REDIS_PORT",
  "YUDAO_ADMIN_UI_URL",
  "YUDAO_APP_UI_URL",
  "YUDAO_PAY_ORDER_NOTIFY_URL",
  "YUDAO_PAY_REFUND_NOTIFY_URL",
  "YUDAO_PAY_TRANSFER_NOTIFY_URL",
  "YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY",
];

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]"]);

const valueOf = (env, key) => String(env[key] || "").trim();

const isPlaceholder = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  return (
    !normalized ||
    normalized.includes("<") ||
    normalized.includes(">") ||
    normalized.includes("replace-me")
  );
};

const isValidPort = (value) => {
  if (!/^\d+$/.test(String(value || "").trim())) return false;
  const port = Number(value);
  return port >= 1 && port <= 65535;
};

const isLocalish = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  if (!normalized) return false;
  if (LOCAL_HOSTS.has(normalized)) return true;
  return /(^|[/:@])(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[?::1\]?)(?=[:/?#]|$)/i.test(normalized);
};

const hostOf = (value) => {
  const normalized = String(value || "").trim();
  if (!normalized) return "";

  try {
    const url = new URL(normalized);
    if (url.hostname) return url.hostname.toLowerCase();
  } catch {
    // Some backend values, such as JDBC URLs, are not parseable as standard URLs.
  }

  const authority = normalized.match(/\/\/([^/?#]+)/)?.[1] || normalized;
  const hostWithPort = authority.split("@").pop() || "";
  if (hostWithPort.startsWith("[")) {
    return hostWithPort.slice(1, hostWithPort.indexOf("]")).toLowerCase();
  }
  return hostWithPort.split(/[/:?#]/)[0].toLowerCase();
};

const isDocumentationDomainHost = (value) => {
  const hostname = hostOf(value);
  return hostname === "example.com" || hostname.endsWith(".example.com") || hostname.endsWith(".example");
};

const isHttpsUrl = (value) => {
  try {
    const url = new URL(value);
    return url.protocol === "https:";
  } catch {
    return false;
  }
};

const isSafeHttpsUrl = (value) => {
  if (!isHttpsUrl(value)) return false;
  return !isLocalish(new URL(value).hostname);
};

const shouldSkipPlaceholderFormat = (env, key, options) =>
  options.allowPlaceholders && isPlaceholder(valueOf(env, key));

export const parseBackendProductionEnvArgs = (argv = []) => {
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

export const validateBackendProductionEnv = (env, options = {}) => {
  const errors = [];
  const warnings = [];

  for (const key of REQUIRED_KEYS) {
    const value = valueOf(env, key);
    if (!value) {
      errors.push(`${key} is required.`);
    } else if (!options.allowPlaceholders && isPlaceholder(value)) {
      errors.push(`${key} must be replaced with a real backend production value.`);
    }
  }

  if (valueOf(env, "SPRING_PROFILES_ACTIVE").toLowerCase() !== "prod") {
    errors.push("SPRING_PROFILES_ACTIVE must be prod.");
  }

  if (!shouldSkipPlaceholderFormat(env, "YUDAO_DB_URL", options) && isLocalish(valueOf(env, "YUDAO_DB_URL"))) {
    errors.push("YUDAO_DB_URL must not point to localhost.");
  }

  if (!options.allowPlaceholders && isDocumentationDomainHost(valueOf(env, "YUDAO_DB_URL"))) {
    errors.push("YUDAO_DB_URL must not use a documentation/example domain.");
  }

  if (valueOf(env, "YUDAO_DB_USERNAME").toLowerCase() === "root") {
    errors.push("YUDAO_DB_USERNAME must not be root.");
  }

  if (valueOf(env, "YUDAO_DB_PASSWORD") === "123456") {
    errors.push("YUDAO_DB_PASSWORD must not use the default development password.");
  }

  if (!shouldSkipPlaceholderFormat(env, "YUDAO_REDIS_HOST", options) && isLocalish(valueOf(env, "YUDAO_REDIS_HOST"))) {
    errors.push("YUDAO_REDIS_HOST must not point to localhost.");
  }

  if (!shouldSkipPlaceholderFormat(env, "YUDAO_REDIS_PORT", options) && !isValidPort(valueOf(env, "YUDAO_REDIS_PORT"))) {
    errors.push("YUDAO_REDIS_PORT must be a valid TCP port.");
  }

  if (!options.allowPlaceholders && isDocumentationDomainHost(valueOf(env, "YUDAO_REDIS_HOST"))) {
    errors.push("YUDAO_REDIS_HOST must not use a documentation/example domain.");
  }

  for (const key of ["YUDAO_ADMIN_UI_URL", "YUDAO_APP_UI_URL"]) {
    if (!shouldSkipPlaceholderFormat(env, key, options) && !isSafeHttpsUrl(valueOf(env, key))) {
      errors.push(`${key} must be an absolute https URL that is not localhost.`);
    }
    if (!options.allowPlaceholders && isDocumentationDomainHost(valueOf(env, key))) {
      errors.push(`${key} must not use a documentation/example domain.`);
    }
  }

  for (const key of ["YUDAO_PAY_ORDER_NOTIFY_URL", "YUDAO_PAY_REFUND_NOTIFY_URL", "YUDAO_PAY_TRANSFER_NOTIFY_URL"]) {
    if (!valueOf(env, key)) continue;
    if (!shouldSkipPlaceholderFormat(env, key, options) && !isHttpsUrl(valueOf(env, key))) {
      errors.push(`${key} must be an absolute https URL.`);
    }
    if (!options.allowPlaceholders && isDocumentationDomainHost(valueOf(env, key))) {
      errors.push(`${key} must not use a documentation/example domain.`);
    }
  }

  if (valueOf(env, "YUDAO_SECURITY_MOCK_ENABLE").toLowerCase() === "true") {
    errors.push("YUDAO_SECURITY_MOCK_ENABLE must not be true in production.");
  }

  return {
    ok: errors.length === 0,
    errors,
    warnings,
  };
};

export const readAndValidateBackendProductionEnv = (envFile = DEFAULT_ENV_FILE, options = {}) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    return {
      ok: false,
      errors: [`Backend production env file not found: ${envPath}`],
      warnings: [],
    };
  }

  return validateBackendProductionEnv(parseEnvFileContent(readFileSync(envPath, "utf8")), options);
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parseBackendProductionEnvArgs(process.argv.slice(2));
  const result = readAndValidateBackendProductionEnv(options.envFile, {
    allowPlaceholders: options.allowPlaceholders,
  });
  if (result.ok) {
    console.log(`Backend production env check passed: ${options.envFile}`);
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
  } else {
    console.error(`Backend production env check failed: ${options.envFile}`);
    result.errors.forEach((error) => console.error(`error: ${error}`));
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
    process.exitCode = 1;
  }
}
