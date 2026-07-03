import { existsSync, readFileSync } from "node:fs";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildRealAccountReadinessConfig,
  buildRealAccountReadinessPlan,
  evaluateRealAccountReadiness,
  parseRealAccountReadinessArgs,
  runRealAccountReadinessSmoke,
} from "../scripts/real-account-readiness-smoke.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const smokeScriptPath = new URL("../scripts/real-account-readiness-smoke.mjs", import.meta.url);

describe("real account readiness smoke gate", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("exposes a repeatable real account readiness command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:smoke:real-account"]).toBe("node scripts/real-account-readiness-smoke.mjs");
    expect(existsSync(smokeScriptPath)).toBe(true);
  });

  it("builds a non-mutating live readiness plan by default", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const config = buildRealAccountReadinessConfig(options, {
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_ORDER_SMOKE_CART_ID: "301",
      YUDAO_ORDER_SMOKE_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    });
    const plan = buildRealAccountReadinessPlan(config);

    expect(config).toMatchObject({
      baseUrl: "https://api.oakvedhome.com/app-api",
      tenantId: "121",
      token: "launch-token",
      adminBaseUrl: "https://api.oakvedhome.com/admin-api",
      adminTenantId: "121",
      adminToken: "admin-token",
      spuId: "5001",
      cartId: "301",
      skuId: "20",
      addressId: "501",
      wishlistSpuId: "10",
      wishlistSkuId: "20",
      membershipStatus: "active_annual",
      membershipPlanCode: "annual_membership",
      giftRegistryItemSpuId: "10",
      giftRegistryItemSkuId: "20",
      orderId: "7001",
      userId: "1",
      giftRegistryPublicCode: "reg-100",
      tradeId: "RH-TRADE-10086",
      tradeEmail: "designer@oakvedhome.com",
      checkOrder: false,
    });
    expect(plan.map((step) => step.name)).toEqual([
      "product-catalog-page",
      "product-detail",
      "cart-list",
      "order-page",
      "member-profile",
      "member-address-list",
      "wishlist-page",
      "membership-profile",
      "gift-registry-my",
      "membership-admin-page",
      "gift-registry-admin-page",
      "trade-application-admin-page",
    ]);
    expect(plan.every((step) => step.method === "GET")).toBe(true);
    expect(plan.find((step) => step.name === "membership-admin-page")?.path).toContain("userId=1");
    expect(plan.find((step) => step.name === "gift-registry-admin-page")?.path).toContain("publicCode=reg-100");
    expect(plan.find((step) => step.name === "trade-application-admin-page")?.path).toContain("primaryEmail=designer%40oakvedhome.com");
  });

  it("requires live account and admin tokens before touching readiness APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);

    expect(() =>
      buildRealAccountReadinessConfig(options, {
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_TOKEN/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        YUDAO_SMOKE_TOKEN: "launch-token",
        YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
        YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
        YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
        YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_ADMIN_TOKEN/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
        YUDAO_SMOKE_TOKEN: "launch-token",
        YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
        YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_USER_ID/);
  });

  it("rejects documentation domains before touching real-account smoke APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://admin-api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must not use a documentation\/example domain/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.example.com/admin-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must not use a documentation\/example domain/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@example.com",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL must not use a documentation\/example domain/);
  });

  it("rejects invalid Trade email values before touching real-account smoke APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    for (const value of ["designer", "designer@", "@oakvedhome.com"]) {
      expect(() =>
        buildRealAccountReadinessConfig(options, {
          ...baseline,
          YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: value,
        }),
      ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL must be a valid email address/);
    }
  });

  it("rejects non-http real-account smoke base URLs before touching APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "api.oakvedhome.com/app-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL must be an absolute http\(s\) URL/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "ftp://api.oakvedhome.com/admin-api",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL must be an absolute http\(s\) URL/);
  });

  it("rejects placeholder values before touching real-account smoke APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "<real-app-user-token>",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_TOKEN must be replaced with a real account smoke value/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "<real-user-id>",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_USER_ID must be replaced with a real account smoke value/);
    expect(() =>
      buildRealAccountReadinessConfig(options, {
        ...baseline,
        YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "replace-me",
      }),
    ).toThrow(/YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE must be replaced with a real account smoke value/);
  });

  it("rejects non-positive numeric seeded identifiers before touching real-account smoke APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_SMOKE_CART_ID: "301",
      YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    for (const [key, value] of [
      ["YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID", "abc"],
      ["YUDAO_REAL_ACCOUNT_SMOKE_USER_ID", "0"],
      ["YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID", "-1"],
      ["YUDAO_REAL_ACCOUNT_SMOKE_CART_ID", "cart-301"],
      ["YUDAO_REAL_ACCOUNT_SMOKE_SKU_ID", "20.5"],
      ["YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID", "tenant"],
    ]) {
      expect(() =>
        buildRealAccountReadinessConfig(options, {
          ...baseline,
          [key]: value,
        }),
      ).toThrow(new RegExp(`${key} must be a positive integer`));
    }
  });

  it("requires complete seeded module identifiers before touching readiness APIs", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", ".env.production.example"]);
    const baseline = {
      YUDAO_SMOKE_TOKEN: "launch-token",
      YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID: "5001",
      YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID: "501",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS: "active_annual",
      YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE: "annual_membership",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID: "10",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID: "20",
      YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID: "7001",
      YUDAO_REAL_ACCOUNT_SMOKE_USER_ID: "1",
      YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE: "reg-100",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID: "RH-TRADE-10086",
      YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL: "designer@oakvedhome.com",
      YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
      YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL: "https://api.oakvedhome.com/admin-api",
      YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID: "121",
      YUDAO_REAL_ACCOUNT_ADMIN_TOKEN: "admin-token",
    };

    for (const key of [
      "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID",
      "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID",
    ]) {
      const env = { ...baseline };
      delete env[key];
      expect(() => buildRealAccountReadinessConfig(options, env)).toThrow(
        new RegExp(`${key} is required for real account readiness smoke`),
      );
    }
  });

  it("loads the dedicated real-account smoke token from the env file", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", "tests/fixtures/real-account-smoke-only.env"]);

    expect(buildRealAccountReadinessConfig(options, {})).toMatchObject({
      baseUrl: "https://api.oakvedhome.com/app-api",
      tenantId: "121",
      token: "real-account-file-token",
      spuId: "5001",
      addressId: "501",
      wishlistSpuId: "10",
      wishlistSkuId: "20",
      membershipStatus: "active_annual",
      membershipPlanCode: "annual_membership",
      giftRegistryItemSpuId: "10",
      giftRegistryItemSkuId: "20",
    });
  });

  it("honors the real-account order detail check flag from the env file", () => {
    const options = parseRealAccountReadinessArgs(["--env-file", "tests/fixtures/real-account-smoke-only.env"]);
    const config = buildRealAccountReadinessConfig(options, {});
    const plan = buildRealAccountReadinessPlan(config);

    expect(config.checkOrder).toBe(true);
    expect(plan.map((step) => step.name)).toContain("order-detail");
    expect(plan.find((step) => step.name === "order-detail")?.path).toBe("/trade/order/get-detail?id=7001");
  });

  it("evaluates product, cart, account, and optional module readiness from live payloads", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
    );

    expect(evaluation.ok).toBe(true);
    expect(evaluation.cartReadiness.ready).toBe(true);
    expect(evaluation.moduleSnapshot).toMatchObject({
      productCatalog: "ready",
      cart: "ready",
      checkout: "ready",
      orders: "ready",
      billing: "ready",
      accountProfile: "ready",
      addressBook: "ready",
      wishlist: "ready",
      membership: "ready",
      giftRegistry: "ready",
      tradeProgram: "ready",
    });
  });

  it("does not mark optional modules ready when optional readiness endpoints fail", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [], total: 0 },
        "member-profile": { id: 1 },
        "member-address-list": [],
        "wishlist-page": { list: [], total: 0 },
        "membership-profile": { optionalUnavailable: true },
        "gift-registry-my": { optionalUnavailable: true },
      },
      "token",
    );

    expect(evaluation.moduleSnapshot).toMatchObject({
      membership: "blocked",
      giftRegistry: "blocked",
    });
    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(expect.arrayContaining(["module-membership-blocked", "module-giftRegistry-blocked"]));
  });

  it("fails when admin management pages cannot see seeded real-account records", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [], total: 0 },
        "gift-registry-admin-page": { list: [], total: 0 },
        "trade-application-admin-page": { list: [], total: 0 },
      },
      "token",
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(
      expect.arrayContaining([
        "admin-membership-page-empty",
        "admin-gift-registry-page-empty",
        "admin-trade-application-page-empty",
      ]),
    );
  });

  it("fails when admin management pages return records from a different seeded account", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active" },
        "gift-registry-my": { id: 100, userId: 1, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 91, userId: 2 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 101, userId: 2, publicCode: "reg-200" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 301, tradeId: "RH-TRADE-99999" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(
      expect.arrayContaining([
        "admin-membership-seeded-user-missing",
        "admin-gift-registry-seeded-record-missing",
        "admin-trade-application-seeded-record-missing",
      ]),
    );
  });

  it("fails when the admin trade application row belongs to a different seeded email", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active" },
        "gift-registry-my": { id: 100, userId: 1, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": {
          list: [{ id: 300, tradeId: "RH-TRADE-10086", primaryEmail: "other@oakvedhome.com" }],
          total: 1,
        },
      },
      "token",
      {
        userId: "1",
        cartId: "301",
        skuId: "20",
        addressId: "501",
        orderId: "7001",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
        tradeEmail: "designer@oakvedhome.com",
        membershipStatus: "active_annual",
        membershipPlanCode: "annual_membership",
        giftRegistryItemSpuId: "10",
        giftRegistryItemSkuId: "20",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("admin-trade-application-seeded-email-missing");
  });

  it("fails when the admin Gift Registry detail does not contain the seeded real product item", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active_annual", planCode: "annual_membership" },
        "gift-registry-my": {
          id: 100,
          userId: 1,
          publicCode: "reg-100",
          status: "active",
          items: [{ id: 501, spuId: 10, skuId: 20 }],
        },
        "membership-admin-page": {
          list: [{ id: 90, userId: 1, status: "active_annual", planCode: "annual_membership" }],
          total: 1,
        },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "gift-registry-admin-detail": {
          id: 100,
          userId: 1,
          publicCode: "reg-100",
          items: [{ id: 502, spuId: 11, skuId: 21 }],
        },
        "trade-application-admin-page": {
          list: [{ id: 300, tradeId: "RH-TRADE-10086", primaryEmail: "designer@oakvedhome.com" }],
          total: 1,
        },
      },
      "token",
      {
        userId: "1",
        cartId: "301",
        skuId: "20",
        addressId: "501",
        orderId: "7001",
        membershipStatus: "active_annual",
        membershipPlanCode: "annual_membership",
        giftRegistryPublicCode: "reg-100",
        giftRegistryItemSpuId: "10",
        giftRegistryItemSkuId: "20",
        tradeId: "RH-TRADE-10086",
        tradeEmail: "designer@oakvedhome.com",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("admin-gift-registry-detail-seeded-item-missing");
  });

  it("requests admin Gift Registry detail after finding the seeded registry row", async () => {
    const payloadByPath = new Map([
      ["/product/spu/page?pageNo=1&pageSize=1", { list: [{ id: 10 }], total: 1 }],
      ["/product/spu/get-detail?id=5001", { id: 5001, skus: [{ id: 20 }] }],
      ["/trade/cart/list", { validList: [{ id: 301, count: 1, spu: { id: 10 }, sku: { id: 20 } }] }],
      ["/trade/order/page?pageNo=1&pageSize=1", { list: [{ id: 7001, payOrderId: 9001 }], total: 1 }],
      ["/trade/order/get-detail?id=7001", { id: 7001, payOrderId: 9001 }],
      ["/member/user/get", { id: 1, tradeId: "RH-TRADE-10086" }],
      ["/member/address/list", [{ id: 501, name: "Launch Buyer" }]],
      ["/product/favorite/page?pageNo=1&pageSize=1", { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 }],
      ["/member/membership/get", { id: 90, userId: 1, status: "active_annual", planCode: "annual_membership" }],
      [
        "/member/gift-registry/my",
        {
          id: 100,
          userId: 1,
          publicCode: "reg-100",
          status: "active",
          items: [{ id: 501, spuId: 10, skuId: 20 }],
        },
      ],
      [
        "/member/membership/page?pageNo=1&pageSize=1&userId=1",
        { list: [{ id: 90, userId: 1, status: "active_annual", planCode: "annual_membership" }], total: 1 },
      ],
      [
        "/member/gift-registry/page?pageNo=1&pageSize=1&userId=1&publicCode=reg-100",
        { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
      ],
      [
        "/member/gift-registry/get?id=100",
        { id: 100, userId: 1, publicCode: "reg-100", items: [{ id: 501, spuId: 10, skuId: 20 }] },
      ],
      [
        "/member/trade-application/page?pageNo=1&pageSize=1&primaryEmail=designer%40oakvedhome.com",
        { list: [{ id: 300, tradeId: "RH-TRADE-10086", primaryEmail: "designer@oakvedhome.com" }], total: 1 },
      ],
    ]);
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      const parsedUrl = new URL(String(url));
      const path = parsedUrl.pathname.replace(/^\/(?:app-api|admin-api)/, "") + parsedUrl.search;
      return {
        ok: true,
        json: async () => ({ code: 0, data: payloadByPath.get(path) }),
      };
    });

    const result = await runRealAccountReadinessSmoke({ envFile: "tests/fixtures/real-account-smoke-only.env" });

    expect(result.ok).toBe(true);
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toContain(
      "https://api.oakvedhome.com/admin-api/member/gift-registry/get?id=100",
    );
  });

  it("returns seeded account identifiers for launch evidence", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active" },
        "gift-registry-my": { id: 100, userId: 1, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": {
          list: [{ id: 300, tradeId: "RH-TRADE-10086", primaryEmail: "designer@oakvedhome.com" }],
          total: 1,
        },
      },
      "token",
      {
        userId: "1",
        cartId: "301",
        skuId: "20",
        addressId: "501",
        orderId: "7001",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
        tradeEmail: "designer@oakvedhome.com",
        membershipStatus: "active_annual",
        membershipPlanCode: "annual_membership",
        giftRegistryItemSpuId: "10",
        giftRegistryItemSkuId: "20",
      },
    );

    expect(evaluation.seededAccount).toEqual({
      userId: "1",
      cartId: "301",
      skuId: "20",
      addressId: "501",
      orderId: "7001",
      giftRegistryPublicCode: "reg-100",
      tradeId: "RH-TRADE-10086",
      tradeEmail: "designer@oakvedhome.com",
      membershipStatus: "active_annual",
      membershipPlanCode: "annual_membership",
      giftRegistryItemSpuId: "10",
      giftRegistryItemSkuId: "20",
    });
  });

  it("fails when the app token account does not match seeded user and registry identifiers", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 2, tradeId: "RH-TRADE-99999" },
        "member-address-list": [{ id: 501, name: "Other Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 2, status: "active" },
        "gift-registry-my": { id: 100, userId: 2, publicCode: "reg-200", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(
      expect.arrayContaining([
        "app-profile-seeded-user-mismatch",
        "app-gift-registry-seeded-record-mismatch",
        "app-trade-seeded-id-mismatch",
      ]),
    );
  });

  it("fails when the app membership profile belongs to another seeded user", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 2, status: "active_annual", planCode: "annual_membership" },
        "gift-registry-my": { id: 100, userId: 1, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1, status: "active_annual", planCode: "annual_membership" }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("app-membership-seeded-user-mismatch");
  });

  it("fails when the app gift registry belongs to another seeded user", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active" },
        "gift-registry-my": { id: 100, userId: 2, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("app-gift-registry-seeded-user-mismatch");
  });

  it("fails when seeded membership status or plan differs between app and admin data", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "inactive", planCode: "monthly_membership" },
        "gift-registry-my": { id: 100, userId: 1, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1, status: "inactive", planCode: "monthly_membership" }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
        membershipStatus: "active_annual",
        membershipPlanCode: "annual_membership",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(
      expect.arrayContaining([
        "app-membership-seeded-status-mismatch",
        "app-membership-seeded-plan-mismatch",
        "admin-membership-seeded-status-mismatch",
        "admin-membership-seeded-plan-mismatch",
      ]),
    );
  });

  it("fails when the app gift registry does not contain the seeded real product item", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, userId: 1, status: "active" },
        "gift-registry-my": {
          id: 100,
          userId: 1,
          publicCode: "reg-100",
          status: "active",
          items: [{ id: 501, spuId: 11, skuId: 21 }],
        },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        giftRegistryItemSpuId: "10",
        giftRegistryItemSkuId: "20",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("app-gift-registry-seeded-item-missing");
  });

  it("fails when order detail checking does not return the seeded order", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "order-detail": { id: 7002, payOrderId: 9002 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        checkOrder: true,
        orderId: "7001",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("order-detail-seeded-order-missing");
  });

  it("fails when order detail exists but the order center page does not list the seeded order", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7002, payOrderId: 9002 }], total: 1 },
        "order-detail": { id: 7001, payOrderId: 9001 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        checkOrder: true,
        orderId: "7001",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("order-page-seeded-order-missing");
  });

  it("fails when the order center page does not list the seeded order without detail checking", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7002, payOrderId: 9002 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        orderId: "7001",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("order-page-seeded-order-missing");
  });

  it("passes order detail checking when the seeded order detail is returned", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "order-detail": { id: 7001, payOrderId: 9001 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        checkOrder: true,
        orderId: "7001",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(true);
    expect(evaluation.failures).not.toContain("order-detail-seeded-order-missing");
  });

  it("does not mark persisted account modules ready when seeded datasets are empty", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [], total: 0 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [],
        "wishlist-page": { list: [], total: 0 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
      },
      "token",
    );

    expect(evaluation.moduleSnapshot).toMatchObject({
      orders: "blocked",
      billing: "blocked",
      addressBook: "blocked",
      wishlist: "blocked",
    });
    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(
      expect.arrayContaining([
        "module-orders-blocked",
        "module-billing-blocked",
        "module-addressBook-blocked",
        "module-wishlist-blocked",
      ]),
    );
  });

  it("does not mark optional modules ready when live endpoints return no persisted record", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [], total: 0 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [],
        "wishlist-page": { list: [], total: 0 },
        "membership-profile": null,
        "gift-registry-my": null,
      },
      "token",
    );

    expect(evaluation.moduleSnapshot).toMatchObject({
      membership: "blocked",
      giftRegistry: "blocked",
    });
    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toEqual(expect.arrayContaining(["module-membership-blocked", "module-giftRegistry-blocked"]));
  });

  it("fails when live cart rows cannot create a Yudao checkout", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "cart-list": {
          validList: [
            {
              id: "",
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [], total: 0 },
        "member-profile": { id: 1 },
        "member-address-list": [],
        "wishlist-page": { list: [], total: 0 },
      },
      "token",
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("item-1:missing-cart-id");
  });

  it("fails when the live cart does not contain the seeded cart row", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 302,
              count: 1,
              spu: { id: 10 },
              sku: { id: 21 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        cartId: "301",
        skuId: "20",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("cart-seeded-row-missing");
  });

  it("fails when product detail does not return the seeded product", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 11, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        spuId: "10",
        skuId: "20",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("product-detail-seeded-product-missing");
  });

  it("fails when product detail does not include the seeded SKU", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 21 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        spuId: "10",
        skuId: "20",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("product-detail-seeded-sku-missing");
  });

  it("fails when the address book does not contain the seeded address", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 502, name: "Other Address" }],
        "wishlist-page": { list: [{ id: 601, spuId: 10, skuId: 20 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        addressId: "501",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("address-book-seeded-address-missing");
  });

  it("fails when the wishlist page does not contain the seeded favorite row", () => {
    const evaluation = evaluateRealAccountReadiness(
      {
        "product-catalog-page": { list: [{ id: 10 }], total: 1 },
        "product-detail": { id: 10, skus: [{ id: 20 }] },
        "cart-list": {
          validList: [
            {
              id: 301,
              count: 1,
              spu: { id: 10 },
              sku: { id: 20 },
            },
          ],
        },
        "order-page": { list: [{ id: 7001, payOrderId: 9001 }], total: 1 },
        "member-profile": { id: 1, tradeId: "RH-TRADE-10086" },
        "member-address-list": [{ id: 501, name: "Launch Buyer" }],
        "wishlist-page": { list: [{ id: 601, spuId: 11, skuId: 21 }], total: 1 },
        "membership-profile": { id: 90, status: "active" },
        "gift-registry-my": { id: 100, publicCode: "reg-100", status: "active" },
        "membership-admin-page": { list: [{ id: 90, userId: 1 }], total: 1 },
        "gift-registry-admin-page": { list: [{ id: 100, userId: 1, publicCode: "reg-100" }], total: 1 },
        "trade-application-admin-page": { list: [{ id: 300, tradeId: "RH-TRADE-10086" }], total: 1 },
      },
      "token",
      {
        wishlistSpuId: "10",
        wishlistSkuId: "20",
        userId: "1",
        giftRegistryPublicCode: "reg-100",
        tradeId: "RH-TRADE-10086",
      },
    );

    expect(evaluation.ok).toBe(false);
    expect(evaluation.failures).toContain("wishlist-seeded-row-missing");
  });
});
