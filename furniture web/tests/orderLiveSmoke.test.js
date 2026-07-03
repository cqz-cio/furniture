import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

import {
  buildOrderLiveSmokeConfig,
  buildOrderLiveSmokePlan,
  parseOrderLiveSmokeArgs,
} from "../scripts/order-live-smoke.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
const orderLiveSmokeScriptPath = new URL("../scripts/order-live-smoke.mjs", import.meta.url);

describe("order live smoke gate", () => {
  const requiredRuntimeEnv = {
    YUDAO_ORDER_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api",
    YUDAO_SMOKE_TOKEN: "launch-token",
    YUDAO_ORDER_SMOKE_SKU_ID: "5001",
    YUDAO_ORDER_SMOKE_CART_ID: "7001",
    YUDAO_ORDER_SMOKE_ADDRESS_ID: "8101",
    YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.oakvedhome.com",
  };

  it("exposes a repeatable order live smoke command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["test:smoke:order-live"]).toBe("node scripts/order-live-smoke.mjs");
    expect(existsSync(orderLiveSmokeScriptPath)).toBe(true);
  });

  it("builds a settlement-only live smoke plan by default", () => {
    const options = parseOrderLiveSmokeArgs(["--env-file", ".env.production.example"]);
    const config = buildOrderLiveSmokeConfig(options, requiredRuntimeEnv);
    const plan = buildOrderLiveSmokePlan(config);

    expect(config).toMatchObject({
      baseUrl: "https://api.oakvedhome.com/app-api",
      tenantId: "121",
      token: "launch-token",
      skuId: "5001",
      cartId: "7001",
      addressId: "8101",
      count: "1",
      payChannelCode: "alipay_pc",
      createOrder: false,
    });
    expect(plan.map((step) => step.name)).toEqual(["order-settlement"]);
    expect(plan[0].path).toContain("/trade/order/settlement?");
    expect(plan[0].path).toContain("items%5B0%5D.skuId=5001");
    expect(plan[0].path).toContain("addressId=8101");
  });

  it("adds create-order and submit-payment steps only when explicitly enabled", () => {
    const options = parseOrderLiveSmokeArgs(["--env-file=.env.production.example", "--create-order"]);
    const config = buildOrderLiveSmokeConfig(options, requiredRuntimeEnv);
    const plan = buildOrderLiveSmokePlan(config, {
      orderId: 9101,
      payOrderId: 9201,
    });

    expect(config.createOrder).toBe(true);
    expect(plan.map((step) => step.name)).toEqual(["order-settlement", "order-create", "payment-submit"]);
    expect(plan[1]).toMatchObject({
      method: "POST",
      path: "/trade/order/create",
    });
    expect(plan[2]).toMatchObject({
      method: "POST",
      path: "/pay/order/submit",
      body: {
        id: 9201,
        channelCode: "alipay_pc",
        displayMode: "url",
        returnUrl: "https://shop.oakvedhome.com/account/orders?id=9101&payOrderId=9201",
      },
    });
  });

  it("rejects documentation or localhost order smoke URLs before touching live APIs", () => {
    const options = parseOrderLiveSmokeArgs(["--env-file", ".env.production.example"]);

    expect(() =>
      buildOrderLiveSmokeConfig(options, {
        ...requiredRuntimeEnv,
        YUDAO_ORDER_SMOKE_BASE_URL: "https://api.oakved.example/app-api",
      }),
    ).toThrow(/YUDAO_ORDER_SMOKE_BASE_URL must not use a documentation\/example domain/);
    expect(() =>
      buildOrderLiveSmokeConfig(options, {
        ...requiredRuntimeEnv,
        YUDAO_ORDER_SMOKE_BASE_URL: "http://localhost:48080/app-api",
      }),
    ).toThrow(/YUDAO_ORDER_SMOKE_BASE_URL must not point to localhost/);
    expect(() =>
      buildOrderLiveSmokeConfig(options, {
        ...requiredRuntimeEnv,
        YUDAO_ORDER_SMOKE_RETURN_ORIGIN: "https://shop.example.com",
      }),
    ).toThrow(/YUDAO_ORDER_SMOKE_RETURN_ORIGIN must not use a documentation\/example domain/);
    expect(() =>
      buildOrderLiveSmokeConfig(options, {
        ...requiredRuntimeEnv,
        YUDAO_ORDER_SMOKE_RETURN_URL: "http://127.0.0.1:4173/account/orders",
      }),
    ).toThrow(/YUDAO_ORDER_SMOKE_RETURN_URL must not point to localhost/);
  });

  it("requires a real return URL or return origin when live order creation is enabled", () => {
    const options = parseOrderLiveSmokeArgs(["--env-file=.env.production.example", "--create-order"]);
    const envWithoutReturnTarget = { ...requiredRuntimeEnv };
    delete envWithoutReturnTarget.YUDAO_ORDER_SMOKE_RETURN_ORIGIN;

    expect(() => buildOrderLiveSmokeConfig(options, envWithoutReturnTarget)).toThrow(
      /YUDAO_ORDER_SMOKE_RETURN_ORIGIN or YUDAO_ORDER_SMOKE_RETURN_URL is required/,
    );
  });

  it("requires a token and smoke cart identifiers before touching live order APIs", () => {
    const options = parseOrderLiveSmokeArgs(["--env-file", ".env.production.example"]);

    expect(() => buildOrderLiveSmokeConfig(options, { YUDAO_ORDER_SMOKE_BASE_URL: "https://api.oakvedhome.com/app-api" })).toThrow(
      /YUDAO_SMOKE_TOKEN/,
    );
    expect(() =>
      buildOrderLiveSmokeConfig(options, {
        ...requiredRuntimeEnv,
        YUDAO_SMOKE_TOKEN: "<real-app-user-token>",
      }),
    ).toThrow(/YUDAO_SMOKE_TOKEN must be a real live token/);
    expect(() => buildOrderLiveSmokeConfig(options, { ...requiredRuntimeEnv, YUDAO_ORDER_SMOKE_CART_ID: "" })).toThrow(
      /YUDAO_ORDER_SMOKE_CART_ID/,
    );
  });
});
