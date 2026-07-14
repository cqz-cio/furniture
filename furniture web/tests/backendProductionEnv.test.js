import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import {
  parseBackendProductionEnvArgs,
  validateBackendProductionEnv,
} from "../scripts/verify-backend-production-env.mjs";
import { parseEnvFileContent } from "../scripts/verify-production-env.mjs";

const readSource = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

const validBackendEnv = {
  SPRING_PROFILES_ACTIVE: "prod",
  YUDAO_DB_URL: "jdbc:mysql://mysql.internal:3306/oakved?useSSL=true&serverTimezone=Asia/Shanghai",
  YUDAO_DB_USERNAME: "oakved_app",
  YUDAO_DB_PASSWORD: "replace-with-strong-password",
  YUDAO_REDIS_HOST: "redis.internal",
  YUDAO_REDIS_PORT: "6379",
  YUDAO_ADMIN_UI_URL: "https://admin.oakvedhome.com",
  YUDAO_APP_UI_URL: "https://shop.oakvedhome.com",
  YUDAO_PAY_ORDER_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/order",
  YUDAO_PAY_REFUND_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/refund",
  YUDAO_PAY_TRANSFER_NOTIFY_URL: "https://api.oakvedhome.com/admin-api/pay/notify/transfer",
  YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY: "replace-with-real-google-address-validation-key",
};

describe("backend production runtime environment", () => {
  it("exposes a repeatable backend production env verifier", () => {
    const packageJson = JSON.parse(readSource("../package.json"));

    expect(packageJson.scripts["verify:backend-production-env"]).toBe(
      "node scripts/verify-backend-production-env.mjs",
    );
  });

  it("documents every required backend production env variable in the example file", () => {
    const example = readSource("../.env.backend-production.example");

    for (const key of Object.keys(validBackendEnv)) {
      expect(example).toContain(`${key}=`);
    }
  });

  it("parses env file and placeholder flags", () => {
    expect(
      parseBackendProductionEnvArgs([
        "--env-file",
        ".env.backend-production",
        "--allow-placeholders",
      ]),
    ).toEqual({
      envFile: ".env.backend-production",
      allowPlaceholders: true,
    });
  });

  it("accepts production-shaped backend env values", () => {
    expect(validateBackendProductionEnv(validBackendEnv)).toEqual({ ok: true, errors: [], warnings: [] });
  });

  it("rejects local, mock, missing, and unsafe backend env values", () => {
    const result = validateBackendProductionEnv({
      SPRING_PROFILES_ACTIVE: "local",
      YUDAO_DB_URL: "jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro",
      YUDAO_DB_USERNAME: "root",
      YUDAO_DB_PASSWORD: "123456",
      YUDAO_REDIS_HOST: "localhost",
      YUDAO_REDIS_PORT: "not-a-port",
      YUDAO_ADMIN_UI_URL: "http://localhost:5173",
      YUDAO_APP_UI_URL: "http://127.0.0.1:5173",
      YUDAO_PAY_ORDER_NOTIFY_URL: "http://api.oakved.example/admin-api/pay/notify/order",
      YUDAO_PAY_REFUND_NOTIFY_URL: "",
      YUDAO_PAY_TRANSFER_NOTIFY_URL: "not-a-url",
      YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY: "",
      YUDAO_SECURITY_MOCK_ENABLE: "true",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("SPRING_PROFILES_ACTIVE must be prod.");
    expect(result.errors).toContain("YUDAO_DB_URL must not point to localhost.");
    expect(result.errors).toContain("YUDAO_DB_USERNAME must not be root.");
    expect(result.errors).toContain("YUDAO_DB_PASSWORD must not use the default development password.");
    expect(result.errors).toContain("YUDAO_REDIS_HOST must not point to localhost.");
    expect(result.errors).toContain("YUDAO_REDIS_PORT must be a valid TCP port.");
    expect(result.errors).toContain("YUDAO_ADMIN_UI_URL must be an absolute https URL that is not localhost.");
    expect(result.errors).toContain("YUDAO_APP_UI_URL must be an absolute https URL that is not localhost.");
    expect(result.errors).toContain("YUDAO_PAY_ORDER_NOTIFY_URL must be an absolute https URL.");
    expect(result.errors).toContain("YUDAO_PAY_REFUND_NOTIFY_URL is required.");
    expect(result.errors).toContain("YUDAO_PAY_TRANSFER_NOTIFY_URL must be an absolute https URL.");
    expect(result.errors).toContain("YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY is required.");
    expect(result.errors).toContain("YUDAO_SECURITY_MOCK_ENABLE must not be true in production.");
  });

  it("rejects documentation domains in backend production endpoints", () => {
    const result = validateBackendProductionEnv({
      ...validBackendEnv,
      YUDAO_DB_URL: "jdbc:mysql://mysql.example.com:3306/oakved?useSSL=true",
      YUDAO_REDIS_HOST: "redis.example.com",
      YUDAO_ADMIN_UI_URL: "https://admin.example.com",
      YUDAO_APP_UI_URL: "https://shop.example.com",
      YUDAO_PAY_ORDER_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/order",
      YUDAO_PAY_REFUND_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/refund",
      YUDAO_PAY_TRANSFER_NOTIFY_URL: "https://api.example.com/admin-api/pay/notify/transfer",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "YUDAO_DB_URL must not use a documentation/example domain.",
        "YUDAO_REDIS_HOST must not use a documentation/example domain.",
        "YUDAO_ADMIN_UI_URL must not use a documentation/example domain.",
        "YUDAO_APP_UI_URL must not use a documentation/example domain.",
        "YUDAO_PAY_ORDER_NOTIFY_URL must not use a documentation/example domain.",
        "YUDAO_PAY_REFUND_NOTIFY_URL must not use a documentation/example domain.",
        "YUDAO_PAY_TRANSFER_NOTIFY_URL must not use a documentation/example domain.",
      ]),
    );
  });

  it("can validate a concrete backend env file path", async () => {
    const directory = mkdtempSync(join(tmpdir(), "oakved-backend-env-"));
    const envFile = join(directory, ".env.backend-production");
    writeFileSync(
      envFile,
      Object.entries(validBackendEnv)
        .map(([key, value]) => `${key}=${value}`)
        .join("\n"),
      "utf8",
    );

    try {
      const { readAndValidateBackendProductionEnv } = await import("../scripts/verify-backend-production-env.mjs");

      expect(readAndValidateBackendProductionEnv(envFile).ok).toBe(true);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("allows documented placeholders only for the checked-in example", () => {
    const exampleEnv = parseEnvFileContent(readSource("../.env.backend-production.example"));

    expect(validateBackendProductionEnv(exampleEnv, { allowPlaceholders: true }).ok).toBe(true);
    expect(validateBackendProductionEnv(exampleEnv).ok).toBe(false);
  });
});
