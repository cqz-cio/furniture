import { spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const host = process.env.CHECKOUT_PREVIEW_HOST || "127.0.0.1";
const port = Number(process.env.CHECKOUT_PREVIEW_PORT || 4173);
const baseUrl = `http://${host}:${port}`;
const timeoutMs = Number(process.env.CHECKOUT_PREVIEW_TIMEOUT_MS || 30000);
const envFile = process.env.CHECKOUT_PREVIEW_ENV_FILE || ".env.production.example";
const skipBuild = process.env.CHECKOUT_PREVIEW_SKIP_BUILD === "true";

const npmProcess = () => {
  if (process.platform === "win32" && process.env.npm_execpath) {
    return {
      command: process.execPath,
      argsPrefix: [process.env.npm_execpath],
    };
  }
  return {
    command: "npm",
    argsPrefix: [],
  };
};

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export const waitForPreview = async (url = baseUrl, timeout = timeoutMs) => {
  const startedAt = Date.now();
  let lastError;

  while (Date.now() - startedAt < timeout) {
    try {
      const response = await fetch(url);
      if (response.ok) return true;
      lastError = new Error(`Preview responded with HTTP ${response.status}`);
    } catch (caught) {
      lastError = caught;
    }
    await wait(250);
  }

  throw new Error(`Vite preview did not become ready at ${url}`, { cause: lastError });
};

const runChild = (command, args, options = {}) =>
  new Promise((resolve) => {
    const child = spawn(command, args, {
      cwd: process.cwd(),
      env: { ...process.env, ...(options.env || {}) },
      stdio: options.stdio || "inherit",
    });
    child.on("exit", (code, signal) => resolve({ code, signal }));
  });

const readViteEnv = () => {
  const envPath = resolve(process.cwd(), envFile);
  if (!existsSync(envPath)) return {};
  return parseEnvFileContent(readFileSync(envPath, "utf8"));
};

const runNpm = (args, options = {}) => {
  const npm = npmProcess();
  return runChild(npm.command, [...npm.argsPrefix, ...args], options);
};

const run = async () => {
  const viteEnv = readViteEnv();
  if (!skipBuild) {
    const buildResult = await runNpm(["run", "build"], { env: viteEnv });
    if (buildResult.code !== 0) {
      process.exitCode = buildResult.code || 1;
      return;
    }
  }

  const npm = npmProcess();
  const previewProcess = spawn(npm.command, [...npm.argsPrefix, "run", "preview", "--", "--host", host, "--port", String(port), "--strictPort"], {
    cwd: process.cwd(),
    env: { ...process.env, ...viteEnv },
    stdio: "inherit",
  });

  try {
    await waitForPreview(baseUrl, timeoutMs);
    const result = await runChild("node", ["scripts/checkout-e2e-smoke.mjs"], {
      env: {
        CHECKOUT_E2E_BASE_URL: baseUrl,
      },
    });

    if (result.code !== 0) {
      process.exitCode = result.code || 1;
    }
  } finally {
    previewProcess.kill();
  }
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
