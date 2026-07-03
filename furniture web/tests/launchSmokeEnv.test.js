import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  parseLaunchSmokeEnvArgs,
  readAndValidateLaunchSmokeEnv,
  validateLaunchSmokeEnv,
} from "../scripts/verify-launch-smoke-env.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const smokeEnvExamplePath = new URL("../.env.launch-smoke.example", import.meta.url);

const createValidLaunchSmokeEnv = (overrides = {}) => ({
  YUDAO_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
  YUDAO_SMOKE_TENANT_ID: "121",
  YUDAO_SMOKE_TOKEN: "launch-token",
  YUDAO_ORDER_SMOKE_SKU_ID: "5001",
  YUDAO_ORDER_SMOKE_CART_ID: "7001",
  YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
  YUDAO_ORDER_SMOKE_COUNT: "1",
  YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
  YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.oakvedhome.com",
  YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
  YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
  YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
  YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
  YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "8101",
  YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "5001",
  YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "6001",
  YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
  YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
  YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "5001",
  YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "6001",
  YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "9001",
  YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
  YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
  YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
  YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
  YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER: "true",
  YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
  YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
  YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
  ...overrides,
});

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
      "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL",
      "YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_TOKEN",
      "YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_USER_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE",
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL",
      "YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER",
      "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL",
      "YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID",
      "YUDAO_REAL_ACCOUNT_ADMIN_TOKEN",
    ];

    for (const key of requiredKeys) {
      expect(source).toContain(`${key}=`);
    }
  });

  it("validates real smoke env values and rejects placeholders by default", () => {
    expect(validateLaunchSmokeEnv(createValidLaunchSmokeEnv())).toMatchObject({ ok: true, errors: [] });

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

    expect(
      validateLaunchSmokeEnv(
        createValidLaunchSmokeEnv({
          YUDAO_SMOKE_TOKEN: "your-token",
          YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "your-admin-token",
        }),
      ).errors,
    ).toEqual(
      expect.arrayContaining([
        "YUDAO_SMOKE_TOKEN must be replaced with a real launch smoke value.",
        "YUDAO_REAL_ACCOUNT_ADMIN_TOKEN must be replaced with a real launch smoke value.",
      ]),
    );
  });

  it("requires an explicit boolean real-account order check flag", () => {
    const missing = createValidLaunchSmokeEnv();
    delete missing.YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER;

    expect(validateLaunchSmokeEnv(missing).errors).toContain("YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER is required.");
    expect(validateLaunchSmokeEnv(createValidLaunchSmokeEnv({ YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER: "maybe" })).errors).toContain(
      "YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER must be true or false.",
    );
  });

  it("requires explicit real account smoke values for the launch gate", () => {
    const result = validateLaunchSmokeEnv({
      YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      YUDAO_SMOKE_TENANT_ID: "121",
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_ORDER_SMOKE_SKU_ID: "5001",
      YUDAO_ORDER_SMOKE_CART_ID: "7001",
      YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
      YUDAO_ORDER_SMOKE_COUNT: "1",
      YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "YUDAO_REAL_ACCOUNT_SMOKE_TOKEN is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_USER_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID is required.",
        "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL is required.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL is required.",
        "YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID is required.",
        "YUDAO_REAL_ACCOUNT_ADMIN_TOKEN is required.",
      ]),
    );
  });

  it("rejects an example.com trade email for real-account smoke by default", () => {
    const result = validateLaunchSmokeEnv({
      YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      YUDAO_SMOKE_TENANT_ID: "121",
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_ORDER_SMOKE_SKU_ID: "5001",
      YUDAO_ORDER_SMOKE_CART_ID: "7001",
      YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
      YUDAO_ORDER_SMOKE_COUNT: "1",
      YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "8101",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "9001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@example.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakved.example/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL must not use example.com.");
  });

  it("rejects invalid trade email values for real-account smoke by default", () => {
    const result = validateLaunchSmokeEnv({
      YUDAO_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_SMOKE_TENANT_ID: "121",
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_ORDER_SMOKE_SKU_ID: "5001",
      YUDAO_ORDER_SMOKE_CART_ID: "7001",
      YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
      YUDAO_ORDER_SMOKE_COUNT: "1",
      YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "8101",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "9001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toContain("YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL must be a valid email address.");
  });

  it("rejects invalid optional dedicated real-account cart and SKU identifiers", () => {
    const result = validateLaunchSmokeEnv(
      createValidLaunchSmokeEnv({
        YUDAO_REAL_ACCOUNT_SMOKE_CART_ID: "cart-7001",
        YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID: "0",
      }),
    );

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "YUDAO_REAL_ACCOUNT_SMOKE_CART_ID must be a positive integer.",
        "YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID must be a positive integer.",
      ]),
    );
  });

  it("rejects documentation domains in launch smoke URLs by default", () => {
    const result = validateLaunchSmokeEnv({
      YUDAO_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      YUDAO_SMOKE_TENANT_ID: "121",
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_ORDER_SMOKE_SKU_ID: "5001",
      YUDAO_ORDER_SMOKE_CART_ID: "7001",
      YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
      YUDAO_ORDER_SMOKE_COUNT: "1",
      YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE: "alipay_pc",
      YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.example.com",
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "8101",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "6001",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "9001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.example.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    });

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "YUDAO_SMOKE_BASE_URL must not use a documentation/example domain.",
        "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must not use a documentation/example domain.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must not use a documentation/example domain.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must not use a documentation/example domain.",
      ]),
    );
  });

  it("rejects localhost URLs in launch smoke values by default", () => {
    const result = validateLaunchSmokeEnv(
      createValidLaunchSmokeEnv({
        YUDAO_SMOKE_BASE_URL: "http://localhost:48080/app-api",
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "http://127.0.0.1:4173",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "http://0.0.0.0:48080/app-api",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "http://[::1]:48080/admin-api",
      }),
    );

    expect(result.ok).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "YUDAO_SMOKE_BASE_URL must not point to localhost.",
        "YUDAO_ORDER_SMOKE_RETURN_ORIGIN must not point to localhost.",
        "YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must not point to localhost.",
        "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must not point to localhost.",
      ]),
    );
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
