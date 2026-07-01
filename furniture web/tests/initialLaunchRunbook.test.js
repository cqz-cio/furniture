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
      "npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com",
      "npm.cmd run create:launch-evidence -- --launch-env-file .env.production --launch-smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com",
      "npm.cmd run audit:launch-evidence -- --dir launch-evidence/<timestamp>",
      "npm.cmd run audit:initial-launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com --evidence-dir launch-evidence/<timestamp>",
      ".env.launch-smoke.example",
      "npm.cmd run verify:launch-smoke-env -- --env-file .env.launch-smoke",
      "product-favorite-sku-wishlist.sql",
      "trade-order-address-verification.sql",
      "member-address-address-verification.sql",
      "npm.cmd run verify:launch-readiness -- --env-file .env.production --include-db-migrations",
      "--include-backend-prod-config",
      "--include-backend-prod-env",
      "--backend-env-file .env.backend-production",
      "--include-launch-env-alignment",
      "--base-url https://shop.example.com",
      "--include-admin-check",
      "--include-admin-build",
      "--include-backend-build",
      "SPRING_PROFILES_ACTIVE=prod",
      "npm.cmd run verify:backend-production-config",
      "--include-live-business-smoke",
      "--include-order-live-smoke",
      "--smoke-env-file .env.launch-smoke",
      "npm.cmd run test:smoke:order-live -- --env-file .env.launch-smoke --create-order",
      "npm.cmd run test:deploy:health -- --base-url https://shop.example.com",
      "docker build -t oakved-storefront:launch",
      "launch-manifest.json",
      "output from `npm.cmd run verify:backend-production-env -- --env-file .env.backend-production`",
      "output from `npm.cmd run verify:launch-env-alignment -- --env-file .env.production --smoke-env-file .env.launch-smoke --backend-env-file .env.backend-production --base-url https://shop.example.com`",
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
