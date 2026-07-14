import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { parseEnvFileContent, validateProductionEnv } from "../scripts/verify-production-env.mjs";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("production environment readiness", () => {
  it("exposes an npm gate for production env verification", () => {
    const packageJson = JSON.parse(readSource("../package.json"));

    expect(packageJson.scripts["verify:production-env"]).toBe("node scripts/verify-production-env.mjs");
  });

  it("documents every storefront production env variable in the production example", () => {
    const example = readSource("../.env.production.example");

    for (const key of [
      "VITE_YUDAO_APP_API_BASE",
      "VITE_YUDAO_APP_TENANT_ID",
      "VITE_YUDAO_US_DEFAULT_AREA_ID",
      "VITE_YUDAO_PAY_CHANNEL_CODE",
      "VITE_ADDRESS_VERIFICATION_PATH",
      "VITE_ADDRESS_VERIFICATION_STATUS_PATH",
      "VITE_SHOW_AUTH_TOKEN_PANEL",
    ]) {
      expect(example).toContain(`${key}=`);
    }
  });

  it("rejects missing or unsafe launch-critical env values", () => {
    const result = validateProductionEnv({
      VITE_YUDAO_APP_API_BASE: "http://127.0.0.1:48080/app-api",
      VITE_YUDAO_APP_TENANT_ID: "",
      VITE_YUDAO_US_DEFAULT_AREA_ID: "not-a-number",
      VITE_YUDAO_PAY_CHANNEL_CODE: "",
      VITE_SHOW_AUTH_TOKEN_PANEL: "true",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("VITE_YUDAO_APP_API_BASE must be an absolute http(s) URL that is not localhost.");
    expect(result.errors).toContain("VITE_YUDAO_APP_TENANT_ID is required.");
    expect(result.errors).toContain("VITE_YUDAO_US_DEFAULT_AREA_ID must be a positive integer.");
    expect(result.errors).toContain("VITE_YUDAO_PAY_CHANNEL_CODE is required before accepting production payment.");
    expect(result.errors).toContain("VITE_SHOW_AUTH_TOKEN_PANEL must not be true in production.");
  });

  it("rejects documentation domains in the storefront production API URL", () => {
    const result = validateProductionEnv({
      VITE_YUDAO_APP_API_BASE: "https://api.oakved.example/app-api",
      VITE_YUDAO_APP_TENANT_ID: "121",
      VITE_YUDAO_US_DEFAULT_AREA_ID: "100200",
      VITE_YUDAO_PAY_CHANNEL_CODE: "alipay_pc",
      VITE_SHOW_AUTH_TOKEN_PANEL: "false",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("VITE_YUDAO_APP_API_BASE must not use a documentation/example domain.");
  });

  it("accepts production-shaped env files and normalizes comments and quotes", () => {
    const env = parseEnvFileContent(`
      # Production API
      VITE_YUDAO_APP_API_BASE="https://api.oakvedhome.com/app-api"
      VITE_YUDAO_APP_TENANT_ID=121
      VITE_YUDAO_US_DEFAULT_AREA_ID=100200
      VITE_YUDAO_PAY_CHANNEL_CODE=alipay_pc
      VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify
      VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status
      VITE_SHOW_AUTH_TOKEN_PANEL=false
    `);

    expect(validateProductionEnv(env)).toEqual({ ok: true, errors: [], warnings: [] });
  });

  it("can validate a concrete env file path", async () => {
    const directory = mkdtempSync(join(tmpdir(), "oakved-env-"));
    const envFile = join(directory, ".env.production");
    writeFileSync(
      envFile,
      [
        "VITE_YUDAO_APP_API_BASE=https://api.oakvedhome.com/app-api",
        "VITE_YUDAO_APP_TENANT_ID=121",
        "VITE_YUDAO_US_DEFAULT_AREA_ID=100200",
        "VITE_YUDAO_PAY_CHANNEL_CODE=alipay_pc",
        "VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify",
        "VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status",
        "VITE_SHOW_AUTH_TOKEN_PANEL=false",
      ].join("\n"),
    );

    try {
      const { readAndValidateProductionEnv } = await import("../scripts/verify-production-env.mjs");

      expect(readAndValidateProductionEnv(envFile).ok).toBe(true);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });
});
