import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { parseEnvFileContent } from "./verify-production-env.mjs";

const DEFAULT_ENV_FILE = ".env.production";
const DEFAULT_COUNT = "1";
const DEFAULT_DELIVERY_TYPE = "1";

export const parseOrderLiveSmokeArgs = (argv = []) => {
  const options = {
    envFile: DEFAULT_ENV_FILE,
    createOrder: false,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--env-file") {
      options.envFile = argv[index + 1] || DEFAULT_ENV_FILE;
      index += 1;
    } else if (arg.startsWith("--env-file=")) {
      options.envFile = arg.slice("--env-file=".length);
    } else if (arg === "--create-order") {
      options.createOrder = true;
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

const firstValue = (...values) => values.find((value) => String(value || "").trim()) || "";

const required = (value, key) => {
  const normalized = String(value || "").trim();
  if (!normalized) throw new Error(`${key} is required for order live smoke.`);
  return normalized;
};

const isDocumentationDomain = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "example.com" || normalized.endsWith(".example.com") || normalized.endsWith(".example");
};

const isLocalhost = (hostname = "") => {
  const normalized = String(hostname || "").trim().toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "0.0.0.0" || normalized === "::1" || normalized === "[::1]";
};

const requireLaunchUrl = (value, key) => {
  const normalized = required(value, key).replace(/\/$/, "");
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

const optionalLaunchUrl = (value, key) => {
  const normalized = String(value || "").trim();
  return normalized ? requireLaunchUrl(normalized, key) : "";
};

const buildReturnUrl = (config, orderResult = {}) => {
  const explicitReturnUrl = String(config.returnUrl || "").trim();
  if (explicitReturnUrl) return explicitReturnUrl;

  const origin = String(config.returnOrigin || "").replace(/\/$/, "");
  const search = new URLSearchParams();
  search.set("id", String(orderResult.orderId || ""));
  if (orderResult.payOrderId) search.set("payOrderId", String(orderResult.payOrderId));
  return `${origin}/account/orders?${search.toString()}`;
};

export const buildOrderLiveSmokeConfig = (options = {}, runtimeEnv = process.env) => {
  const envFile = options.envFile || DEFAULT_ENV_FILE;
  const fileEnv = readEnvFile(envFile);
  const createOrder =
    options.createOrder === true || String(runtimeEnv.YUDAO_ORDER_SMOKE_CREATE_ORDER || "").toLowerCase() === "true";

  const returnOrigin = optionalLaunchUrl(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_RETURN_ORIGIN, fileEnv.YUDAO_ORDER_SMOKE_RETURN_ORIGIN), "YUDAO_ORDER_SMOKE_RETURN_ORIGIN");
  const returnUrl = optionalLaunchUrl(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_RETURN_URL, fileEnv.YUDAO_ORDER_SMOKE_RETURN_URL), "YUDAO_ORDER_SMOKE_RETURN_URL");

  if (createOrder && !returnOrigin && !returnUrl) {
    throw new Error("YUDAO_ORDER_SMOKE_RETURN_ORIGIN or YUDAO_ORDER_SMOKE_RETURN_URL is required for order live smoke.");
  }

  return {
    baseUrl: requireLaunchUrl(
      firstValue(
        runtimeEnv.YUDAO_ORDER_SMOKE_BASE_URL,
        runtimeEnv.YUDAO_SMOKE_BASE_URL,
        fileEnv.YUDAO_ORDER_SMOKE_BASE_URL,
        fileEnv.YUDAO_SMOKE_BASE_URL,
        fileEnv.VITE_YUDAO_APP_API_BASE,
      ),
      "YUDAO_ORDER_SMOKE_BASE_URL",
    ),
    tenantId: required(
      firstValue(
        runtimeEnv.YUDAO_ORDER_SMOKE_TENANT_ID,
        runtimeEnv.YUDAO_SMOKE_TENANT_ID,
        fileEnv.YUDAO_ORDER_SMOKE_TENANT_ID,
        fileEnv.YUDAO_SMOKE_TENANT_ID,
        fileEnv.VITE_YUDAO_APP_TENANT_ID,
      ),
      "YUDAO_ORDER_SMOKE_TENANT_ID",
    ),
    token: required(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_TOKEN, runtimeEnv.YUDAO_SMOKE_TOKEN, fileEnv.YUDAO_SMOKE_TOKEN), "YUDAO_SMOKE_TOKEN"),
    skuId: required(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_SKU_ID, fileEnv.YUDAO_ORDER_SMOKE_SKU_ID), "YUDAO_ORDER_SMOKE_SKU_ID"),
    cartId: required(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_CART_ID, fileEnv.YUDAO_ORDER_SMOKE_CART_ID), "YUDAO_ORDER_SMOKE_CART_ID"),
    addressId: required(
      firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_ADDRESS_ID, fileEnv.YUDAO_ORDER_SMOKE_ADDRESS_ID),
      "YUDAO_ORDER_SMOKE_ADDRESS_ID",
    ),
    count: required(firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_COUNT, fileEnv.YUDAO_ORDER_SMOKE_COUNT, DEFAULT_COUNT), "YUDAO_ORDER_SMOKE_COUNT"),
    deliveryType: required(
      firstValue(runtimeEnv.YUDAO_ORDER_SMOKE_DELIVERY_TYPE, fileEnv.YUDAO_ORDER_SMOKE_DELIVERY_TYPE, DEFAULT_DELIVERY_TYPE),
      "YUDAO_ORDER_SMOKE_DELIVERY_TYPE",
    ),
    payChannelCode: required(
      firstValue(
        runtimeEnv.YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE,
        fileEnv.YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE,
        fileEnv.VITE_YUDAO_PAY_CHANNEL_CODE,
      ),
      "YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE",
    ),
    returnOrigin,
    returnUrl,
    createOrder,
  };
};

const buildSettlementPath = (config) => {
  const search = new URLSearchParams();
  search.append("items[0].skuId", config.skuId);
  search.append("items[0].count", config.count);
  search.append("items[0].cartId", config.cartId);
  search.append("pointStatus", "false");
  search.append("deliveryType", config.deliveryType);
  search.append("addressId", config.addressId);
  return `/trade/order/settlement?${search.toString()}`;
};

const buildAddressVerificationAudit = () => ({
  source: "backend-address-verification",
  addressSource: "saved",
  status: "verified",
  reason: "order-live-smoke",
  choice: "original",
  deliverable: true,
  confirmedAt: new Date(0).toISOString(),
  providerStatus: "live",
  selectedAddress: {
    street: "Smoke saved address",
    city: "Smoke City",
    state: "CA",
    postalCode: "94105",
  },
});

export const buildOrderLiveSmokePlan = (config, orderResult = {}) => {
  const plan = [
    {
      name: "order-settlement",
      method: "GET",
      path: buildSettlementPath(config),
    },
  ];

  if (!config.createOrder) return plan;

  plan.push({
    name: "order-create",
    method: "POST",
    path: "/trade/order/create",
    body: {
      items: [{ skuId: config.skuId, count: Number(config.count), cartId: config.cartId }],
      pointStatus: false,
      deliveryType: Number(config.deliveryType),
      addressId: config.addressId,
      addressVerification: buildAddressVerificationAudit(),
      remark: "Order live smoke",
    },
  });

  if (orderResult.payOrderId) {
    plan.push({
      name: "payment-submit",
      method: "POST",
      path: "/pay/order/submit",
      body: {
        id: orderResult.payOrderId,
        channelCode: config.payChannelCode,
        channelExtras: {},
        displayMode: "url",
        returnUrl: buildReturnUrl(config, orderResult),
      },
    });
  }

  return plan;
};

const requestLive = async (config, step) => {
  const response = await fetch(`${config.baseUrl}${step.path}`, {
    method: step.method,
    headers: {
      "Content-Type": "application/json",
      "tenant-id": config.tenantId,
      Authorization: `Bearer ${config.token}`,
    },
    ...(step.body ? { body: JSON.stringify(step.body) } : {}),
  });
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.code !== 0) {
    throw new Error(`Order live smoke failed: ${step.method} ${step.path} ${JSON.stringify(payload)}`);
  }

  return payload.data;
};

export const runOrderLiveSmoke = async (options = {}) => {
  const config = buildOrderLiveSmokeConfig(options);
  const settlementStep = buildOrderLiveSmokePlan(config)[0];
  console.log(`\n==> ${settlementStep.name}`);
  await requestLive(config, settlementStep);

  if (!config.createOrder) {
    return { ok: true, createdOrder: false };
  }

  const createStep = buildOrderLiveSmokePlan(config).find((step) => step.name === "order-create");
  console.log(`\n==> ${createStep.name}`);
  const orderData = await requestLive(config, createStep);
  const orderResult = {
    orderId: orderData?.id || orderData?.orderId,
    payOrderId: orderData?.payOrderId,
  };
  if (!orderResult.payOrderId) {
    throw new Error("Order live smoke create step did not return payOrderId.");
  }

  const paymentStep = buildOrderLiveSmokePlan(config, orderResult).find((step) => step.name === "payment-submit");
  console.log(`\n==> ${paymentStep.name}`);
  await requestLive(config, paymentStep);

  return { ok: true, createdOrder: true, orderId: orderResult.orderId, payOrderId: orderResult.payOrderId };
};

const isCli = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isCli) {
  try {
    const options = parseOrderLiveSmokeArgs(process.argv.slice(2));
    const result = await runOrderLiveSmoke(options);
    console.log(
      result.createdOrder
        ? `\nOrder live smoke passed: orderId=${result.orderId}, payOrderId=${result.payOrderId}`
        : "\nOrder live smoke passed: settlement only",
    );
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
