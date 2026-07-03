import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.production";

export const parseLiveBusinessSmokeArgs = (argv = []) => {
  const options = {
    envFile: DEFAULT_ENV_FILE,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    }
  }

  return options;
};

const readEnvFile = (envFile) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) {
    throw new Error(`Production env file not found: ${envPath}`);
  }
  return parseEnvFileContent(readFileSync(envPath, "utf8"));
};

const required = (env, key) => {
  const value = String(env[key] || "").trim();
  if (!value) throw new Error(`${key} is required for live business smoke.`);
  return value;
};

const isDocumentationDomain = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "example.com" || normalized.endsWith(".example.com") || normalized.endsWith(".example");
};

const isLocalhost = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "0.0.0.0" || normalized === "::1" || normalized === "[::1]";
};

const requireLaunchUrl = (env, key) => {
  const normalized = required(env, key).replace(/\/$/, "");
  try {
    const url = new URL(normalized);
    if (!["http:", "https:"].includes(url.protocol)) throw new Error(`${key} must be an absolute http(s) URL.`);
    if (isLocalhost(url.hostname)) throw new Error(`${key} must not point to localhost`);
    if (isDocumentationDomain(url.hostname)) throw new Error(`${key} must not use a documentation/example domain`);
  } catch (error) {
    if (error.message.includes("absolute http(s) URL")) throw error;
    if (error.message.includes("must not point to localhost")) throw error;
    if (error.message.includes("documentation/example domain")) throw error;
    throw new Error(`${key} must be an absolute http(s) URL.`);
  }
  return normalized;
};

const requireRealToken = (env, key) => {
  const token = required(env, key);
  const normalized = token.toLowerCase();
  if (normalized.includes("<") || normalized.includes(">") || normalized.includes("replace-me") || normalized.includes("your-token")) {
    throw new Error(`${key} must be a real live token.`);
  }
  return token;
};

export const buildLiveBusinessSmokeSteps = (options = {}, runtimeEnv = process.env) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const fileEnv = readEnvFile(envFile);
  const smokeEnv = {
    WISHLIST_SMOKE_MODE: "live",
    YUDAO_SMOKE_BASE_URL:
      runtimeEnv.YUDAO_SMOKE_BASE_URL || fileEnv.YUDAO_SMOKE_BASE_URL || fileEnv.VITE_YUDAO_APP_API_BASE,
    YUDAO_SMOKE_TENANT_ID:
      runtimeEnv.YUDAO_SMOKE_TENANT_ID || fileEnv.YUDAO_SMOKE_TENANT_ID || fileEnv.VITE_YUDAO_APP_TENANT_ID,
    YUDAO_SMOKE_TOKEN: runtimeEnv.YUDAO_SMOKE_TOKEN || fileEnv.YUDAO_SMOKE_TOKEN,
  };

  smokeEnv.YUDAO_SMOKE_TOKEN = requireRealToken(smokeEnv, "YUDAO_SMOKE_TOKEN");
  smokeEnv.YUDAO_SMOKE_BASE_URL = requireLaunchUrl(smokeEnv, "YUDAO_SMOKE_BASE_URL");
  required(smokeEnv, "YUDAO_SMOKE_TENANT_ID");

  return [
    {
      name: "wishlist-live-smoke",
      command: "npm",
      args: ["run", "test:smoke:wishlist"],
      env: smokeEnv,
    },
  ];
};

const resolveStepProcess = (step, env = process.env, platform = process.platform) => {
  if (platform === "win32" && step.command === "npm") {
    if (env.npm_execpath) {
      return {
        command: process.execPath,
        args: [env.npm_execpath, ...step.args],
      };
    }
    return {
      command: "cmd.exe",
      args: ["/d", "/s", "/c", "npm.cmd", ...step.args],
    };
  }

  return {
    command: step.command,
    args: step.args,
  };
};

export const runLiveBusinessSmoke = (options = {}) => {
  const steps = buildLiveBusinessSmokeSteps(options);

  for (const step of steps) {
    console.log(`\n==> ${step.name}`);
    const processStep = resolveStepProcess(step);
    const result = spawnSync(processStep.command, processStep.args, {
      cwd: process.cwd(),
      env: { ...process.env, ...(step.env || {}) },
      stdio: "inherit",
    });
    if (result.status !== 0) {
      return {
        ok: false,
        failedStep: step.name,
        status: result.status,
      };
    }
  }

  return {
    ok: true,
    failedStep: "",
    status: 0,
  };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  try {
    const options = parseLiveBusinessSmokeArgs(process.argv.slice(2));
    const result = runLiveBusinessSmoke(options);
    if (result.ok) {
      console.log("\nLive business smoke passed.");
    } else {
      console.error(`\nLive business smoke failed at ${result.failedStep}.`);
      process.exitCode = result.status || 1;
    }
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
