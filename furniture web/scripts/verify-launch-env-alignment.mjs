import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.production";
const DEFAULT_SMOKE_ENV_FILE = ".env.launch-smoke";
const DEFAULT_BACKEND_ENV_FILE = ".env.backend-production";

const trim = (value) => String(value || "").trim();

const isPlaceholder = (value) => {
  const normalized = trim(value).toLowerCase();
  return !normalized || normalized.includes("<") || normalized.includes(">") || normalized.includes("replace-me");
};

const normalizeUrl = (value) => trim(value).replace(/\/$/, "");

const originOf = (value) => {
  try {
    return new URL(value).origin;
  } catch {
    return "";
  }
};

const shouldSkip = (value, options) => options.allowPlaceholders && isPlaceholder(value);

const isDocumentationDomainUrl = (value) => {
  try {
    const hostname = new URL(value).hostname.toLowerCase();
    return hostname === "example.com" || hostname.endsWith(".example.com") || hostname.endsWith(".example");
  } catch {
    return false;
  }
};

const isLocalhostUrl = (value) => {
  try {
    const hostname = new URL(value).hostname.toLowerCase();
    return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "0.0.0.0" || hostname === "::1" || hostname === "[::1]";
  } catch {
    return false;
  }
};

const isHttpUrl = (value) => {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol);
  } catch {
    return false;
  }
};

const pushInvalidUrl = (errors, key, value, options) => {
  if (options.allowPlaceholders || !value || shouldSkip(value, options)) return;
  if (!isHttpUrl(value)) {
    errors.push(`${key} must be an absolute http(s) URL.`);
  }
};

const pushDocumentationDomain = (errors, key, value, options) => {
  if (options.allowPlaceholders || !value) return;
  if (isDocumentationDomainUrl(value)) {
    errors.push(`${key} must not use a documentation/example domain.`);
  }
};

const pushLocalhostUrl = (errors, key, value, options) => {
  if (options.allowPlaceholders || !value) return;
  if (isLocalhostUrl(value)) {
    errors.push(`${key} must not point to localhost.`);
  }
};

const pushMismatch = (errors, left, right, message, options) => {
  if (shouldSkip(left, options) || shouldSkip(right, options)) return;
  if (normalizeUrl(left) !== normalizeUrl(right)) errors.push(message);
};

const pushOriginMismatch = (errors, left, right, message, options) => {
  if (shouldSkip(left, options) || shouldSkip(right, options)) return;
  if (originOf(left) !== originOf(right)) errors.push(message);
};

export const parseLaunchEnvAlignmentArgs = (argv = [], env = process.env) => {
  const options = {
    envFile: env.LAUNCH_ENV_FILE || DEFAULT_ENV_FILE,
    smokeEnvFile: env.LAUNCH_SMOKE_ENV_FILE || DEFAULT_SMOKE_ENV_FILE,
    backendEnvFile: env.LAUNCH_BACKEND_ENV_FILE || DEFAULT_BACKEND_ENV_FILE,
    baseUrl: env.LAUNCH_HEALTH_BASE_URL || "",
    allowPlaceholders: false,
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
    } else if (arg === "--allow-placeholders") {
      options.allowPlaceholders = true;
    }
  }

  return options;
};

export const validateLaunchEnvAlignment = ({ productionEnv = {}, smokeEnv = {}, backendEnv = {}, baseUrl = "" }, options = {}) => {
  const errors = [];
  const warnings = [];
  const appApiBase = productionEnv.VITE_YUDAO_APP_API_BASE;
  const smokeApiBase = smokeEnv.YUDAO_SMOKE_BASE_URL;
  const storefrontUrl = backendEnv.YUDAO_APP_UI_URL;
  const smokeReturnOrigin = smokeEnv.YUDAO_ORDER_SMOKE_RETURN_ORIGIN || smokeEnv.YUDAO_ORDER_SMOKE_RETURN_URL;

  for (const [key, value] of [
    ["VITE_YUDAO_APP_API_BASE", appApiBase],
    ["YUDAO_SMOKE_BASE_URL", smokeApiBase],
    ["YUDAO_ORDER_SMOKE_RETURN_ORIGIN", smokeReturnOrigin],
    ["YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL", smokeEnv.YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL],
    ["YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL", smokeEnv.YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL],
    ["YUDAO_APP_UI_URL", storefrontUrl],
    ["YUDAO_PAY_ORDER_NOTIFY_URL", backendEnv.YUDAO_PAY_ORDER_NOTIFY_URL],
    ["YUDAO_PAY_REFUND_NOTIFY_URL", backendEnv.YUDAO_PAY_REFUND_NOTIFY_URL],
    ["YUDAO_PAY_TRANSFER_NOTIFY_URL", backendEnv.YUDAO_PAY_TRANSFER_NOTIFY_URL],
    ["--base-url", baseUrl],
  ]) {
    pushInvalidUrl(errors, key, value, options);
    pushLocalhostUrl(errors, key, value, options);
    pushDocumentationDomain(errors, key, value, options);
  }

  pushMismatch(
    errors,
    appApiBase,
    smokeApiBase,
    "VITE_YUDAO_APP_API_BASE must match YUDAO_SMOKE_BASE_URL.",
    options,
  );
  pushMismatch(
    errors,
    productionEnv.VITE_YUDAO_APP_TENANT_ID,
    smokeEnv.YUDAO_SMOKE_TENANT_ID,
    "VITE_YUDAO_APP_TENANT_ID must match YUDAO_SMOKE_TENANT_ID.",
    options,
  );
  pushMismatch(
    errors,
    appApiBase,
    smokeEnv.YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL,
    "VITE_YUDAO_APP_API_BASE must match YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL.",
    options,
  );
  pushMismatch(
    errors,
    productionEnv.VITE_YUDAO_APP_TENANT_ID,
    smokeEnv.YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID,
    "VITE_YUDAO_APP_TENANT_ID must match YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID.",
    options,
  );
  pushOriginMismatch(
    errors,
    smokeEnv.YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL,
    appApiBase,
    "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL origin must match VITE_YUDAO_APP_API_BASE origin.",
    options,
  );
  pushMismatch(
    errors,
    productionEnv.VITE_YUDAO_APP_TENANT_ID,
    smokeEnv.YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID,
    "VITE_YUDAO_APP_TENANT_ID must match YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID.",
    options,
  );
  pushMismatch(
    errors,
    productionEnv.VITE_YUDAO_PAY_CHANNEL_CODE,
    smokeEnv.YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE,
    "VITE_YUDAO_PAY_CHANNEL_CODE must match YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE.",
    options,
  );

  if (baseUrl) {
    pushMismatch(errors, storefrontUrl, baseUrl, "YUDAO_APP_UI_URL must match --base-url.", options);
    pushMismatch(errors, smokeReturnOrigin, baseUrl, "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must match --base-url.", options);
  } else if (!storefrontUrl || !smokeReturnOrigin) {
    warnings.push("--base-url was not provided, so storefront URL alignment is only checked between backend and smoke env.");
  }

  if (!baseUrl) {
    pushMismatch(
      errors,
      storefrontUrl,
      smokeReturnOrigin,
      "YUDAO_APP_UI_URL must match YUDAO_ORDER_SMOKE_RETURN_ORIGIN.",
      options,
    );
  }

  for (const key of ["YUDAO_PAY_ORDER_NOTIFY_URL", "YUDAO_PAY_REFUND_NOTIFY_URL", "YUDAO_PAY_TRANSFER_NOTIFY_URL"]) {
    pushOriginMismatch(errors, backendEnv[key], appApiBase, `${key} origin must match VITE_YUDAO_APP_API_BASE origin.`, options);
  }

  return {
    ok: errors.length === 0,
    errors,
    warnings,
  };
};

const readEnv = (envFile, label) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    return {
      ok: false,
      errors: [`${label} env file not found: ${envPath}`],
      env: {},
    };
  }

  return {
    ok: true,
    errors: [],
    env: parseEnvFileContent(readFileSync(envPath, "utf8")),
  };
};

export const readAndValidateLaunchEnvAlignment = (options = {}) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const smokeEnvFile = options.smokeEnvFile || DEFAULT_SMOKE_ENV_FILE;
  const backendEnvFile = options.backendEnvFile || DEFAULT_BACKEND_ENV_FILE;
  const production = readEnv(envFile, "Production");
  const smoke = readEnv(smokeEnvFile, "Launch smoke");
  const backend = readEnv(backendEnvFile, "Backend production");
  const fileErrors = [...production.errors, ...smoke.errors, ...backend.errors];

  if (fileErrors.length) {
    return {
      ok: false,
      errors: fileErrors,
      warnings: [],
    };
  }

  return validateLaunchEnvAlignment(
    {
      productionEnv: production.env,
      smokeEnv: smoke.env,
      backendEnv: backend.env,
      baseUrl: options.baseUrl || "",
    },
    options,
  );
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  const options = parseLaunchEnvAlignmentArgs(process.argv.slice(2));
  const result = readAndValidateLaunchEnvAlignment(options);
  if (result.ok) {
    console.log("Launch env alignment check passed.");
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
  } else {
    console.error("Launch env alignment check failed:");
    result.errors.forEach((error) => console.error(`error: ${error}`));
    result.warnings.forEach((warning) => console.warn(`warning: ${warning}`));
    process.exitCode = 1;
  }
}
