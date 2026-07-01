import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  parseLaunchSmokeEnvArgs,
  readAndValidateLaunchSmokeEnv,
  validateLaunchSmokeEnv,
} from "../scripts/verify-launch-smoke-env.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const smokeEnvExamplePath = new URL("../.env.launch-smoke.example", import.meta.url);

describe("launch smoke env readiness", () => {
  it("exposes a launch smoke env example and verification command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["verify:launch-smoke-env"]).toBe("node scripts/verify-launch-smoke-env.mjs");
    expect(existsSync(smokeEnvExamplePath)).toBe(true);
  });

  it("documents every live smoke variable in the example file", () => {
    const source = readFileSync(smokeEnvExamplePath, "utf8");
    const requiredKeys = [
      "YUDAO_SMOKE_BASE_URL",
      "YUDAO_SMOKE_TENANT_ID",
      "YUDAO_SMOKE_TOKEN",
      "YUDAO_ORDER_SMOKE_SKU_ID",
      "YUDAO_ORDER_SMOKE_CART_ID",
      "YUDAO_ORDER_SMOKE_ADDRESS_ID",
      "YUDAO_ORDER_SMOKE_COUNT",
      "YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE",
      "YUDAO_ORDER_SMOKE_RETURN_ORIGIN",
    ];

    for (const key of requiredKeys) {
      expect(source).toContain(`${key}=`);
    }
  });

  it("validates real smoke env values and rejects placeholders by default", () => {
    expect(
      validateLaunchSmokeEnv({
        YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
        YUDAO_SMOKE_TENANT_ID: "121",
        YUDAO_SMOKE_TOKEN: "launch-token",
        YUDAO_ORDER_SMOKE_SKU_ID: "5001",
        YUDAO_ORDER_SMOKE_CART_ID: "7001",
        YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
        YUDAO_ORDER_SMOKE_COUNT: "1",
        YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.oakved.example",
      }),
    ).toMatchObject({ ok: true, errors: [] });

    expect(
      validateLaunchSmokeEnv({
        YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
        YUDAO_SMOKE_TENANT_ID: "121",
        YUDAO_SMOKE_TOKEN: "<real-app-user-token>",
        YUDAO_ORDER_SMOKE_SKU_ID: "5001",
        YUDAO_ORDER_SMOKE_CART_ID: "7001",
        YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
        YUDAO_ORDER_SMOKE_COUNT: "1",
        YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
      }).ok,
    ).toBe(false);
  });

  it("can validate the checked-in example when placeholders are allowed", () => {
    const result = readAndValidateLaunchSmokeEnv(".env.launch-smoke.example", { allowPlaceholders: true });

    expect(result).toMatchObject({ ok: true, errors: [] });
  });

  it("parses env-file and allow-placeholders flags", () => {
    expect(parseLaunchSmokeEnvArgs(["--env-file", ".env.launch-smoke.example", "--allow-placeholders"])).toEqual({
      envFile: ".env.launch-smoke.example",
      allowPlaceholders: true,
    });
    expect(parseLaunchSmokeEnvArgs(["--env-file=.env.launch-smoke"])).toMatchObject({
      envFile: ".env.launch-smoke",
      allowPlaceholders: false,
    });
  });
});
