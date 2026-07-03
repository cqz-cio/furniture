import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { createLaunchEvidenceBundle } from "../scripts/create-launch-evidence.mjs";
import {
  auditLaunchEvidence,
  parseLaunchEvidenceAuditArgs,
} from "../scripts/audit-launch-evidence.mjs";

const readProjectFile = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

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
    "skuId": "6001",
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

const createCompleteEvidence = () => {
  const tempRoot = mkdtempSync(join(tmpdir(), "oakved-launch-audit-"));
  const evidenceDir = join(tempRoot, "launch");
  const bundle = createLaunchEvidenceBundle({
    dir: evidenceDir,
    commitSha: "abc123",
    imageTag: "oakved-storefront:abc123",
    imageDigest: "sha256:123",
    baseUrl: "https://shop.oakvedhome.com",
    envFile: ".env.production",
    smokeEnvFile: ".env.launch-smoke",
    createdAt: "2026-07-01T00:00:00.000Z",
  });

  for (const file of bundle.manifest.requiredEvidenceFiles) {
    writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
  }

  writeFileSync(join(evidenceDir, "browser-home.png"), tinyPng);
  return { tempRoot, evidenceDir };
};

describe("launch evidence audit", () => {
  it("exposes a repeatable launch evidence audit command", () => {
    const packageJson = JSON.parse(readProjectFile("package.json"));

    expect(packageJson.scripts["audit:launch-evidence"]).toBe("node scripts/audit-launch-evidence.mjs");
  });

  it("parses evidence directory flags", () => {
    expect(parseLaunchEvidenceAuditArgs(["--dir", "launch-evidence/2026-07-01"])).toEqual({
      dir: "launch-evidence/2026-07-01",
    });
    expect(parseLaunchEvidenceAuditArgs(["--dir=launch-evidence/latest"])).toEqual({
      dir: "launch-evidence/latest",
    });
  });

  it("passes when manifest metadata and required evidence files are complete", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result).toMatchObject({ ok: true, errors: [] });
      expect(result.checkedFiles).toEqual(
        expect.arrayContaining(["production-env.txt", "launch-readiness.txt", "real-account-smoke.txt", "post-deploy-health.txt"]),
      );
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when evidence files still contain placeholders", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "production-env.txt"), "Paste command output here for production-env.txt.\n", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("production-env.txt still contains placeholder text");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when the manifest omits real-account smoke from required evidence files", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.requiredEvidenceFiles = manifest.requiredEvidenceFiles.filter((file) => file !== "real-account-smoke.txt");
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("requiredEvidenceFiles must include real-account-smoke.txt");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when a manifest command output file is omitted from required evidence files", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.requiredEvidenceFiles = manifest.requiredEvidenceFiles.filter((file) => file !== "launch-readiness.txt");
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain(
        "requiredEvidenceFiles must include outputFile launch-readiness.txt from launch-readiness command",
      );
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when the manifest omits the standalone real-account smoke command", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.commands = manifest.commands.filter((command) => command.name !== "real-account-smoke");
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke command is required in launch-manifest.json");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when the standalone real-account smoke command omits the order check", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      const command = manifest.commands.find((item) => item.name === "real-account-smoke");
      command.command = "npm.cmd run test:smoke:real-account -- --env-file .env.launch-smoke";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke command must include --check-order");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when any launch manifest command allows placeholders", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      const command = manifest.commands.find((item) => item.name === "launch-smoke-env");
      command.command = `${command.command} --allow-placeholders`;
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("launch-smoke-env command must not include --allow-placeholders");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when a command output file does not contain the expected success marker", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "launch-readiness.txt"), "npm finished with exit code 0\n", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("launch-readiness.txt must contain success marker: Launch readiness check passed.");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence does not include a full ready module snapshot", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "real-account-smoke.txt"), "Real account readiness smoke passed.\n", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include ready module snapshot for productCatalog");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include ready module snapshot for tradeProgram");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence omits seeded account identifiers", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `Real account readiness smoke passed.
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
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: userId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: cartId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: skuId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: addressId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: orderId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: giftRegistryPublicCode");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: tradeId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: tradeEmail");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: membershipStatus");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: membershipPlanCode");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: giftRegistryItemSpuId");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seeded real-account identifier: giftRegistryItemSkuId");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when check-order real-account smoke evidence omits the order-detail step", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        successOutputByFile["real-account-smoke.txt"].replace("==> order-detail\n", ""),
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include the order-detail step when --check-order is used");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence omits required app or admin step logs", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        successOutputByFile["real-account-smoke.txt"]
          .replace("==> cart-list\n", "")
          .replace("==> membership-admin-page\n", "")
          .replace("==> gift-registry-admin-detail\n", "")
          .replace("==> trade-application-admin-page\n", ""),
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include the real-account smoke step: cart-list");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include the real-account smoke step: membership-admin-page");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include the real-account smoke step: gift-registry-admin-detail");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include the real-account smoke step: trade-application-admin-page");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when seeded identifiers are present outside the seededAccount block", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `Real account readiness smoke passed.
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
  "userId": "1",
  "giftRegistryPublicCode": "reg-100",
  "tradeId": "RH-TRADE-10086",
  "tradeEmail": "designer@oakvedhome.com"
}
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must include seededAccount block");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence uses placeholder seeded account identifiers", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `Real account readiness smoke passed.
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
    "skuId": "6001",
    "addressId": "8101",
    "orderId": "9001",
    "giftRegistryPublicCode": "replace-me",
    "tradeId": "replace-me",
    "tradeEmail": "designer@oakvedhome.com",
    "membershipStatus": "active_annual",
    "membershipPlanCode": "annual_membership",
    "giftRegistryItemSpuId": "5001",
    "giftRegistryItemSkuId": "6001"
  }
}
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded giftRegistryPublicCode must not be a placeholder");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded tradeId must not be a placeholder");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence uses invalid seeded account shapes", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `Real account readiness smoke passed.
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
    "userId": "abc",
    "cartId": "cart-7001",
    "skuId": "0",
    "addressId": "address-8101",
    "orderId": "order-9001",
    "giftRegistryPublicCode": "reg-100",
    "tradeId": "RH-TRADE-10086",
    "tradeEmail": "designer",
    "membershipStatus": "active_annual",
    "membershipPlanCode": "annual_membership",
    "giftRegistryItemSpuId": "spu-5001",
    "giftRegistryItemSkuId": "sku-6001"
  }
}
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded userId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded cartId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded skuId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded addressId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded orderId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded giftRegistryItemSpuId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded giftRegistryItemSkuId must be a positive integer");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded tradeEmail must be a valid email address");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence uses an example trade email", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `Real account readiness smoke passed.
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
    "skuId": "6001",
    "addressId": "8101",
    "orderId": "9001",
    "giftRegistryPublicCode": "reg-100",
    "tradeId": "RH-TRADE-10086",
    "tradeEmail": "designer@example.com",
    "membershipStatus": "active_annual",
    "membershipPlanCode": "annual_membership",
    "giftRegistryItemSpuId": "5001",
    "giftRegistryItemSkuId": "6001"
  }
}
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt seeded tradeEmail must not use example.com");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence mixes success with skipped or failed readiness output", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `${successOutputByFile["real-account-smoke.txt"]}
Optional readiness step skipped: membership-profile
Error: Real account readiness failed: module-membership-blocked
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must not include skipped readiness steps");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must not include failure output");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when real-account smoke evidence mixes ready output with partial or blocked module states", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(
        join(evidenceDir, "real-account-smoke.txt"),
        `${successOutputByFile["real-account-smoke.txt"]}
{
  "membership": "partial",
  "tradeProgram": "blocked"
}
`,
        "utf8",
      );
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must not include non-ready module snapshot for membership");
      expect(result.errors.join("\n")).toContain("real-account-smoke.txt must not include non-ready module snapshot for tradeProgram");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when the launch-readiness manifest command omits admin or backend gates", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      const launchReadiness = manifest.commands.find((item) => item.name === "launch-readiness");
      launchReadiness.command =
        "npm.cmd run verify:launch-readiness -- --env-file .env.production --smoke-env-file .env.launch-smoke --include-db-migrations --include-real-account-smoke";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("launch-readiness command must include --include-admin-check");
      expect(result.errors.join("\n")).toContain("launch-readiness command must include --include-admin-build");
      expect(result.errors.join("\n")).toContain("launch-readiness command must include --include-backend-build");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when no browser screenshot evidence is present", () => {
    const tempRoot = mkdtempSync(join(tmpdir(), "oakved-launch-audit-no-shot-"));
    const evidenceDir = join(tempRoot, "launch");
    const bundle = createLaunchEvidenceBundle({
      dir: evidenceDir,
      commitSha: "abc123",
      imageTag: "oakved-storefront:abc123",
      imageDigest: "sha256:123",
      baseUrl: "https://shop.oakvedhome.com",
      envFile: ".env.production",
      smokeEnvFile: ".env.launch-smoke",
      createdAt: "2026-07-01T00:00:00.000Z",
    });

    for (const file of bundle.manifest.requiredEvidenceFiles) {
      writeFileSync(join(evidenceDir, file), successOutputByFile[file] || `Command passed for ${file}\n`, "utf8");
    }

    try {
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("At least one browser screenshot image is required");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when browser screenshot evidence still contains placeholder text", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "browser-home.png"), "screenshot-placeholder", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("browser-home.png still contains placeholder screenshot text");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when screenshot evidence is a text file renamed as an image", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      writeFileSync(join(evidenceDir, "browser-home.png"), "PNG screenshot evidence for deployed home page", "utf8");
      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("browser-home.png must be a valid PNG, JPEG, or WebP image.");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when launch metadata is missing", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.imageDigest = "";
      manifest.baseUrl = "";
      manifest.backendEnvFile = "";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("imageDigest is required");
      expect(result.errors.join("\n")).toContain("baseUrl is required");
      expect(result.errors.join("\n")).toContain("backendEnvFile is required");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });

  it("fails when launch manifest baseUrl uses a documentation domain", () => {
    const { tempRoot, evidenceDir } = createCompleteEvidence();

    try {
      const manifestPath = join(evidenceDir, "launch-manifest.json");
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      manifest.baseUrl = "https://shop.example.com";
      writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

      const result = auditLaunchEvidence({ dir: evidenceDir });

      expect(result.ok).toBe(false);
      expect(result.errors.join("\n")).toContain("baseUrl must not use a documentation/example domain");
    } finally {
      rmSync(tempRoot, { recursive: true, force: true });
    }
  });
});
