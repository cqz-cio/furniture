import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { createLaunchEvidenceBundle } from "../scripts/create-launch-evidence.mjs";
import {
  auditInitialLaunchReadiness,
  parseInitialLaunchReadinessArgs,
} from "../scripts/audit-initial-launch-readiness.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

const productionEnv = `VITE_YUDAO_APP_API_BASE=https://api.oakvedhome.com/app-api
VITE_YUDAO_APP_TENANT_ID=121
VITE_YUDAO_US_DEFAULT_AREA_ID=100200
VITE_YUDAO_PAY_CHANNEL_CODE=alipay_pc
VITE_ADDRESS_VERIFICATION_PATH=/member/address/verify
VITE_ADDRESS_VERIFICATION_STATUS_PATH=/member/address/verification-status
VITE_SHOW_AUTH_TOKEN_PANEL=false
`;

const smokeEnv = `YUDAO_SMOKE_BASE_URL=https://api.oakvedhome.com/app-api
YUDAO_SMOKE_TENANT_ID=121
YUDAO_SMOKE_TOKEN=launch-token
YUDAO_ORDER_SMOKE_SKU_ID=5001
YUDAO_ORDER_SMOKE_CART_ID=7001
YUDAO_ORDER_SMOKE_ADDRESS_ID=8101
YUDAO_ORDER_SMOKE_COUNT=1
YUDAO_ORDER_SMOKE_PAY_CHANNEL_CODE=alipay_pc
YUDAO_ORDER_SMOKE_RETURN_ORIGIN=https://shop.oakvedhome.com
YUDAO_ORDER_SMOKE_CREATE_ORDER=false
YUDAO_REAL_ACCOUNT_SMOKE_BASE_URL=https://api.oakvedhome.com/app-api
YUDAO_REAL_ACCOUNT_SMOKE_TENANT_ID=121
YUDAO_REAL_ACCOUNT_SMOKE_TOKEN=launch-token
YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID=5001
YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID=8101
YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID=5001
YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID=6001
YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS=active_annual
YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE=annual_membership
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID=5001
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID=6001
YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID=9001
YUDAO_REAL_ACCOUNT_SMOKE_USER_ID=1
YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE=reg-100
YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID=RH-TRADE-10086
YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL=designer@oakvedhome.com
YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER=true
YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL=https://api.oakvedhome.com/admin-api
YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID=121
YUDAO_REAL_ACCOUNT_ADMIN_TOKEN=admin-token
`;

const backendEnv = `SPRING_PROFILES_ACTIVE=prod
YUDAO_DB_URL=jdbc:mysql://mysql.internal:3306/oakved?useSSL=true&serverTimezone=Asia/Shanghai
YUDAO_DB_USERNAME=oakved_app
YUDAO_DB_PASSWORD=replace-with-strong-password
YUDAO_REDIS_HOST=redis.internal
YUDAO_REDIS_PORT=6379
YUDAO_ADMIN_UI_URL=https://admin.oakvedhome.com
YUDAO_APP_UI_URL=https://shop.oakvedhome.com
YUDAO_PAY_ORDER_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/order
YUDAO_PAY_REFUND_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/refund
YUDAO_PAY_TRANSFER_NOTIFY_URL=https://api.oakvedhome.com/admin-api/pay/notify/transfer
YUDAO_GOOGLE_ADDRESS_VALIDATION_API_KEY=replace-with-real-google-address-validation-key
`;

const successOutputByFile = {
  "production-env.txt": "Production env check passed: .env.production\n",
  "launch-smoke-env.txt": "Launch smoke env check passed: .env.launch-smoke\n",
  "backend-production-env.txt": "Backend production env check passed: .env.backend-production\n",
  "backend-production-config.txt": "Backend production config check passed.\n",
  "launch-env-alignment.txt": "Launch env alignment check passed.\n",
  "db-migrations.txt": "Database migration check passed: 7 file(s)\n",
  "launch-readiness.txt": "Launch readiness check passed.\n",
  "real-account-smoke.txt": `==> product-catalog-page
==> product-detail
==> cart-list
==> order-page
==> order-detail
==> member-profile
==> member-address-list
==> wishlist-page
==> membership-profile
==> gift-registry-my
==> membership-admin-page
==> gift-registry-admin-page
==> gift-registry-admin-detail
==> trade-application-admin-page

Real account readiness smoke passed.
{
  "productCatalog": "ready",
  "cart": "ready",
  "checkout": "ready",
  "orders": "ready",
  "billing": "ready",
  "accountProfile": "ready",
  "addressBook": "ready",
  "wishlist": "ready",
  "membership": "ready",
  "giftRegistry": "ready",
  "tradeProgram": "ready"
}
{
  "seededAccount": {
    "userId": "1",
    "cartId": "7001",
    "skuId": "5001",
    "addressId": "8101",
    "orderId": "9001",
    "giftRegistryPublicCode": "reg-100",
    "tradeId": "RH-TRADE-10086",
    "tradeEmail": "designer@oakvedhome.com",
    "membershipStatus": "active_annual",
    "membershipPlanCode": "annual_membership",
    "giftRegistryItemSpuId": "5001",
    "giftRegistryItemSkuId": "6001"
  }
}
`,
  "order-create-smoke.txt": "Order live smoke passed: orderId=1001, payOrderId=2001\n",
  "post-deploy-health.txt": "Post-deploy health check passed: 5 check(s)\n",
};

const tinyPng = Buffer.from([
  0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01,
]);

const createCompleteLaunchFixture = () => {
  const tempRoot = mkdtempSync(join(tmpdir(), "oakved-initial-launch-"));
  const envFile = join(tempRoot, ".env.production");
  const smokeEnvFile = join(tempRoot, ".env.launch-smoke");
  const backendEnvFile = join(tempRoot, ".env.backend-production");
  const evidenceDir = join(tempRoot, "evidence");
  writeFileSync(envFile, productionEnv, "utf8");
  writeFileSync(smokeEnvFile, smokeEnv, "utf8");
  writeFileSync(backendEnvFile, backendEnv, "utf8");

  const bundle = createLaunchEvidenceBundle({
    dir: evidenceDir,
    commitSha: "abc123",
    imageTag: "oakved-storefront:abc123",
    imageDigest: "sha256:123",
    baseUrl: "https://shop.oakvedhome.com",
    envFile,
    smokeEnvFile,
    backendEnvFile,
    createdAt: "2026-07-01T00:00:00.000Z",
  });

  for (const file of bundle.manifest.requiredEvidenceFiles) {
    writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
  }

  writeFileSync(join(evidenceDir, "browser-home.png"), tinyPng);

  return { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir };
};

describe("initial launch readiness audit", () => {
  it("exposes a repeatable initial launch readiness audit command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["audit:initial-launch-readiness"]).toBe("node scripts/audit-initial-launch-readiness.mjs");
  });

  it("parses env and evidence directory flags", () => {
    expect(
      parseInitialLaunchReadinessArgs([
        "--env-file",
        ".env.production",
        "--smoke-env-file",
        ".env.launch-smoke",
        "--backend-env-file",
        ".env.backend-production",
        "--base-url",
        "https://shop.oakvedhome.com",
        "--evidence-dir",
        "launch-evidence/2026-07-01",
      ]),
    ).toEqual({
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      baseUrl: "https://shop.oakvedhome.com",
      evidenceDir: "launch-evidence/2026-07-01",
    });
  });

  it("passes only when env files align, backend production config, DB migration files, and evidence audit are complete", () => {
    const { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir } = createCompleteLaunchFixture();

    try {
      const result = auditInitialLaunchReadiness({
        envFile,
        smokeEnvFile,
        backendEnvFile,
        baseUrl: "https://shop.oakvedhome.com",
        evidenceDir,
      });

      expect(result).toMatchObject({ ok: true, blockers: [] });
      expect(result.checks.find((check) => check.name === "launch-evidence")?.details.join("\n")).toContain(
        "Launch evidence is complete",
      );
      expect(result.checks.map((check) => check.name)).toEqual([
        "production-env",
        "launch-smoke-env",
        "backend-production-env",
        "launch-env-alignment",
        "backend-production-config",
        "db-migrations",
        "launch-evidence",
        "launch-manifest-alignment",
        "real-account-seed-alignment",
      ]);
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence seeded identifiers do not match the smoke env", () => {
    const { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir } = createCompleteLaunchFixture();

    try {
      const realAccountSmokePath = join(evidenceDir, "real-account-smoke.txt");
      writeFileSync(
        realAccountSmokePath,
        readFileSync(realAccountSmokePath, "utf8")
          .replace('"orderId": "9001"', '"orderId": "9002"')
          .replace('"membershipStatus": "active_annual"', '"membershipStatus": "inactive"'),
        "utf8",
      );

      const result = auditInitialLaunchReadiness({
        envFile,
        smokeEnvFile,
        backendEnvFile,
        baseUrl: "https://shop.oakvedhome.com",
        evidenceDir,
      });

      expect(result.ok).toBe(false);
      expect(result.blockers.join("\n")).toContain(
        "real-account-smoke.txt seeded orderId must match YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID",
      );
      expect(result.blockers.join("\n")).toContain(
        "real-account-smoke.txt seeded membershipStatus must match YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS",
      );
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when final launch smoke env does not explicitly enable real-account order detail checking", () => {
    const { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir } = createCompleteLaunchFixture();

    try {
      writeFileSync(
        smokeEnvFile,
        readFileSync(smokeEnvFile, "utf8").replace("YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER=true", "YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER=false"),
        "utf8",
      );

      const result = auditInitialLaunchReadiness({
        envFile,
        smokeEnvFile,
        backendEnvFile,
        baseUrl: "https://shop.oakvedhome.com",
        evidenceDir,
      });

      expect(result.ok).toBe(false);
      expect(result.blockers.join("\n")).toContain(
        "YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER must be true for final initial launch evidence",
      );
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when the launch manifest does not match the audited env files and base URL", () => {
    const { tempRoot, envFile, smokeEnvFile, backendEnvFile, evidenceDir } = createCompleteLaunchFixture();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.smokeEnvFile = join(tempRoot, ".env.launch-smoke.other");
      manifest.baseUrl = "https://staging.oakvedhome.com";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditInitialLaunchReadiness({
        envFile,
        smokeEnvFile,
        backendEnvFile,
        baseUrl: "https://shop.oakvedhome.com",
        evidenceDir,
      });

      expect(result.ok).toBe(false);
      expect(result.blockers.join("\n")).toContain("launch-manifest.json smokeEnvFile must match --smoke-env-file");
      expect(result.blockers.join("\n")).toContain("launch-manifest.json baseUrl must match --base-url");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("reports concrete blockers when required external launch evidence is missing", () => {
    const result = auditInitialLaunchReadiness({
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      backendEnvFile: ".env.backend-production",
      baseUrl: "https://shop.oakvedhome.com",
      evidenceDir: "",
    });

    expect(result.ok).toBe(false);
    expect(result.blockers.join("\n")).toContain("Production env file not found");
    expect(result.blockers.join("\n")).toContain("Launch smoke env file not found");
    expect(result.blockers.join("\n")).toContain("Backend production env file not found");
    expect(result.blockers.join("\n")).toContain("env file not found");
    expect(result.blockers.join("\n")).toContain("Launch evidence directory is required");
  });
});
