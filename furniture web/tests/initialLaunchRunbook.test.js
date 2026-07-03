import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const runbookPath = new URL("../docs/yudao-integration/initial-launch-runbook.md", import.meta.url);
const readRunbook = () => readFileSync(runbookPath, "utf8");

describe("initial launch runbook", () => {
  it("exists as the operator-facing launch checklist", () => {
    expect(existsSync(runbookPath)).toBe(true);
  });

  it("documents the launch-critical command sequence", () => {
    const source = readRunbook();
    const requiredSnippets = [
      "npm.cmd run verify:production-env -- --env-file .env.production",
      ".env.backend-production.example",
      "npm.cmd run verify:backend-production-env -- --env-file .env.backend-production",
      "npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com",
      "npm.cmd run create:launch-evidence -- --launch-env-file .env.production --launch-smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com",
      "npm.cmd run audit:launch-evidence -- --dir launch-evidence/<timestamp>",
      "npm.cmd run audit:initial-launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com --evidence-dir launch-evidence/<timestamp>",
      ".env.launch-smoke.example",
      "npm.cmd run verify:launch-smoke-env -- --env-file .env.launch-smoke",
      "YUDAO_REAL_ACCOUNT_SMOKE_TOKEN=<real-app-user-token>",
      "YUDAO_REAL_ACCOUNT_SMOKE_SPU_ID=<real-spu-id-with-sku>",
      "YUDAO_REAL_ACCOUNT_SMOKE_ADDRESS_ID=<real-saved-address-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SPU_ID=<real-wishlist-spu-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_WISHLIST_SKU_ID=<real-wishlist-sku-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_STATUS=<real-membership-status-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_MEMBERSHIP_PLAN_CODE=<real-membership-plan-code-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SPU_ID=<real-registry-item-spu-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_ITEM_SKU_ID=<real-registry-item-sku-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_ORDER_ID=<real-order-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_USER_ID=<real-user-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_GIFT_REGISTRY_PUBLIC_CODE=<real-gift-registry-public-code-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_ID=<real-trade-id-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_SMOKE_TRADE_EMAIL=<real-trade-application-email-for-token-owner>",
      "YUDAO_REAL_ACCOUNT_ADMIN_BASE_URL=https://api.oakvedhome.com/admin-api",
      "YUDAO_REAL_ACCOUNT_ADMIN_TENANT_ID=121",
      "YUDAO_REAL_ACCOUNT_ADMIN_TOKEN=<real-admin-user-token>",
      "product-favorite-sku-wishlist.sql",
      "trade-order-address-verification.sql",
      "member-address-address-verification.sql",
      "member-trade-application.sql",
      "member-membership.sql",
      "member-gift-registry.sql",
      "trade-gift-registry-context.sql",
      "npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations",
      "--include-backend-prod-config",
      "--include-backend-prod-env",
      "--backend-env-file .env.backend-production",
      "--include-launch-env-alignment",
      "--base-url https://shop.oakvedhome.com",
      "--include-admin-check",
      "--include-admin-build",
      "--include-backend-build",
      "SPRING_PROFILES_ACTIVE=prod",
      "npm.cmd run verify:backend-production-config",
      "--include-live-business-smoke",
      "--include-order-live-smoke",
      "--include-real-account-smoke",
      "--real-account-check-order",
      "--smoke-env-file .env.launch-smoke",
      "npm.cmd run test:smoke:order-live -- --env-file .env.launch-smoke --create-order",
      "npm.cmd run test:deploy:health -- --base-url https://shop.oakvedhome.com",
      "docker build -t oakved-storefront:launch",
      "launch-manifest.json",
      "output from `npm.cmd run verify:backend-production-env -- --env-file .env.backend-production`",
      "output from `npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.oakvedhome.com`",
      "output from the standalone real account smoke command: `npm.cmd run test:smoke:real-account -- --env-file .env.launch-smoke --check-order`",
      "`launch-manifest.json` `requiredEvidenceFiles` must include `real-account-smoke.txt`, and its `real-account-smoke` command must include `--check-order`",
      "`launch-manifest.json` every command `outputFile` must be listed in `requiredEvidenceFiles`",
      "`launch-manifest.json` `envFile`, `smokeEnvFile`, `backendEnvFile`, and `baseUrl` must match the final `audit:initial-launch-readiness` arguments",
      "`real-account-smoke.txt` must include the `==> ...` step logs for product, cart, order page, profile, address, wishlist, membership, Gift Registry, Admin membership, Admin Gift Registry list/detail, and Admin Trade checks",
      "`real-account-smoke.txt` must include the JSON module snapshot with every module set to `ready`",
      "`real-account-smoke.txt` must include the `seededAccount` JSON block with positive-integer `userId`, `cartId`, `skuId`, `addressId`, `orderId`, `giftRegistryItemSpuId`, `giftRegistryItemSkuId`, plus `giftRegistryPublicCode`, `tradeId`, `membershipStatus`, `membershipPlanCode`, and a valid non-`example.com` `tradeEmail`",
      "`.env.launch-smoke` `YUDAO_REAL_ACCOUNT_SMOKE_CHECK_ORDER` must be `true` for the final `audit:initial-launch-readiness` gate",
    ];

    for (const snippet of requiredSnippets) {
      expect(source).toContain(snippet);
    }
  });

  it("keeps the operator flow in the safe launch order", () => {
    const source = readRunbook();
    const orderedHeadings = [
      "## 1. Freeze The Launch Window",
      "## 2. Prepare Production Configuration",
      "## 3. Apply Database Migrations",
      "## 4. Run Automated Readiness Gates",
      "## 5. Run Live Business Smoke",
      "## 6. Build And Publish The Storefront Image",
      "## 7. Post-Deploy Verification",
      "## 8. Rollback Criteria",
    ];

    let previousIndex = -1;
    for (const heading of orderedHeadings) {
      const nextIndex = source.indexOf(heading);
      expect(nextIndex).toBeGreaterThan(previousIndex);
      previousIndex = nextIndex;
    }
  });

  it("requires evidence capture instead of relying on memory", () => {
    const source = readRunbook();

    expect(source).toContain("Save the command output");
    expect(source).toContain("Record the commit SHA");
    expect(source).toContain("Record the image tag");
    expect(source).toContain("Do not proceed");
  });
});
