import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import {
  parseLaunchEnvAlignmentArgs,
  validateLaunchEnvAlignment,
} from "../scripts/verify-launch-env-alignment.mjs";
import { parseEnvFileContent } from "../scripts/verify-production-env.mjs";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

const productionEnv = {
  VITE_YUDAO_APP_API_BASE: "https://api.oakvedhome.com/app-api",
  VITE_YUDAO_APP_TENANT_ID: "121",
  VITE_YUDAO_PAY_CHANNEL_CODE: "alipay_pc",
};

const smokeEnv = {
  YUDAO_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
  YUDAO_SMOKE_TENANT_ID: "121",
  YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
  YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.oakvedhome.com",
  YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
  YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
  YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
  YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
};

const backendEnv = {
  YUDAO_APP_UI_URL: "https://shop.oakvedhome.com",
  YUDAO_PAY_ORDER_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/order",
  YUDAO_PAY_REFUND_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/refund",
  YUDAO_PAY_TRANSFER_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/transfer",
};

describe("launch env alignment", () => {
  it("exposes a repeatable launch env alignment command", () => {
    const packageJson = JSON.parse(readSource("../package.json"));

    expect(packageJson.scripts["verify:launch-env-alignment"]).toBe("node scripts/verify-launch-env-alignment.mjs");
  });

  it("parses env file and launch base URL flags", () => {
    expect(
      parseLaunchEnvAlignmentArgs([
        "--env-file",
        ".env.production",
        "--smoke-env-file=.env.launch-smoke",
        "--backend-env-file",
        ".env.backend-production",
        "--base-url",
        "https://shop.oakvedhome.com",
        "--allow-placeholders",
      ]),
    ).toEqual({
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      baseUrl: "https://shop.oakvedhome.com",
      allowPlaceholders: true,
    });
  });

  it("accepts aligned storefront, smoke, backend callback, tenant, and payment values", () => {
    expect(
      validateLaunchEnvAlignment({
        productionEnv,
        smokeEnv,
        backendEnv,
        baseUrl: "https://shop.oakvedhome.com",
      }),
    ).toEqual({ ok: true, errors: [], warnings: [] });
  });

  it("rejects documentation domains even when all launch endpoints are aligned", () => {
    const result = validateLaunchEnvAlignment({
      productionEnv: {
        ...productionEnv,
        VITE_YUDAO_APP_API_BASE: "https://api.example.com/app-api",
      },
      smokeEnv: {
        ...smokeEnv,
        YUDAO_SMOKE_BASE_URL: "https://api.example.com/app-api",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.example.com",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.example.com/app-api",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.example.com/admin-api",
      },
      backendEnv: {
        ...backendEnv,
        YUDAO_APP_UI_URL: "https://shop.example.com",
        YUDAO_PAY_ORDER_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/order",
        YUDAO_PAY_REFUND_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/refund",
        YUDAO_PAY_TRANSFER_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/transfer",
      },
      baseUrl: "https://shop.example.com",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "VITE_YUDAO_APP_API_BASE must not use a documentation/example domain.",
        "YUDAO_SMOKE_BASE_URL must not use a documentation/example domain.",
        "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must not use a documentation/example domain.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must not use a documentation/example domain.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must not use a documentation/example domain.",
        "YUDAO_APP_UI_URL must not use a documentation/example domain.",
        "YUDAO_PAY_ORDER_NOTIFY_URL must not use a documentation/example domain.",
        "YUDAO_PAY_REFUND_NOTIFY_URL must not use a documentation/example domain.",
        "YUDAO_PAY_TRANSFER_NOTIFY_URL must not use a documentation/example domain.",
        "--base-url must not use a documentation/example domain.",
      ]),
    );
  });

  it("rejects localhost URLs even when all launch endpoints are aligned", () => {
    const result = validateLaunchEnvAlignment({
      productionEnv: {
        ...productionEnv,
        VITE_YUDAO_APP_API_BASE: "http://localhost:48080/app-api",
      },
      smokeEnv: {
        ...smokeEnv,
        YUDAO_SMOKE_BASE_URL: "http://localhost:48080/app-api",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "http://127.0.0.1:4173",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "http://localhost:48080/app-api",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "http://localhost:48080/admin-api",
      },
      backendEnv: {
        ...backendEnv,
        YUDAO_APP_UI_URL: "http://127.0.0.1:4173",
        YUDAO_PAY_ORDER_NOTIFY_URL: "http://localhost:48080/admin-api/pay/notify/order",
        YUDAO_PAY_REFUND_NOTIFY_URL: "http://localhost:48080/admin-api/pay/notify/refund",
        YUDAO_PAY_TRANSFER_NOTIFY_URL: "http://localhost:48080/admin-api/pay/notify/transfer",
      },
      baseUrl: "http://127.0.0.1:4173",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "VITE_YUDAO_APP_API_BASE must not point to localhost.",
        "YUDAO_SMOKE_BASE_URL must not point to localhost.",
        "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must not point to localhost.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must not point to localhost.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must not point to localhost.",
        "YUDAO_APP_UI_URL must not point to localhost.",
        "YUDAO_PAY_ORDER_NOTIFY_URL must not point to localhost.",
        "YUDAO_PAY_REFUND_NOTIFY_URL must not point to localhost.",
        "YUDAO_PAY_TRANSFER_NOTIFY_URL must not point to localhost.",
        "--base-url must not point to localhost.",
      ]),
    );
  });

  it("rejects non-http launch URLs even when the values are internally aligned", () => {
    const result = validateLaunchEnvAlignment({
      productionEnv: {
        ...productionEnv,
        VITE_YUDAO_APP_API_BASE: "api.oakvedhome.com/app-api",
      },
      smokeEnv: {
        ...smokeEnv,
        YUDAO_SMOKE_BASE_URL: "api.oakvedhome.com/app-api",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "shop.oakvedhome.com",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "api.oakvedhome.com/app-api",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "api.oakvedhome.com/admin-api",
      },
      backendEnv: {
        ...backendEnv,
        YUDAO_APP_UI_URL: "shop.oakvedhome.com",
        YUDAO_PAY_ORDER_NOTIFY_URL: "api.oakvedhome.com/admin-api/pay/notify/order",
        YUDAO_PAY_REFUND_NOTIFY_URL: "api.oakvedhome.com/admin-api/pay/notify/refund",
        YUDAO_PAY_TRANSFER_NOTIFY_URL: "api.oakvedhome.com/admin-api/pay/notify/transfer",
      },
      baseUrl: "shop.oakvedhome.com",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "VITE_YUDAO_APP_API_BASE must be an absolute http(s) URL.",
        "YUDAO_SMOKE_BASE_URL must be an absolute http(s) URL.",
        "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must be an absolute http(s) URL.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must be an absolute http(s) URL.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must be an absolute http(s) URL.",
        "YUDAO_APP_UI_URL must be an absolute http(s) URL.",
        "YUDAO_PAY_ORDER_NOTIFY_URL must be an absolute http(s) URL.",
        "YUDAO_PAY_REFUND_NOTIFY_URL must be an absolute http(s) URL.",
        "YUDAO_PAY_TRANSFER_NOTIFY_URL must be an absolute http(s) URL.",
        "--base-url must be an absolute http(s) URL.",
      ]),
    );
  });

  it("rejects mismatched API, tenant, payment, storefront, and callback origins", () => {
    const result = validateLaunchEnvAlignment({
      productionEnv,
      smokeEnv: {
        ...smokeEnv,
        YUDAO_SMOKE_BASE_URL: "https://staging-api.oakved.example/app-api",
        YUDAO_SMOKE_TENANT_ID: "122",
        YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "stripe",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://staging-shop.oakved.example",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://real-account-api.oakved.example/app-api",
        YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "123",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://admin-smoke.oakved.example/admin-api",
        YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "124",
      },
      backendEnv: {
        ...backendEnv,
        YUDAO_APP_UI_URL: "https://backend-shop.oakved.example",
        YUDAO_PAY_ORDER_NOTIFY_URL: "https://pay.oakved.example/admin-api/pay/notify/order",
      },
      baseUrl: "https://shop.oakved.example",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("VITE_YUDAO_APP_API_BASE must match YUDAO_SMOKE_BASE_URL.");
    expect(result.errors).toContain("VITE_YUDAO_APP_TENANT_ID must match YUDAO_SMOKE_TENANT_ID.");
    expect(result.errors).toContain("VITE_YUDAO_PAY_CHANNEL_CODE must match YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE.");
    expect(result.errors).toContain("VITE_YUDAO_APP_API_BASE must match YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL.");
    expect(result.errors).toContain("VITE_YUDAO_APP_TENANT_ID must match YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID.");
    expect(result.errors).toContain("YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL origin must match VITE_YUDAO_APP_API_BASE origin.");
    expect(result.errors).toContain("VITE_YUDAO_APP_TENANT_ID must match YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID.");
    expect(result.errors).toContain("YUDAO_APP_UI_URL must match --base-url.");
    expect(result.errors).toContain("YUDAO_ORDER_SMOKE_RETURN_ORIGIN must match --base-url.");
    expect(result.errors).toContain("YUDAO_PAY_ORDER_NOTIFY_URL origin must match VITE_YUDAO_APP_API_BASE origin.");
  });

  it("can validate concrete env file paths", async () => {
    const directory = mkdtempSync(join(tmpdir(), "oakved-launch-alignment-"));
    const envFile = join(directory, ".env.production");
    const smokeEnvFile = join(directory, ".env.launch-smoke");
    const backendEnvFile = join(directory, ".env.backend-production");
    const writeEnv = (path, env) =>
      writeFileSync(path, Object.entries(env).map(([key, value]) => `${key}=${value}`).join("\n"), "utf8");

    writeEnv(envFile, productionEnv);
    writeEnv(smokeEnvFile, smokeEnv);
    writeEnv(backendEnvFile, backendEnv);

    try {
      const { readAndValidateLaunchEnvAlignment } = await import("../scripts/verify-launch-env-alignment.mjs");

      expect(
        readAndValidateLaunchEnvAlignment({
          envFile,
          smokeEnvFile,
          backendEnvFile,
          baseUrl: "https://shop.oakvedhome.com",
        }).ok,
      ).toBe(true);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("accepts checked-in examples only when placeholders are allowed", () => {
    const result = validateLaunchEnvAlignment(
      {
        productionEnv: parseEnvFileContent(readSource("../.env.production.example")),
        smokeEnv: parseEnvFileContent(readSource("../.env.launch-smoke.example")),
        backendEnv: parseEnvFileContent(readSource("../.env.backend-production.example")),
        baseUrl: "https://shop.oakved.example",
      },
      { allowPlaceholders: true },
    );

    expect(result.ok).toBe(true);
  });
});
