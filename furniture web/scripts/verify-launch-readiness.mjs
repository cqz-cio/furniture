import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.production";
const DEFAULT_BACKEND_ENV_FILE = ".env.backend-production";
const DEFAULT_DOCKER_TAG = "oakved-storefront:launch-smoke";
const MONOREPO_ROOT = resolve(process.cwd(), "..");
const ADMIN_APP_DIR = resolve(MONOREPO_ROOT, "yudao电商管理平台前后端", "yudao-ui-admin-vue3");
const BACKEND_APP_DIR = resolve(MONOREPO_ROOT, "yudao电商管理平台前后端", "yudao-cloud");
const DOCKER_BUILD_ARG_KEYS = [
  "VITE_YUDAO_APP_API_BASE",
  "VITE_YUDAO_APP_TENANT_ID",
  "VITE_YUDAO_US_DEFAULT_AREA_ID",
  "VITE_YUDAO_PAY_CHANNEL_CODE",
  "VITE_ADDRESS_VERIFICATION_PATH",
  "VITE_ADDRESS_VERIFICATION_STATUS_PATH",
];

export const parseLaunchReadinessArgs = (argv = []) => {
  const options = {
    envFile: DEFAULT_ENV_FILE,
    smokeEnvFile: "",
    includeDocker: false,
    includeCheckoutSmoke: false,
    includeDbMigrations: false,
    includeBackendProdConfig: false,
    includeBackendProdEnv: false,
    includeLaunchEnvAlignment: false,
    backendEnvFile: DEFAULT_BACKEND_ENV_FILE,
    backendEnvAllowPlaceholders: false,
    baseUrl: "",
    launchEnvAlignmentAllowPlaceholders: false,
    includeAdminCheck: false,
    includeAdminBuild: false,
    includeBackendBuild: false,
    includeLiveBusinessSmoke: false,
    includeOrderLiveSmoke: false,
    includeWishlistSmoke: false,
    dockerTag: DEFAULT_DOCKER_TAG,
    wishlistSmokeMode: "live",
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    } else if (arg === "--smoke-env-file") {
      options.smokeEnvFile = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--smoke-env-file=")) {
      options.smokeEnvFile = arg.slice("--smoke-env-file=".length);
    } else if (arg === "--include-docker") {
      options.includeDocker = true;
    } else if (arg === "--include-db-migrations") {
      options.includeDbMigrations = true;
    } else if (arg === "--include-backend-prod-config") {
      options.includeBackendProdConfig = true;
    } else if (arg === "--include-backend-prod-env") {
      options.includeBackendProdEnv = true;
    } else if (arg === "--include-launch-env-alignment") {
      options.includeLaunchEnvAlignment = true;
    } else if (arg === "--backend-env-file") {
      options.backendEnvFile = argv[index + 1] || DEFAULT_BACKEND_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--backend-env-file=")) {
      options.backendEnvFile = arg.slice("--backend-env-file=".length);
    } else if (arg === "--backend-env-allow-placeholders") {
      options.backendEnvAllowPlaceholders = true;
    } else if (arg === "--base-url") {
      options.baseUrl = argv[index + 1] || "";
      index += 1;
    } else if (arg.startsWith("--base-url=")) {
      options.baseUrl = arg.slice("--base-url=".length);
    } else if (arg === "--launch-env-alignment-allow-placeholders") {
      options.launchEnvAlignmentAllowPlaceholders = true;
    } else if (arg === "--include-admin-check") {
      options.includeAdminCheck = true;
    } else if (arg === "--include-admin-build") {
      options.includeAdminBuild = true;
    } else if (arg === "--include-backend-build") {
      options.includeBackendBuild = true;
    } else if (arg === "--include-checkout-smoke") {
      options.includeCheckoutSmoke = true;
    } else if (arg === "--include-live-business-smoke") {
      options.includeLiveBusinessSmoke = true;
    } else if (arg === "--include-order-live-smoke") {
      options.includeOrderLiveSmoke = true;
    } else if (arg === "--include-wishlist-smoke") {
      options.includeWishlistSmoke = true;
    } else if (arg === "--wishlist-smoke-mode") {
      options.wishlistSmokeMode = argv[index + 1] || "live";
      index += 1;
    } else if (arg.startsWith("--wishlist-smoke-mode=")) {
      options.wishlistSmokeMode = arg.slice("--wishlist-smoke-mode=".length);
    } else if (arg === "--docker-tag") {
      options.dockerTag = argv[index + 1] || DEFAULT_DOCKER_TAG;
      index += 1;
    } else if (arg.startsWith("--docker-tag=")) {
      options.dockerTag = arg.slice("--docker-tag=".length);
    }
  }

  return options;
};

const readEnvFile = (envFile) => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) return {};
  return parseEnvFileContent(readFileSync(envPath, "utf8"));
};

export const buildDockerArgs = (env) =>
  DOCKER_BUILD_ARG_KEYS.flatMap((key) => {
    const value = String(env[key] || "").trim();
    return value ? ["--build-arg", `${key}=${value}`] : [];
  });

export const resolveStepProcess = (step, env = process.env, platform = process.platform) => {
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

  if (platform === "win32" && ["pnpm", "mvn"].includes(step.command)) {
    return {
      command: "cmd.exe",
      args: ["/d", "/s", "/c", `${step.command}.cmd`, ...step.args],
    };
  }

  return {
    command: step.command,
    args: step.args,
  };
};

export const buildLaunchReadinessSteps = (options = {}) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const smokeEnvFile = options.smokeEnvFile || envFile;
  const viteEnv = readEnvFile(envFile);
  const steps = [
    {
      name: "production-env",
      command: "node",
      args: ["scripts/verify-production-env.mjs", "--env-file", envFile],
    },
    {
      name: "npm-audit",
      command: "npm",
      args: ["audit", "--audit-level=low"],
    },
    {
      name: "unit-tests",
      command: "npm",
      args: ["test"],
    },
    {
      name: "production-build",
      command: "npm",
      args: ["run", "build"],
      env: viteEnv,
    },
  ];

  if (options.includeDocker) {
    steps.push({
      name: "docker-build",
      command: "docker",
      args: ["build", "-t", options.dockerTag || DEFAULT_DOCKER_TAG, ...buildDockerArgs(viteEnv), "."],
    });
  }

  if (options.includeDbMigrations) {
    steps.push({
      name: "db-migrations",
      command: "npm",
      args: ["run", "verify:db-migrations"],
    });
  }

  if (options.includeBackendProdConfig) {
    steps.push({
      name: "backend-production-config",
      command: "npm",
      args: ["run", "verify:backend-production-config"],
    });
  }

  if (options.includeBackendProdEnv) {
    steps.push({
      name: "backend-production-env",
      command: "npm",
      args: [
        "run",
        "verify:backend-production-env",
        "--",
        "--env-file",
        options.backendEnvFile || DEFAULT_BACKEND_ENV_FILE,
        ...(options.backendEnvAllowPlaceholders ? ["--allow-placeholders"] : []),
      ],
    });
  }

  if (options.includeLaunchEnvAlignment) {
    steps.push({
      name: "launch-env-alignment",
      command: "npm",
      args: [
        "run",
        "verify:launch-env-alignment",
        "--",
        "--env-file",
        envFile,
        "--smoke-env-file",
        smokeEnvFile,
        "--backend-env-file",
        options.backendEnvFile || DEFAULT_BACKEND_ENV_FILE,
        ...(options.baseUrl ? ["--base-url", options.baseUrl] : []),
        ...(options.launchEnvAlignmentAllowPlaceholders ? ["--allow-placeholders"] : []),
      ],
    });
  }

  if (options.includeAdminCheck) {
    steps.push({
      name: "admin-furniture-lite-check",
      command: "pnpm",
      args: ["run", "check:furniture-lite"],
      cwd: ADMIN_APP_DIR,
    });
  }

  if (options.includeAdminBuild) {
    steps.push({
      name: "admin-production-build",
      command: "pnpm",
      args: ["run", "build:prod"],
      cwd: ADMIN_APP_DIR,
    });
  }

  if (options.includeBackendBuild) {
    steps.push({
      name: "backend-server-build",
      command: "mvn",
      args: ["-pl", "yudao-server", "-am", "-DskipTests", "package"],
      cwd: BACKEND_APP_DIR,
    });
  }

  if (options.includeCheckoutSmoke) {
    steps.push({
      name: "checkout-preview-smoke",
      command: "npm",
      args: ["run", "test:e2e:checkout:preview"],
      env: {
        ...viteEnv,
        CHECKOUT_PREVIEW_ENV_FILE: envFile,
        CHECKOUT_PREVIEW_SKIP_BUILD: "true",
      },
    });
  }

  if (options.includeWishlistSmoke) {
    steps.push({
      name: "wishlist-smoke",
      command: "npm",
      args: ["run", "test:smoke:wishlist"],
      env: {
        WISHLIST_SMOKE_MODE: options.wishlistSmokeMode || "live",
      },
    });
  }

  if (options.includeLiveBusinessSmoke) {
    steps.push({
      name: "live-business-smoke",
      command: "npm",
      args: ["run", "test:smoke:live-business", "--", "--env-file", smokeEnvFile],
    });
  }

  if (options.includeOrderLiveSmoke) {
    steps.push({
      name: "order-live-smoke",
      command: "npm",
      args: ["run", "test:smoke:order-live", "--", "--env-file", smokeEnvFile],
    });
  }

  return steps;
};

export const runLaunchReadiness = (options = {}) => {
  const steps = buildLaunchReadinessSteps(options);
  for (const step of steps) {
    console.log(`\n==> ${step.name}`);
    const processStep = resolveStepProcess(step);
    const result = spawnSync(processStep.command, processStep.args, {
      cwd: step.cwd || process.cwd(),
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
  const options = parseLaunchReadinessArgs(process.argv.slice(2));
  const result = runLaunchReadiness(options);
  if (result.ok) {
    console.log("\nLaunch readiness check passed.");
  } else {
    console.error(`\nLaunch readiness check failed at ${result.failedStep}.`);
    process.exitCode = result.status || 1;
  }
}
